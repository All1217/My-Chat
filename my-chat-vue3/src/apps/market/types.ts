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
