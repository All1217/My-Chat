/** 金额展示。 */
export function formatMoney(v: number | null | undefined) {
  if (v === null || v === undefined || Number.isNaN(Number(v))) return '—'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 })
}

/** 比例展示。 */
export function formatPct(v: number | null | undefined) {
  if (v === null || v === undefined) return '—'
  return `${(Number(v) * 100).toFixed(1)}%`
}

/** 时间展示为 yyyy-MM-dd HH:mm:ss（兼容 ISO / 已格式化字符串）。 */
export function formatDateTime(v: unknown): string {
  if (v === null || v === undefined || v === '') return '—'
  const raw = String(v).trim()
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(raw)) return raw
  const d = new Date(raw.includes('T') ? raw : raw.replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return raw
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const ACTION_LABELS: Record<string, string> = {
  BUY: '买入',
  SELL: '卖出',
  HOLD: '持有',
  FLAT: '空仓观望',
}

const EXEC_STATUS_LABELS: Record<string, string> = {
  PENDING: '待确认',
  EXECUTED: '已执行',
  SKIPPED: '已跳过',
  REJECTED: '已拒绝',
  DISMISSED: '已忽略',
}

const RUN_STATUS_LABELS: Record<string, string> = {
  PENDING: '排队中',
  RUNNING: '运行中',
  SUCCEEDED: '成功',
  FAILED: '失败',
}

const ORDER_STATUS_LABELS: Record<string, string> = {
  FILLED: '已成交',
  REJECTED: '已拒绝',
}

function labelOf(map: Record<string, string>, v: unknown): string {
  if (v === null || v === undefined || v === '') return '—'
  const key = String(v).trim().toUpperCase()
  return map[key] ?? String(v)
}

/** 决策动作：BUY/SELL/HOLD/FLAT。 */
export function formatAction(v: unknown): string {
  return labelOf(ACTION_LABELS, v)
}

/** 决策执行状态。 */
export function formatExecStatus(v: unknown): string {
  return labelOf(EXEC_STATUS_LABELS, v)
}

/** 搜集/交易等轮次状态。 */
export function formatRunStatus(v: unknown): string {
  return labelOf(RUN_STATUS_LABELS, v)
}

/** 订单状态。 */
export function formatOrderStatus(v: unknown): string {
  return labelOf(ORDER_STATUS_LABELS, v)
}

/** Element Plus Tag 的 type。 */
export type ElTagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

export type EnumTagKind = 'action' | 'execStatus' | 'runStatus' | 'orderStatus'

const ACTION_TAG_TYPES: Record<string, ElTagType> = {
  BUY: 'danger',
  SELL: 'success',
  HOLD: 'warning',
  FLAT: 'info',
}

const EXEC_STATUS_TAG_TYPES: Record<string, ElTagType> = {
  PENDING: 'warning',
  EXECUTED: 'success',
  SKIPPED: 'info',
  REJECTED: 'danger',
  DISMISSED: 'info',
}

const RUN_STATUS_TAG_TYPES: Record<string, ElTagType> = {
  PENDING: 'info',
  RUNNING: 'warning',
  SUCCEEDED: 'success',
  FAILED: 'danger',
}

const ORDER_STATUS_TAG_TYPES: Record<string, ElTagType> = {
  FILLED: 'success',
  REJECTED: 'danger',
}

function typeOf(map: Record<string, ElTagType>, v: unknown): ElTagType {
  if (v === null || v === undefined || v === '') return 'info'
  const key = String(v).trim().toUpperCase()
  return map[key] ?? 'info'
}

/** 枚举值对应的 Tag 颜色。 */
export function tagTypeFor(kind: EnumTagKind, v: unknown): ElTagType {
  switch (kind) {
    case 'action':
      return typeOf(ACTION_TAG_TYPES, v)
    case 'execStatus':
      return typeOf(EXEC_STATUS_TAG_TYPES, v)
    case 'runStatus':
      return typeOf(RUN_STATUS_TAG_TYPES, v)
    case 'orderStatus':
      return typeOf(ORDER_STATUS_TAG_TYPES, v)
    default:
      return 'info'
  }
}

/** 枚举值中文标签。 */
export function formatEnumLabel(kind: EnumTagKind, v: unknown): string {
  switch (kind) {
    case 'action':
      return formatAction(v)
    case 'execStatus':
      return formatExecStatus(v)
    case 'runStatus':
      return formatRunStatus(v)
    case 'orderStatus':
      return formatOrderStatus(v)
    default:
      return v == null || v === '' ? '—' : String(v)
  }
}

/** 解析研究卡片 signalTags（JSON 数组或逗号分隔）。 */
export function parseSignalTags(v: unknown): string[] {
  if (v == null || v === '') return []
  const raw = String(v).trim()
  if (!raw) return []
  if (raw.startsWith('[')) {
    try {
      const parsed = JSON.parse(raw) as unknown
      if (Array.isArray(parsed)) {
        return parsed
          .map((item) => String(item ?? '').trim())
          .filter(Boolean)
      }
    } catch {
      /* fall through */
    }
  }
  return raw
    .split(/[,，;；|/]/)
    .map((part) => part.trim().replace(/^["']|["']$/g, ''))
    .filter(Boolean)
}
