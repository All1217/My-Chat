import { paperTradeApi } from '@/apps/paper-trade/api'

/** 历史资源列定义。 */
export interface HistoryColumn {
  prop: string
  label: string
  width?: number
  minWidth?: number
  overflow?: boolean
  /** datetime → yyyy-MM-dd HH:mm:ss；bool → 是/否；valueTier → 已持仓/应该买入… */
  format?: 'datetime' | 'bool' | 'valueTier'
}

/** 可扩展历史资源注册项。 */
export interface HistoryResource {
  key: string
  title: string
  /** 对应库表名，用于导出 INSERT.sql */
  tableName: string
  idField: string
  creatable: boolean
  columns: HistoryColumn[]
  formFields: {
    key: string
    label: string
    type?: 'text' | 'textarea' | 'number' | 'switch' | 'valueTier'
  }[]
  list: (limit?: number) => Promise<Record<string, unknown>[]>
  get: (id: string) => Promise<Record<string, unknown>>
  create?: (body: Record<string, unknown>) => Promise<Record<string, unknown>>
  update?: (id: string, body: Record<string, unknown>) => Promise<Record<string, unknown>>
  remove: (id: string) => Promise<void>
}

function asRows<T>(rows: T[] | null | undefined): Record<string, unknown>[] {
  return (rows ?? []) as unknown as Record<string, unknown>[]
}

function asRow<T>(row: T): Record<string, unknown> {
  return row as unknown as Record<string, unknown>
}

/** 首批四个资源；后续加表只往数组追加。 */
export const historyResources: HistoryResource[] = [
  {
    key: 'stage-summaries',
    title: '阶段总结',
    tableName: 'paper_stage_summary',
    idField: 'id',
    creatable: true,
    columns: [
      { prop: 'stageNo', label: '阶段', width: 70 },
      { prop: 'endedAt', label: '结束', width: 170, format: 'datetime' },
      { prop: 'startCash', label: '起始资金', width: 100 },
      { prop: 'endEquity', label: '结束权益', width: 100 },
      { prop: 'tradeCount', label: '笔数', width: 70 },
      { prop: 'winRate', label: '胜率', width: 80 },
      { prop: 'summaryText', label: '总结', minWidth: 180, overflow: true },
    ],
    formFields: [
      { key: 'stageNo', label: '阶段号', type: 'number' },
      { key: 'startCash', label: '起始资金', type: 'number' },
      { key: 'endEquity', label: '结束权益', type: 'number' },
      { key: 'tradeCount', label: '成交笔数', type: 'number' },
      { key: 'winRate', label: '胜率', type: 'number' },
      { key: 'payoffRatio', label: '盈亏比', type: 'number' },
      { key: 'maxDrawdown', label: '最大回撤', type: 'number' },
      { key: 'totalFee', label: '总费用', type: 'number' },
      { key: 'lossSources', label: '出场来源', type: 'text' },
      { key: 'summaryText', label: '总结正文', type: 'textarea' },
    ],
    list: async (limit) => asRows(await paperTradeApi.adminStageSummaries(limit)),
    get: async (id) => asRow(await paperTradeApi.adminStageSummary(id)),
    create: async (body) => asRow(await paperTradeApi.adminCreateStageSummary(body)),
    update: async (id, body) => asRow(await paperTradeApi.adminUpdateStageSummary(id, body)),
    remove: (id) => paperTradeApi.adminDeleteStageSummary(id),
  },
  {
    key: 'decisions',
    title: 'AI 决策',
    tableName: 'paper_ai_decision',
    idField: 'id',
    creatable: false,
    columns: [
      { prop: 'createdAt', label: '时间', width: 170, format: 'datetime' },
      { prop: 'action', label: '动作', width: 70 },
      { prop: 'symbol', label: '代码', width: 90 },
      { prop: 'qty', label: '数量', width: 70 },
      { prop: 'confidence', label: '置信度', width: 80 },
      { prop: 'execStatus', label: '状态', width: 100 },
      { prop: 'rationale', label: '理由', minWidth: 160, overflow: true },
    ],
    formFields: [
      { key: 'action', label: '动作', type: 'text' },
      { key: 'symbol', label: '代码', type: 'text' },
      { key: 'name', label: '名称', type: 'text' },
      { key: 'qty', label: '数量', type: 'number' },
      { key: 'refPrice', label: '参考价', type: 'number' },
      { key: 'confidence', label: '置信度', type: 'number' },
      { key: 'execStatus', label: '执行状态', type: 'text' },
      { key: 'skipReason', label: '跳过原因', type: 'text' },
      { key: 'needsHumanReview', label: '需人工复核', type: 'switch' },
      { key: 'rationale', label: '理由', type: 'textarea' },
    ],
    list: async (limit) => asRows(await paperTradeApi.adminDecisions(limit)),
    get: async (id) => asRow(await paperTradeApi.adminDecision(id)),
    update: async (id, body) => asRow(await paperTradeApi.adminUpdateDecision(id, body)),
    remove: (id) => paperTradeApi.adminDeleteDecision(id),
  },
  {
    key: 'research-runs',
    title: '搜集轮次',
    tableName: 'paper_research_run',
    idField: 'id',
    creatable: false,
    columns: [
      { prop: 'createdAt', label: '创建', width: 170, format: 'datetime' },
      { prop: 'status', label: '状态', width: 100 },
      { prop: 'screenedCount', label: '筛选数', width: 80 },
      { prop: 'keptCount', label: '留下数', width: 80 },
      { prop: 'errorMessage', label: '错误', minWidth: 160, overflow: true },
      { prop: 'finishedAt', label: '结束', width: 170, format: 'datetime' },
    ],
    formFields: [
      { key: 'id', label: 'ID', type: 'text' },
      { key: 'status', label: '状态', type: 'text' },
      { key: 'screenedCount', label: '筛选数', type: 'number' },
      { key: 'keptCount', label: '留下数', type: 'number' },
      { key: 'agentRunId', label: 'Agent Run', type: 'text' },
      { key: 'errorMessage', label: '错误', type: 'textarea' },
      { key: 'frameworkTrace', label: '轨迹', type: 'textarea' },
    ],
    list: async (limit) => asRows(await paperTradeApi.adminResearchRuns(limit)),
    get: async (id) => asRow(await paperTradeApi.adminResearchRun(id)),
    remove: (id) => paperTradeApi.adminDeleteResearchRun(id),
  },
  {
    key: 'ai-runs',
    title: '交易轮次',
    tableName: 'paper_ai_run',
    idField: 'id',
    creatable: false,
    columns: [
      { prop: 'createdAt', label: '创建', width: 170, format: 'datetime' },
      { prop: 'status', label: '状态', width: 100 },
      { prop: 'agentRunId', label: 'Agent Run', width: 180, overflow: true },
      { prop: 'errorMessage', label: '错误', minWidth: 160, overflow: true },
      { prop: 'finishedAt', label: '结束', width: 170, format: 'datetime' },
    ],
    formFields: [
      { key: 'id', label: 'ID', type: 'text' },
      { key: 'jobId', label: 'Job', type: 'text' },
      { key: 'status', label: '状态', type: 'text' },
      { key: 'agentRunId', label: 'Agent Run', type: 'text' },
      { key: 'errorMessage', label: '错误', type: 'textarea' },
      { key: 'frameworkTrace', label: '轨迹', type: 'textarea' },
      { key: 'rawResponse', label: '模型输出', type: 'textarea' },
      { key: 'contextJson', label: '上下文', type: 'textarea' },
    ],
    list: async (limit) => asRows(await paperTradeApi.aiRuns(limit)),
    get: async (id) => asRow(await paperTradeApi.aiRunDetail(id)),
    remove: (id) => paperTradeApi.adminDeleteAiRun(id),
  },
  {
    key: 'research-notes',
    title: '研究卡片',
    tableName: 'paper_research_note',
    idField: 'symbol',
    creatable: true,
    columns: [
      { prop: 'updatedAt', label: '更新', width: 170, format: 'datetime' },
      { prop: 'symbol', label: '代码', width: 90 },
      { prop: 'name', label: '名称', width: 120 },
      { prop: 'kept', label: '留下', width: 70, format: 'bool' },
      { prop: 'watchlist', label: '自选', width: 70, format: 'bool' },
      { prop: 'valueTier', label: '价值等级', width: 100, format: 'valueTier' },
      { prop: 'fitScore', label: '契合', width: 70 },
      { prop: 'evidenceScore', label: '证据', width: 70 },
      { prop: 'sourceTier', label: '信源', width: 100 },
      { prop: 'signalTags', label: '标签', minWidth: 120, overflow: true },
      { prop: 'story', label: '故事', minWidth: 180, overflow: true },
      { prop: 'rejectReason', label: '拒绝原因', minWidth: 120, overflow: true },
    ],
    formFields: [
      { key: 'symbol', label: '代码', type: 'text' },
      { key: 'name', label: '名称', type: 'text' },
      { key: 'kept', label: '留下', type: 'switch' },
      { key: 'watchlist', label: '是否自选', type: 'switch' },
      { key: 'valueTier', label: '价值等级', type: 'valueTier' },
      { key: 'fitScore', label: '契合分', type: 'text' },
      { key: 'evidenceScore', label: '证据分', type: 'text' },
      { key: 'signalTags', label: '信号标签', type: 'text' },
      { key: 'sourceTier', label: '信源层级', type: 'text' },
      { key: 'story', label: '故事', type: 'textarea' },
      { key: 'risks', label: '风险', type: 'textarea' },
      { key: 'rejectReason', label: '拒绝原因', type: 'text' },
      { key: 'sourcesJson', label: '来源 JSON', type: 'textarea' },
      { key: 'quoteSummary', label: '行情摘要', type: 'textarea' },
    ],
    list: async (limit) => asRows(await paperTradeApi.adminResearchNotes(limit)),
    get: async (id) => asRow(await paperTradeApi.adminResearchNote(id)),
    create: async (body) => asRow(await paperTradeApi.adminCreateResearchNote(body)),
    update: async (id, body) => asRow(await paperTradeApi.adminUpdateResearchNote(id, body)),
    remove: (id) => paperTradeApi.adminDeleteResearchNote(id),
  },
]
