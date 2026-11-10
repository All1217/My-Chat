/** 模拟账户。 */
export interface PaperAccount {
  id: string
  name: string
  cash: number
  initialCash: number
}

/** 持仓行。 */
export interface PaperPosition {
  symbol: string
  market: string
  qty: number
  avgCost: number
  marketValue?: number | null
}

/** 订单行。 */
export interface PaperOrder {
  id: string
  side: 'BUY' | 'SELL' | string
  symbol: string
  market: string
  qty: number
  fillPrice?: number | null
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
