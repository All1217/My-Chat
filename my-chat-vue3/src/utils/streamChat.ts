/**
 * 流式聊天工具
 * 统一走 normalChat NDJSON；默认多步编排 + 写盘质量环（Agent 主路）。
 */

import type { ChatStreamEvent } from '@/types/AiModule/streamEvents'

export interface ChatStreamOptions {
  prompt: string
  chatId: string
  kbId?: string
  files?: File[]
  /** route | orchestrate；缺省由调用方/后端按 orchestrate 处理 */
  agentMode?: 'route' | 'orchestrate'
  /** 写盘后是否跑任务内质量环；主路默认 true */
  qualityLoop?: boolean
  /** Orchestrator 最大步数（可选） */
  maxSteps?: number
  /** 质量环评价标准（可选） */
  criteria?: string
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
  const {
    prompt,
    chatId,
    kbId,
    files,
    agentMode,
    qualityLoop,
    maxSteps,
    criteria,
    onEvent,
    onComplete,
    onError,
  } = options

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
  formData.append('agentMode', agentMode ?? 'orchestrate')
  // 显式传 false 时关闭；缺省 true
  formData.append('qualityLoop', qualityLoop === false ? 'false' : 'true')
  if (maxSteps != null && maxSteps > 0) {
    formData.append('maxSteps', String(maxSteps))
  }
  if (criteria) {
    formData.append('criteria', criteria)
  }

  // 统一入口：后端 Routing 或 agentMode=orchestrate
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
      } finally {
        reader.releaseLock()
      }
    })
    .catch(err => {
      if (err?.name === 'AbortError') {
        return
      }
      onError(err instanceof Error ? err : new Error(String(err)))
    })

  return () => controller.abort()
}

function flushNdjsonLine(line: string, onEvent?: (event: ChatStreamEvent) => void) {
  const trimmed = line.trim()
  if (!trimmed || !onEvent) return
  try {
    onEvent(JSON.parse(trimmed) as ChatStreamEvent)
  } catch {
    // 忽略非 JSON 行
  }
}

export function generateChatId(): string {
  return `chat_${Date.now()}_${Math.random().toString(36).slice(2, 11)}`
}
