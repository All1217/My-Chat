import { ragClient } from '@/utils/http'
import type { FileTreeNode, FileInfo } from '@/types/settings/types'

export const workspaceApi = {
  tree: (path = '') =>
    ragClient.get<FileTreeNode[]>('/ai/file/workspace/tree', { params: { path } }),

  treeLazy: (path = '') =>
    ragClient.get<FileTreeNode[]>('/ai/file/workspace/tree/lazy', { params: { path } }),

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

  switchRoot: (path: string) =>
    ragClient.post<string>('/ai/file/workspace/switch', { path }),

  /** 列出文件系统根目录（如 C:\、D:\） */
  listRoots: () =>
    ragClient.get<string[]>('/ai/file/workspace/roots'),

  /** 浏览绝对路径下的子目录（仅目录） */
  browse: (path: string) =>
    ragClient.get<FileInfo[]>('/ai/file/workspace/browse', { params: { path } }),

  /** 输入联想：根据已输入的绝对路径片段推荐可能的子目录 */
  suggest: (query: string) =>
    ragClient.get<string[]>('/ai/file/workspace/suggest', { params: { query } }),

  /** 校验路径是否可用作工作区目录（无副作用） */
  validate: (path: string, silent = false) =>
    ragClient.get<void>('/ai/file/workspace/validate', { params: { path }, silent }),
}
