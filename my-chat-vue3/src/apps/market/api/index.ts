import { ragClient } from '@/utils/http'
import type { MarketForecast, MarketForecastRequest, MarketQuote } from '@/apps/market/types'

export const marketApi = {
  quote: (symbol: string, range: string) =>
    ragClient.get<MarketQuote>('/ai/apps/market/quote', { params: { symbol, range } }),

  submitForecast: (payload: MarketForecastRequest) =>
    ragClient.post<MarketForecast>('/ai/apps/market/forecast', payload),

  getForecast: (id: string) =>
    ragClient.get<MarketForecast>(`/ai/apps/market/forecast/${id}`),
}
