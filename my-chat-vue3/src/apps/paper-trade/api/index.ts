import { ragClient } from '@/utils/http'
import type {
  PaperAccount,
  PaperAiDecision,
  PaperAiRun,
  PaperAiSubmit,
  PaperOrder,
  PaperOrderRequest,
  PaperPosition,
  PaperQuote,
  PaperQuoteRange,
  PaperResearchNote,
  PaperResearchNoteAdmin,
  PaperResearchRun,
  PaperScheduleSettings,
  PaperSqlExport,
  PaperStageSummary,
  PaperStrategy,
} from '@/apps/paper-trade/types'

/** 模拟炒股 API：全部走 /ai/apps/paper-trade。 */
export const paperTradeApi = {
  /** 看板行情；可传 AbortSignal 取消慢请求。 */
  quote: (symbol: string, range: PaperQuoteRange | string = '1M', signal?: AbortSignal) =>
    ragClient.get<PaperQuote>('/ai/apps/paper-trade/quote', {
      params: { symbol, range },
      timeout: 90_000,
      signal,
      silent: true,
    }),

  account: () => ragClient.get<PaperAccount>('/ai/apps/paper-trade/account'),

  positions: () => ragClient.get<PaperPosition[]>('/ai/apps/paper-trade/positions'),

  orders: () => ragClient.get<PaperOrder[]>('/ai/apps/paper-trade/orders'),

  placeOrder: (payload: PaperOrderRequest) =>
    ragClient.post<PaperOrder>('/ai/apps/paper-trade/orders', payload),

  reset: () => ragClient.post<PaperAccount>('/ai/apps/paper-trade/account/reset'),

  wipe: () => ragClient.post<PaperAccount>('/ai/apps/paper-trade/account/wipe'),

  updateInitialCash: (initialCash: number) =>
    ragClient.put<PaperAccount>('/ai/apps/paper-trade/account/initial-cash', { initialCash }),

  getStrategy: () => ragClient.get<PaperStrategy>('/ai/apps/paper-trade/strategy'),

  saveStrategy: (content: string) =>
    ragClient.put<PaperStrategy>('/ai/apps/paper-trade/strategy', { content }),

  getSchedule: () => ragClient.get<PaperScheduleSettings>('/ai/apps/paper-trade/schedule'),

  saveSchedule: (body: Partial<PaperScheduleSettings>) =>
    ragClient.put<PaperScheduleSettings>('/ai/apps/paper-trade/schedule', body),

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

  // —— 管理端历史 ——

  adminStageSummaries: (limit = 50) =>
    ragClient.get<PaperStageSummary[]>('/ai/apps/paper-trade/admin/stage-summaries', {
      params: { limit },
    }),

  adminStageSummary: (id: string) =>
    ragClient.get<PaperStageSummary>(`/ai/apps/paper-trade/admin/stage-summaries/${id}`),

  adminCreateStageSummary: (body: Partial<PaperStageSummary>) =>
    ragClient.post<PaperStageSummary>('/ai/apps/paper-trade/admin/stage-summaries', body),

  adminUpdateStageSummary: (id: string, body: Partial<PaperStageSummary>) =>
    ragClient.put<PaperStageSummary>(`/ai/apps/paper-trade/admin/stage-summaries/${id}`, body),

  adminDeleteStageSummary: (id: string) =>
    ragClient.delete<void>(`/ai/apps/paper-trade/admin/stage-summaries/${id}`),

  adminDecisions: (limit = 50) =>
    ragClient.get<PaperAiDecision[]>('/ai/apps/paper-trade/admin/decisions', { params: { limit } }),

  adminDecision: (id: string) =>
    ragClient.get<PaperAiDecision>(`/ai/apps/paper-trade/admin/decisions/${id}`),

  adminUpdateDecision: (id: string, body: Partial<PaperAiDecision>) =>
    ragClient.put<PaperAiDecision>(`/ai/apps/paper-trade/admin/decisions/${id}`, body),

  adminDeleteDecision: (id: string) =>
    ragClient.delete<void>(`/ai/apps/paper-trade/admin/decisions/${id}`),

  adminResearchRuns: (limit = 50) =>
    ragClient.get<PaperResearchRun[]>('/ai/apps/paper-trade/admin/research-runs', {
      params: { limit },
    }),

  adminResearchRun: (id: string) =>
    ragClient.get<PaperResearchRun>(`/ai/apps/paper-trade/admin/research-runs/${id}`),

  adminDeleteResearchRun: (id: string) =>
    ragClient.delete<void>(`/ai/apps/paper-trade/admin/research-runs/${id}`),

  adminResearchNotes: (limit = 50) =>
    ragClient.get<PaperResearchNoteAdmin[]>('/ai/apps/paper-trade/admin/research-notes', {
      params: { limit },
    }),

  adminResearchNote: (symbol: string) =>
    ragClient.get<PaperResearchNoteAdmin>(
      `/ai/apps/paper-trade/admin/research-notes/${encodeURIComponent(symbol)}`,
    ),

  adminCreateResearchNote: (body: Partial<PaperResearchNoteAdmin>) =>
    ragClient.post<PaperResearchNoteAdmin>('/ai/apps/paper-trade/admin/research-notes', body),

  adminUpdateResearchNote: (symbol: string, body: Partial<PaperResearchNoteAdmin>) =>
    ragClient.put<PaperResearchNoteAdmin>(
      `/ai/apps/paper-trade/admin/research-notes/${encodeURIComponent(symbol)}`,
      body,
    ),

  adminDeleteResearchNote: (symbol: string) =>
    ragClient.delete<void>(
      `/ai/apps/paper-trade/admin/research-notes/${encodeURIComponent(symbol)}`,
    ),

  /** 导出历史表全量 INSERT.sql。 */
  adminExportSql: (table: string) =>
    ragClient.get<PaperSqlExport>(
      `/ai/apps/paper-trade/admin/export/${encodeURIComponent(table)}`,
    ),
}
