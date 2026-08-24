import { ragClient } from '@/utils/http'
import type { KnowledgeBase, KnowledgeBaseUpdate, DocumentMeta } from '@/types/knowledgeStore/types'

export const knowledgeApi = {
  list: () => ragClient.get<KnowledgeBase[]>('/ai/knowledge-base/list'),

  documents: (kbId: string) =>
    ragClient.get<DocumentMeta[]>('/ai/knowledge-base/documents', { params: { kbId } }),

  create: (name: string, description: string) =>
    ragClient.post<KnowledgeBase>('/ai/knowledge-base/create', null, {
      params: { name, description },
    }),

  update: (payload: KnowledgeBaseUpdate) =>
    ragClient.post<KnowledgeBase>('/ai/knowledge-base/update', payload),

  remove: (id: string) =>
    ragClient.post<void>('/ai/knowledge-base/delete', null, { params: { id } }),

  uploadBatch: (files: File[], kbId: string) => {
    const formData = new FormData()
    formData.append('kbId', kbId)
    for (const file of files) {
      formData.append('files', file)
    }
    return ragClient.post<DocumentMeta[]>('/ai/knowledge-base/documents/upload', formData, {
      timeout: 60_000,
    })
  },

  reindexDocument: (id: string) =>
    ragClient.post<DocumentMeta>('/ai/knowledge-base/documents/reindex', null, {
      params: { id },
    }),

  deleteDocument: (id: string) =>
    ragClient.post<void>('/ai/file/delete', { id }),
}
