import { ragClient } from '@/utils/http'
import type {
  LlmModel,
  LlmModelTestRequest,
  LlmModelTestResult,
  LlmModelUpsert,
  LlmProviderPreset,
} from '@/types/settings/model'

export const modelApi = {
  list: () => ragClient.get<LlmModel[]>('/ai/model/list'),

  providers: () => ragClient.get<LlmProviderPreset[]>('/ai/model/providers'),

  create: (payload: LlmModelUpsert) =>
    ragClient.post<LlmModel>('/ai/model/create', payload),

  update: (payload: LlmModelUpsert) =>
    ragClient.post<LlmModel>('/ai/model/update', payload),

  remove: (id: string) =>
    ragClient.post<void>('/ai/model/delete', null, { params: { id } }),

  setDefault: (id: string) =>
    ragClient.post<LlmModel>('/ai/model/set-default', null, { params: { id } }),

  test: (payload: LlmModelTestRequest) =>
    ragClient.post<LlmModelTestResult>('/ai/model/test', payload, { timeout: 60_000 }),
}
