/** 价值等级：0已持仓 1应该买入 2继续观察 3移出票仓。 */
export const VALUE_TIER_LABELS: Record<number, string> = {
  0: '已持仓',
  1: '应该买入',
  2: '继续观察',
  3: '移出票仓',
}

/** 表单下拉选项。 */
export const VALUE_TIER_OPTIONS = [
  { value: 0, label: '0 · 已持仓' },
  { value: 1, label: '1 · 应该买入' },
  { value: 2, label: '2 · 继续观察' },
  { value: 3, label: '3 · 移出票仓' },
]

/** 数字 → 可读标签；未知则回退原值。 */
export function formatValueTier(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  const n = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(n)) return String(value)
  return VALUE_TIER_LABELS[n] ?? String(n)
}
