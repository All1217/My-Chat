import { ragClient } from '@/utils/http'
import type {
  PaperAccount,
  PaperAiDecision,
  PaperAiRun,
  PaperAiSubmit,
  PaperOrder,
  PaperOrderRequest,
  PaperPosition,
  PaperResearchNote,
  PaperResearchRun,
  PaperStrategy,
} from '@/apps/paper-trade/types'

/** 模拟炒股 API：全部走 /ai/apps/paper-trade。 */
export const paperTradeApi = {
  account: () => ragClient.get<PaperAccount>('/ai/apps/paper-trade/account'),

  positions: () => ragClient.get<PaperPosition[]>('/ai/apps/paper-trade/positions'),

  orders: () => ragClient.get<PaperOrder[]>('/ai/apps/paper-trade/orders'),

  placeOrder: (payload: PaperOrderRequest) =>
    ragClient.post<PaperOrder>('/ai/apps/paper-trade/orders', payload),

  reset: () => ragClient.post<PaperAccount>('/ai/apps/paper-trade/account/reset'),

  getStrategy: () => ragClient.get<PaperStrategy>('/ai/apps/paper-trade/strategy'),

  saveStrategy: (content: string) =>
    ragClient.put<PaperStrategy>('/ai/apps/paper-trade/strategy', { content }),

  aiRun: () => ragClient.post<PaperAiSubmit>('/ai/apps/paper-trade/ai/run'),

  aiRuns: (limit = 20) =>
    ragClient.get<PaperAiRun[]>('/ai/apps/paper-trade/ai/runs', { params: { limit } }),

  aiRunDetail: (id: string) => ragClient.get<PaperAiRun>(`/ai/apps/paper-trade/ai/runs/${id}`),

  decisions: (limit = 30) =>
    ragClient.get<PaperAiDecision[]>('/ai/apps/paper-trade/ai/decisions', { params: { limit } }),

  confirmDecision: (id: string) =>
    ragClient.post<PaperAiDecision>(`/ai/apps/paper-trade/ai/decisions/${id}/confirm`),

  dismissDecision: (id: string) =>
    ragClient.post<PaperAiDecision>(`/ai/apps/paper-trade/ai/decisions/${id}/dismiss`),

  researchNotes: (limit = 40) =>
    ragClient.get<PaperResearchNote[]>('/ai/apps/paper-trade/research/notes', { params: { limit } }),

  researchRun: () => ragClient.post<PaperAiSubmit>('/ai/apps/paper-trade/research/run'),

  researchRunDetail: (id: string) =>
    ragClient.get<PaperResearchRun>(`/ai/apps/paper-trade/research/runs/${id}`),
}
