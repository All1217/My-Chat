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
/** 运行时风控（与闸门 / 锁定策略同源）。 */
export interface PaperRiskSettings {
  maxPositions: number
  maxSingleAllocation: number
  riskBudgetPct: number
  riskBudgetTightPct: number
  drawdownRestrictPct: number
  lossStreakLimit: number
  buyPauseDays: number
  reviewConfidenceThreshold: number
  autoExecute: boolean
  cooldownDaysStock: number
  cooldownDaysEtf: number
  stockTakeProfitPct: number
  stockStopLossPct: number
  stockMaxHoldDays: number
  etfTakeProfitPct: number
  etfStopLossPct: number
  etfMaxHoldDays: number
  qdiiTakeProfitPct: number
  qdiiStopLossPct: number
  qdiiMaxHoldDays: number
  updatedAt?: string | null
}

export interface PaperStrategy {
  id: string
  /** 拼接后的完整正文（只读产物）。 */
  content: string
  lockedContent?: string
  customContent?: string
  risk?: PaperRiskSettings
  updatedAt?: string | null
}

/** 工作流蓝图节点。 */
export interface PaperWorkflowNode {
  id: string
  label: string
  type: 'start' | 'process' | 'ai' | 'end' | string
  x: number
  y: number
}

/** 工作流边。 */
export interface PaperWorkflowEdge {
  id: string
  source: string
  target: string
}

/** 最近一轮 domainStep。 */
export interface PaperWorkflowStepStatus {
  step: string
  status?: string | null
  summary?: string | null
  error?: string | null
}

/** 最近一轮 agent 步骤摘要。 */
export interface PaperWorkflowAgentStep {
  index?: number | null
  action?: string | null
  status?: string | null
  observation?: string | null
}

/** 最近一次运行。 */
export interface PaperWorkflowLatestRun {
  runId: string
  status?: string | null
  createdAt?: string | null
  finishedAt?: string | null
  errorMessage?: string | null
  agentRunId?: string | null
  domainSteps?: PaperWorkflowStepStatus[] | null
  agentSteps?: PaperWorkflowAgentStep[] | null
}

/** 一套工作流图。 */
export interface PaperWorkflowGraph {
  id: string
  title: string
  nodes: PaperWorkflowNode[]
  edges: PaperWorkflowEdge[]
  latest?: PaperWorkflowLatestRun | null
}

/** 工作流总览。 */
export interface PaperWorkflowOverview {
  graphs: PaperWorkflowGraph[]
}

/** 搜集员 / 交易员 / 票仓定时工作流与偏好配置。 */
export interface PaperScheduleSettings {
  researchEnabled: boolean
  researchCron: string
  traderEnabled: boolean
  traderCron: string
  cleanupEnabled: boolean
  cleanupCron: string
  analystEnabled: boolean
  analystCron: string
  /** 价值等级=2 滞留自然日阈值。 */
  warehouseTier2RetainDays: number
  evaluatorEnabled: boolean
  evaluatorCron: string
  /** 评估结果收件人。 */
  evaluatorEmail: string
  zoneId: string
  /** 是否纳入自选：是则自选优先，否则交易员看不到自选。 */
  includeWatchlist: boolean
  /** 是否启用工具检索（Exa/Bocha/Tavily），默认关。 */
  researchToolSearchEnabled: boolean
  updatedAt?: string | null
}

/** 票仓清理 / 分析员轮次。 */
export interface PaperWarehouseRun {
  id: string
  jobId?: string | null
  status: string
  screenedCount?: number | null
  adjustedCount?: number | null
  statsJson?: string | null
  frameworkTrace?: string | null
  errorMessage?: string | null
  createdAt?: string | null
  finishedAt?: string | null
}

/** 交易评估员轮次。 */
export interface PaperEvaluatorRun {
  id: string
  jobId?: string | null
  status: string
  triggerType?: string | null
  stageSummaryId?: string | null
  emailStatus?: string | null
  statsJson?: string | null
  frameworkTrace?: string | null
  errorMessage?: string | null
  createdAt?: string | null
  finishedAt?: string | null
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
  /** 0已持仓 1应该买入 2继续观察 3移出票仓。 */
  valueTier?: number | null
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
  /** 0已持仓 1应该买入 2继续观察 3移出票仓。 */
  valueTier?: number | null
  runId?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

/** 历史表 SQL 导出。 */
export interface PaperSqlExport {
  tableName: string
  filename: string
  content: string
}

/** 搜集轮次。 */
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

/** 本地爬虫 MCP 连通探测。 */
export interface PaperCrawlerProbe {
  ok: boolean
  status: 'DISABLED' | 'NO_CLIENT' | 'ERROR' | 'OK' | string
  message: string
  detail?: string | null
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
