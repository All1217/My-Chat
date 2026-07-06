import { ragClient } from '@/utils/http'
import type { FileTreeNode, FileInfo } from '@/types/settings/types'

export const workspaceApi = {
  tree: (path = '') =>
    ragClient.get<FileTreeNode[]>('/ai/file/workspace/tree', { params: { path } }),

  list: (path: string) =>
    ragClient.get<FileInfo[]>('/ai/file/workspace/list', { params: { path } }),

  createFolder: (path: string, name: string) =>
    ragClient.post<void>('/ai/file/workspace/folder', null, { params: { path, name } }),

  importFiles: (formData: FormData) =>
    ragClient.post<void>('/ai/file/workspace/import', formData),

  readText: (path: string) =>
    ragClient.get<string>('/ai/file/workspace/read', { params: { path } }),

  readBinary: (path: string) =>
    ragClient.get<{ base64: string; mimeType: string }>(
      '/ai/file/workspace/read/binary',
      { params: { path } },
    ),

  rename: (path: string, newName: string) =>
    ragClient.post<void>('/ai/file/workspace/rename', { path, newName }),

  remove: (path: string) =>
    ragClient.post<void>('/ai/file/workspace/delete', { path }),
}
