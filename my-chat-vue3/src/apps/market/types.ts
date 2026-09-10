/** 一根日 K。 */
export interface KlinePoint {
  date: string
  close: number
  high?: number | null
  low?: number | null
  volume?: number | null
}

/** 同步行情。 */
export interface MarketQuote {
  symbol: string
  market: string
  name: string
  rangeKey: string
  lastPrice: number | null
  changePct: number | null
  high: number | null
  low: number | null
  volume: number | null
  history: KlinePoint[]
}

/** 一次预测任务。 */
export interface MarketForecast {
  id: string
  jobId: string | null
  status: string
  symbol: string
  market: string
  rangeKey: string
  name: string
  lastPrice: number | null
  changePct: number | null
  high: number | null
  low: number | null
  volume: number | null
  history: KlinePoint[]
  forecast: KlinePoint[]
  summary: string | null
  errorMessage: string | null
}

export interface MarketForecastRequest {
  symbol: string
  range: string
}

/** 已保存的投资策略与 AI 评价。 */
export interface MarketStrategy {
  strategyText: string
  evaluation: string
  updatedAt: string
}

export interface MarketStrategyRequest {
  strategyText: string
}

/** 自选股列表。 */
export interface MarketWatchlist {
  symbols: string[]
}

export interface MarketWatchlistRequest {
  symbols: string[]
}

/** 当天抄底提醒。 */
export interface MarketWatchlistAlert {
  pickSymbol: string
  pickName: string
  reason: string
  cached: boolean
}

/** 美股大回撤情景预警。 */
export interface MarketCrashRisk {
  weekKey: string
  generatedAt: string
  window: string
  trigger: string
  impact: string
  status: string
  jobId: string
  errorMessage: string
}

/** 一只指数的市盈率与近五年分位。 */
export interface MarketIndexPeItem {
  code: string
  name: string
  pe: number | null
  percentile: number | null
  sampleSize: number
  comment: string
}

/** 三大指数市盈率日缓存。 */
export interface MarketIndexPe {
  cacheDate: string
  generatedAt: string
  status: string
  errorMessage: string
  indices: MarketIndexPeItem[]
}
