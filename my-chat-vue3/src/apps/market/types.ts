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
