/**
 * 与后端 ChatStreamEvent NDJSON 协议对齐（含 Routing route / Orchestrator step）。
 */

export type ChatStreamEventType =
  | 'thinking_delta'
  | 'text_delta'
  | 'tool_call'
  | 'tool_result'
  | 'route'
  | 'step'
  | 'error'
  | 'done'

export interface ChatStreamEvent {
  v: number
  type: ChatStreamEventType
  turnId: string
  seq: number
  /** text_delta / thinking_delta 正文；route/step 为理由 */
  text?: string
  id?: string
  /** tool 名；route 标签；step 的 action */
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
    /** file | kb | search | general | orchestrate */
    route: string
    reasoning?: string
  }
  | {
    type: 'step'
    id: string
    stepIndex: number
    /** retrieve_kb | file | search | general | finish | evaluate_optimize */
    action: string
    reasoning?: string
    instruction?: string
    observation?: string
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
export type StepMessagePart = Extract<MessagePart, { type: 'step' }>
