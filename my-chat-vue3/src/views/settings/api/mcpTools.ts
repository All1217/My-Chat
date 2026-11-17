import { ragClient } from '@/utils/http'

/** 设置页 MCP/搜索连接项。 */
export interface McpToolCatalogItem {
  id: string
  name: string
  type: string
  description: string
  enabled: boolean
  tools: string[]
  updatedAt?: string | null
}

/** MCP 工具管理 API。 */
export const mcpToolsApi = {
  list: () => ragClient.get<McpToolCatalogItem[]>('/ai/settings/mcp-tools'),
  setEnabled: (id: string, enabled: boolean) =>
    ragClient.put<McpToolCatalogItem>(`/ai/settings/mcp-tools/${encodeURIComponent(id)}`, {
      enabled,
    }),
}
