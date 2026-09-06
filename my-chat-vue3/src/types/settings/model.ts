/** 脱敏后的对话模型配置 */
export interface LlmModel {
  id: string
  name: string
  provider: string
  baseUrl: string
  apiKeyMasked: string
  modelId: string
  maxTokens: number
  enabled: boolean
  isDefault: boolean
  createdAt?: string
  updatedAt?: string
}

/** 供应商下拉预设 */
export interface LlmProviderPreset {
  key: string
  label: string
  baseUrl: string
  modelHints: string[]
}

/** 新建 / 更新请求 */
export interface LlmModelUpsert {
  id?: string
  name: string
  provider: string
  baseUrl: string
  apiKey?: string
  modelId: string
  maxTokens: number
  enabled: boolean
  isDefault?: boolean
}

/** 测通请求：有 id 用已存配置 */
export interface LlmModelTestRequest {
  id?: string
  provider?: string
  baseUrl?: string
  apiKey?: string
  modelId?: string
  maxTokens?: number
}

export interface LlmModelTestResult {
  ok: boolean
  message: string
  reply?: string | null
}
