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

/** 与后端 KnowledgeRetrieveHit 对齐的聊天引用来源 */
export interface KbCitation {
  filename?: string
  documentId?: string
  score?: number | null
  kind?: 'chunk' | 'catalog'
  text?: string
}

/** 从 NDJSON / parts.args.citations 解析来源列表 */
export function parseKbCitations(raw: unknown): KbCitation[] | undefined {
  if (!Array.isArray(raw)) return undefined
  const list: KbCitation[] = []
  for (const item of raw) {
    if (!item || typeof item !== 'object') continue
    const o = item as Record<string, unknown>
    list.push({
      filename: typeof o.filename === 'string' ? o.filename : undefined,
      documentId: typeof o.documentId === 'string' ? o.documentId : undefined,
      score: typeof o.score === 'number' ? o.score : undefined,
      kind: o.kind === 'catalog' ? 'catalog' : o.kind === 'chunk' ? 'chunk' : undefined,
      text: typeof o.text === 'string' ? o.text : undefined,
    })
  }
  return list.length ? list : undefined
}

/** 从各 retrieve_kb 步骤收集来源，按 documentId 或 filename 去重 */
export function uniqueKbCitations(parts?: MessagePart[] | null): KbCitation[] {
  const gathered: KbCitation[] = []
  for (const p of parts ?? []) {
    if (p.type !== 'step' || p.action !== 'retrieve_kb' || !p.citations?.length) continue
    gathered.push(...p.citations)
  }
  return dedupeKbCitations(gathered)
}

/** 按 documentId（优先）或 filename 去重，保留首次出现顺序 */
export function dedupeKbCitations(list?: KbCitation[] | null): KbCitation[] {
  const out: KbCitation[] = []
  const seen = new Set<string>()
  for (const c of list ?? []) {
    const key = (c.documentId?.trim()) || (c.filename?.trim()) || ''
    if (!key || seen.has(key)) continue
    seen.add(key)
    out.push(c)
  }
  return out
}

/** 解析编排器 kbScope；非法则忽略 */
export function parseKbScope(raw: unknown): 'catalog' | 'vector' | undefined {
  return raw === 'catalog' || raw === 'vector' ? raw : undefined
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
    citations?: KbCitation[]
    kbScope?: 'catalog' | 'vector'
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
