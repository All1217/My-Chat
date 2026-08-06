/**
 * 流式聊天工具
 * 统一走 normalChat NDJSON（含 Routing）；可选附带 kbId 供分类与 kb 路由。
 */

import type { ChatStreamEvent } from '@/types/AiModule/streamEvents'

export interface ChatStreamOptions {
  prompt: string
  chatId: string
  kbId?: string
  files?: File[]
  /** 兼容旧 RAG 纯文本（本路径已不再使用） */
  onMessage?: (chunk: string) => void
  /** NDJSON 事件 */
  onEvent?: (event: ChatStreamEvent) => void
  onComplete: () => void
  onError: (error: Error) => void
}

/**
 * 发送流式聊天请求
 * @returns 取消请求的函数
 */
export function streamChat(options: ChatStreamOptions): () => void {
  const { prompt, chatId, kbId, files, onEvent, onComplete, onError } = options

  const formData = new FormData()
  formData.append('prompt', prompt)
  formData.append('chatId', chatId)

  if (files && files.length > 0) {
    files.forEach(file => {
      formData.append('files', file)
    })
  }

  if (kbId) {
    formData.append('kbId', kbId)
  }

  // 统一入口：后端 Routing 分发 file/kb/search/general
  const url = '/rag/ai/normalChat/chat?format=ndjson'

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
            if (lineBuffer.trim()) {
              flushNdjsonLine(lineBuffer, onEvent)
              lineBuffer = ''
            }
            onComplete()
            break
          }

          const chunk = decoder.decode(value, { stream: true })
          lineBuffer += chunk
          const lines = lineBuffer.split('\n')
          lineBuffer = lines.pop() ?? ''
          for (const line of lines) {
            flushNdjsonLine(line, onEvent)
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
