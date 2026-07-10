import { ragClient } from '@/utils/http'
import type { KnowledgeBase, DocumentMeta } from '@/types/knowledgeStore/types'

export interface UploadResult {
  documentId: string
  filename: string
  message: string
  embeddingCount: number
}

export const knowledgeApi = {
  list: () => ragClient.get<KnowledgeBase[]>('/ai/knowledge-base/list'),

  documents: (kbId: string) =>
    ragClient.get<DocumentMeta[]>('/ai/knowledge-base/documents', { params: { kbId } }),

  create: (name: string, description: string) =>
    ragClient.post<void>('/ai/knowledge-base/create', null, {
      params: { name, description },
    }),

  remove: (id: string) =>
    ragClient.post<void>('/ai/knowledge-base/delete', null, { params: { id } }),

  upload: (file: File, kbId: string) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('kbId', kbId)
    return ragClient.post<UploadResult>('/ai/file/upload', formData)
  },

  deleteDocument: (id: string) =>
    ragClient.post<void>('/ai/file/delete', { id }),
}
