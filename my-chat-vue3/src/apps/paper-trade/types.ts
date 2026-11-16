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

/** 搜集员 / 交易员定时工作流与偏好配置。 */
export interface PaperScheduleSettings {
  researchEnabled: boolean
  researchCron: string
  traderEnabled: boolean
  traderCron: string
  zoneId: string
  /** 是否纳入自选：是则自选优先，否则交易员看不到自选。 */
  includeWatchlist: boolean
  /** 是否启用工具检索（Exa/Bocha/Tavily），默认关。 */
  researchToolSearchEnabled: boolean
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

/** 已留下的研究卡片（持仓页）。 */
export interface PaperResearchNote {
  symbol: string
  name?: string | null
  story?: string | null
  risks?: string | null
  sourceTitles?: string | null
  fitScore?: number | null
  evidenceScore?: number | null
  signalTags?: string | null
  sourceTier?: string | null
  updatedAt?: string | null
}

/** 管理端研究卡片。 */
export interface PaperResearchNoteAdmin {
  symbol: string
  name?: string | null
  quoteSummary?: string | null
  story?: string | null
  risks?: string | null
  kept?: boolean | null
  /** 是否自选。 */
  watchlist?: boolean | null
  rejectReason?: string | null
  sourcesJson?: string | null
  fitScore?: number | null
  evidenceScore?: number | null
  signalTags?: string | null
  sourceTier?: string | null
  runId?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

/** 搜集轮次状态。 */
export interface PaperResearchRun {
  id: string
  jobId?: string | null
  status: string
  errorMessage?: string | null
  screenedCount?: number | null
  keptCount?: number | null
  frameworkTrace?: string | null
  createdAt?: string | null
  finishedAt?: string | null
}

/** 看板横轴档位。 */
export type PaperQuoteRange = '1M' | '3M' | '1Y' | '3Y' | '5Y' | 'ALL'

/** 看板 K 线点。 */
export interface PaperKlinePoint {
  date: string
  open?: number | null
  close?: number | null
  high?: number | null
  low?: number | null
  volume?: number | null
  amount?: number | null
  turnoverRate?: number | null
}

/** 看板行情。 */
export interface PaperQuote {
  symbol: string
  market?: string | null
  name?: string | null
  rangeKey?: string | null
  lastPrice?: number | null
  changePct?: number | null
  high?: number | null
  low?: number | null
  volume?: number | null
  amount?: number | null
  turnoverRate?: number | null
  history?: PaperKlinePoint[] | null
}

/** 阶段总结。 */
export interface PaperStageSummary {
  id: string
  stageNo: number
  startedAt?: string | null
  endedAt?: string | null
  startCash: number
  endEquity: number
  tradeCount: number
  winRate?: number | null
  payoffRatio?: number | null
  maxDrawdown?: number | null
  avgHoldDays?: number | null
  totalFee: number
  lossSources?: string | null
  summaryText: string
  createdAt?: string | null
}
