export interface KnowledgeBase {
  id: string
  name: string
  description?: string
  createdAt: string
  updatedAt?: string
}

export interface DocumentMeta {
  id: string
  kbId: string
  filename: string
  fileSize: number
  fileType: string
  chunkCount: number
  status: 'PROCESSING' | 'READY' | 'FAILED'
  createdAt: string
}
