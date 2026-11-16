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
