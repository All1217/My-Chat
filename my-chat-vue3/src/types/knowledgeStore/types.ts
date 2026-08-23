export interface KnowledgeBase {
  id: string
  name: string
  description?: string
  chunkSize: number
  chunkOverlap: number
  topK: number
  similarityThreshold: number
  createdAt: string
  updatedAt?: string
}

/** 更新知识库名称/描述与切分、检索参数 */
export interface KnowledgeBaseUpdate {
  id: string
  name?: string
  description?: string
  chunkSize: number
  chunkOverlap: number
  topK: number
  similarityThreshold: number
}

export interface DocumentMeta {
  id: string
  kbId: string
  filename: string
  fileSize: number
  fileType: string
  chunkCount: number
  status: 'PROCESSING' | 'READY' | 'FAILED'
  errorMessage?: string | null
  createdAt: string
}
