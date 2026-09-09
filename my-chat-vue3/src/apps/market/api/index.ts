import { ragClient } from '@/utils/http'
import type {
  MarketForecast,
  MarketForecastRequest,
  MarketQuote,
  MarketStrategy,
  MarketStrategyRequest,
  MarketWatchlist,
  MarketWatchlistAlert,
  MarketWatchlistRequest,
} from '@/apps/market/types'

export const marketApi = {
  quote: (symbol: string, range: string) =>
    ragClient.get<MarketQuote>('/ai/apps/market/quote', { params: { symbol, range } }),

  submitForecast: (payload: MarketForecastRequest) =>
    ragClient.post<MarketForecast>('/ai/apps/market/forecast', payload),

  getForecast: (id: string) =>
    ragClient.get<MarketForecast>(`/ai/apps/market/forecast/${id}`),

  getStrategy: () =>
    ragClient.get<MarketStrategy>('/ai/apps/market/strategy', { silent: true }),

  saveStrategy: (payload: MarketStrategyRequest) =>
    ragClient.post<MarketStrategy>('/ai/apps/market/strategy', payload),

  optimizeStrategy: () =>
    ragClient.post<MarketStrategy>('/ai/apps/market/strategy/optimize'),

  getWatchlist: () =>
    ragClient.get<MarketWatchlist>('/ai/apps/market/watchlist', { silent: true }),

  saveWatchlist: (payload: MarketWatchlistRequest) =>
    ragClient.put<MarketWatchlist>('/ai/apps/market/watchlist', payload),

  getWatchlistAlert: () =>
    ragClient.get<MarketWatchlistAlert>('/ai/apps/market/watchlist/alert', {
      silent: true,
      timeout: 120000,
    }),
}
