/**
 * 与后端 ChatStreamEvent NDJSON 协议对齐（进阶 3 · 第 2 周）。
 */

export type ChatStreamEventType =
  | 'thinking_delta'
  | 'text_delta'
  | 'tool_call'
  | 'tool_result'
  | 'error'
  | 'done'

export interface ChatStreamEvent {
  v: number
  type: ChatStreamEventType
  turnId: string
  seq: number
  text?: string
  id?: string
  name?: string
  args?: unknown
  ok?: boolean
  preview?: string
  truncated?: boolean
  message?: string
}

/** 助手消息内可展示的片段（时间线 + 正文拆分） */
export type MessagePart =
  | { type: 'thinking'; text: string }
  | { type: 'text'; text: string }
  | {
    type: 'tool'
    id: string
    name: string
    args?: unknown
    status: 'running' | 'done' | 'error' | 'cancelled'
    resultPreview?: string
    ok?: boolean
  }

export type ToolMessagePart = Extract<MessagePart, { type: 'tool' }>
