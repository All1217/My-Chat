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
