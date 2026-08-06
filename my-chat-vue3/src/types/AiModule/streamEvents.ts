/**
 * 与后端 ChatStreamEvent NDJSON 协议对齐（含 Routing route 事件）。
 */

export type ChatStreamEventType =
  | 'thinking_delta'
  | 'text_delta'
  | 'tool_call'
  | 'tool_result'
  | 'route'
  | 'error'
  | 'done'

export interface ChatStreamEvent {
  v: number
  type: ChatStreamEventType
  turnId: string
  seq: number
  /** text_delta / thinking_delta 正文；route 事件为分类理由 */
  text?: string
  id?: string
  /** tool 名；route 事件为路由标签 file|kb|search|general */
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
    type: 'route'
    id: string
    /** file | kb | search | general */
    route: string
    reasoning?: string
  }
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
export type RouteMessagePart = Extract<MessagePart, { type: 'route' }>
