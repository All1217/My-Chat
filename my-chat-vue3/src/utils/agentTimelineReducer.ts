import type {
  ActivityStatus,
  ChatStreamEvent,
  MessagePart,
  StepMessagePart,
  ToolMessagePart,
} from '@/types/AiModule/streamEvents'
import { parseKbCitations, parseKbScope } from '@/types/AiModule/streamEvents'
import { unwrapToolPreview } from '@/utils/unwrapToolPreview'

/** 将后端步骤状态转换为 UI 状态。 */
export function activityStatus(
  status: ChatStreamEvent['status'],
  fallback: ActivityStatus,
): ActivityStatus {
  switch (status) {
    case 'RUNNING':
    case 'PENDING':
      return 'running'
    case 'SUCCEEDED':
    case 'SKIPPED':
      return 'done'
    case 'FAILED':
      return 'error'
    case 'CANCELLED':
      return 'cancelled'
    default:
      return fallback
  }
}

/** 实时事件统一归约器：按稳定 ID 原位新增或更新时间线。 */
export function applyTimelineEvent(parts: MessagePart[], event: ChatStreamEvent): void {
  if (event.type === 'route') {
    upsert(parts, {
      type: 'route',
      id: event.stepId ?? event.id ?? `route-${event.seq}`,
      route: event.name ?? 'general',
    })
    return
  }
  if (event.type === 'step') {
    const args = objectArgs(event.args)
    const id = event.stepId ?? event.id ?? `step-${event.seq}`
    const previous = parts.find(part => part.type === 'step' && part.id === id) as
      StepMessagePart | undefined
    const stepIndex = (numberValue(args.stepIndex)
      ?? previous?.stepIndex
      ?? Number(id.replace(/^step-/, ''))) || event.seq
    upsert(parts, {
      type: 'step',
      id,
      stepIndex,
      action: event.name ?? previous?.action ?? 'unknown',
      instruction: stringValue(args.instruction) ?? previous?.instruction,
      observation: event.preview ?? previous?.observation,
      citations: parseKbCitations(args.citations) ?? previous?.citations,
      kbScope: parseKbScope(args.kbScope) ?? previous?.kbScope,
      status: activityStatus(event.status, previous?.status ?? 'done'),
      displayName: event.displayName ?? previous?.displayName,
      elapsedMs: event.elapsedMs ?? previous?.elapsedMs,
    })
    return
  }
  if (event.type === 'tool_call' || event.type === 'tool_result') {
    const id = event.stepId ?? event.id
    if (!id) return
    const previous = parts.find(part => part.type === 'tool' && part.id === id) as
      ToolMessagePart | undefined
    const fallback = event.type === 'tool_call'
      ? 'running'
      : event.ok === false ? 'error' : 'done'
    upsert(parts, {
      type: 'tool',
      id,
      name: event.name ?? previous?.name ?? 'unknown',
      args: event.args ?? previous?.args,
      status: activityStatus(event.status, fallback),
      resultPreview: event.type === 'tool_result'
        ? unwrapToolPreview(event.preview)
        : previous?.resultPreview,
      ok: event.ok ?? previous?.ok,
      parentStepId: event.parentStepId ?? previous?.parentStepId,
      displayName: event.displayName ?? previous?.displayName,
      elapsedMs: event.elapsedMs ?? previous?.elapsedMs,
    })
  }
}

/** 历史 parts 使用同一数据形态归一化，保证刷新前后结构一致。 */
export function normalizeTimelineParts(parts?: MessagePart[] | null): MessagePart[] | undefined {
  if (!parts?.length) return parts ?? undefined
  const normalized: MessagePart[] = []
  for (const part of parts) {
    if (part.type === 'route') {
      const raw = part as unknown as Record<string, unknown>
      upsert(normalized, {
        type: 'route',
        id: stringValue(raw.id) ?? `route-${stringValue(raw.name) ?? 'general'}`,
        route: stringValue(raw.route) ?? stringValue(raw.name) ?? 'general',
      })
    } else if (part.type === 'step') {
      const raw = part as unknown as Record<string, unknown>
      const args = objectArgs(raw.args)
      const id = stringValue(raw.id) ?? `step-${numberValue(args.stepIndex) ?? 0}`
      upsert(normalized, {
        type: 'step',
        id,
        stepIndex: numberValue(args.stepIndex) ?? numberValue(raw.stepIndex) ?? 0,
        action: stringValue(raw.action) ?? stringValue(raw.name) ?? 'unknown',
        instruction: stringValue(args.instruction) ?? stringValue(raw.instruction),
        observation: stringValue(raw.observation) ?? stringValue(raw.resultPreview),
        citations: parseKbCitations(args.citations ?? raw.citations),
        kbScope: parseKbScope(args.kbScope ?? raw.kbScope),
        status: isActivityStatus(raw.status) ? raw.status : 'done',
        displayName: stringValue(raw.displayName),
        elapsedMs: numberValue(raw.elapsedMs),
      })
    } else {
      upsert(normalized, part)
    }
  }
  return normalized
}

/** 按类型和 ID 更新现有项，否则追加。 */
function upsert(parts: MessagePart[], incoming: MessagePart): void {
  const index = parts.findIndex(part => part.type === incoming.type
    && 'id' in part && 'id' in incoming && part.id === incoming.id)
  if (index >= 0) {
    parts[index] = { ...parts[index], ...incoming } as MessagePart
  } else {
    parts.push(incoming)
  }
}

/** 安全读取对象参数。 */
function objectArgs(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' ? value as Record<string, unknown> : {}
}

/** 安全读取字符串。 */
function stringValue(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined
}

/** 安全读取数字。 */
function numberValue(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

/** 判断历史状态是否为 UI 状态。 */
function isActivityStatus(value: unknown): value is ActivityStatus {
  return value === 'running' || value === 'done' || value === 'error' || value === 'cancelled'
}
