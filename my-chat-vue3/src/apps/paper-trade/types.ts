/** 模拟账户。 */
export interface PaperAccount {
  id: string
  name: string
  cash: number
  initialCash: number
  equity?: number | null
  peakEquity?: number | null
  drawdownPct?: number | null
  lossStreak?: number | null
  buyPauseUntil?: string | null
  drawdownRestricted?: boolean | null
  latestSummary?: string | null
}

/** 持仓行。 */
export interface PaperPosition {
  symbol: string
  market: string
  qty: number
  availableQty?: number
  avgCost: number
  marketValue?: number | null
  takeProfitPct?: number | null
  stopLossPct?: number | null
  maxHoldDays?: number | null
  openedAt?: string | null
}

/** 订单行。 */
export interface PaperOrder {
  id: string
  side: 'BUY' | 'SELL' | string
  symbol: string
  market: string
  qty: number
  fillPrice?: number | null
  fee?: number | null
  source?: string | null
  decisionId?: string | null
  status: 'FILLED' | 'REJECTED' | string
  rejectReason?: string | null
  createdAt?: string | null
}

/** 市价下单请求。 */
export interface PaperOrderRequest {
  symbol: string
  side: 'BUY' | 'SELL'
  qty: number
}

/** AI 策略。 */
export interface PaperStrategy {
  id: string
  content: string
  updatedAt?: string | null
}

/** AI 轮次提交结果。 */
export interface PaperAiSubmit {
  runId: string
  jobId: string
  status: string
}

/** AI 决策。 */
export interface PaperAiDecision {
  id: string
  runId: string
  action: string
  symbol?: string | null
  name?: string | null
  qty?: number | null
  refPrice?: number | null
  takeProfitPct?: number | null
  stopLossPct?: number | null
  maxHoldDays?: number | null
  cooldownDays?: number | null
  confidence?: number | null
  rationale?: string | null
  risks?: string[] | null
  needsHumanReview?: boolean | null
  execStatus: string
  skipReason?: string | null
  orderId?: string | null
  createdAt?: string | null
}

/** AI 轮次。 */
export interface PaperAiRun {
  id: string
  jobId?: string | null
  status: string
  contextJson?: string | null
  rawResponse?: string | null
  errorMessage?: string | null
  createdAt?: string | null
  finishedAt?: string | null
  decisions?: PaperAiDecision[] | null
}

/** 已留下的研究卡片。 */
export interface PaperResearchNote {
  symbol: string
  name?: string | null
  story?: string | null
  risks?: string | null
  sourceTitles?: string | null
  updatedAt?: string | null
}

/** 搜集轮次状态。 */
export interface PaperResearchRun {
  id: string
  status: string
  errorMessage?: string | null
  screenedCount?: number | null
  keptCount?: number | null
}
