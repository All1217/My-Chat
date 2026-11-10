import { ragClient } from '@/utils/http'
import type { PaperAccount, PaperOrder, PaperOrderRequest, PaperPosition } from '@/apps/paper-trade/types'

/** 模拟炒股 API：全部走 /ai/apps/paper-trade。 */
export const paperTradeApi = {
  account: () => ragClient.get<PaperAccount>('/ai/apps/paper-trade/account'),

  positions: () => ragClient.get<PaperPosition[]>('/ai/apps/paper-trade/positions'),

  orders: () => ragClient.get<PaperOrder[]>('/ai/apps/paper-trade/orders'),

  placeOrder: (payload: PaperOrderRequest) =>
    ragClient.post<PaperOrder>('/ai/apps/paper-trade/orders', payload),

  reset: () => ragClient.post<PaperAccount>('/ai/apps/paper-trade/account/reset'),
}
