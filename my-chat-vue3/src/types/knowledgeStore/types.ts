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

/** 召回测试请求；topK/阈值不传则用知识库已存值 */
export interface KnowledgeRetrieveTestRequest {
  kbId: string
  query: string
  topK?: number
  similarityThreshold?: number
}

export interface KnowledgeRetrieveHit {
  text: string
  score: number | null
  filename?: string | null
  documentId?: string | null
  summary?: string | null
}

export interface KnowledgeRetrieveTestResponse {
  kbId: string
  topK: number
  similarityThreshold: number
  hits: KnowledgeRetrieveHit[]
}

/** 只读分段列表的单条 */
export interface DocumentChunkItem {
  position: number
  content: string
  summary?: string | null
}

/** 某文档的只读分段 */
export interface DocumentChunkListResponse {
  documentId: string
  filename: string
  chunks: DocumentChunkItem[]
}
