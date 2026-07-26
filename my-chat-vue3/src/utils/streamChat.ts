/**
 * 流式聊天工具
 * 普通聊天：NDJSON 事件流（?format=ndjson）
 * 知识库 RAG：仍为纯文本 chunk（后端尚无 ndjson）
 */

import type { ChatStreamEvent } from '@/types/AiModule/streamEvents'

export interface ChatStreamOptions {
  prompt: string
  chatId: string
  kbId?: string
  files?: File[]
  /** RAG / 纯文本路径 */
  onMessage?: (chunk: string) => void
  /** 普通聊天 NDJSON 路径 */
  onEvent?: (event: ChatStreamEvent) => void
  onComplete: () => void
  onError: (error: Error) => void
}

/**
 * 发送流式聊天请求
 * @returns 取消请求的函数
 */
export function streamChat(options: ChatStreamOptions): () => void {
  const { prompt, chatId, kbId, files, onMessage, onEvent, onComplete, onError } = options

  const formData = new FormData()
  formData.append('prompt', prompt)
  formData.append('chatId', chatId)

  if (files && files.length > 0) {
    files.forEach(file => {
      formData.append('files', file)
    })
  }

  const useNdjson = !kbId
  if (kbId) {
    formData.append('kbId', kbId)
  }
  // 普通聊天固定请求 NDJSON；RAG 不加 format，保持旧协议
  const url = kbId
    ? '/rag/ai/ragChat/chat'
    : '/rag/ai/normalChat/chat?format=ndjson'

  const controller = new AbortController()

  fetch(url, {
    method: 'POST',
    body: formData,
    signal: controller.signal,
  })
    .then(async response => {
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      if (!response.body) {
        throw new Error('Response body is null')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let lineBuffer = ''

      try {
        while (true) {
          const { done, value } = await reader.read()
          if (done) {
            if (useNdjson && lineBuffer.trim()) {
              flushNdjsonLine(lineBuffer, onEvent)
              lineBuffer = ''
            }
            onComplete()
            break
          }

          const chunk = decoder.decode(value, { stream: true })
          if (useNdjson) {
            lineBuffer += chunk
            const lines = lineBuffer.split('\n')
            // 最后一段可能不完整，留在 buffer
            lineBuffer = lines.pop() ?? ''
            for (const line of lines) {
              flushNdjsonLine(line, onEvent)
            }
          } else {
            onMessage?.(chunk)
          }
        }
      } catch (error) {
        onError(error instanceof Error ? error : new Error('Stream reading error'))
      }
    })
    .catch(error => {
      if (error.name === 'AbortError') {
        return
      }
      onError(error instanceof Error ? error : new Error('Request error'))
    })

  return () => {
    controller.abort()
  }
}

function flushNdjsonLine(
  line: string,
  onEvent?: (event: ChatStreamEvent) => void,
) {
  const trimmed = line.trim()
  if (!trimmed) return
  try {
    const event = JSON.parse(trimmed) as ChatStreamEvent
    onEvent?.(event)
  } catch (e) {
    console.warn('[streamChat] 跳过非法 NDJSON 行:', trimmed.slice(0, 120), e)
  }
}

export function generateChatId(): string {
  return `chat_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
}
