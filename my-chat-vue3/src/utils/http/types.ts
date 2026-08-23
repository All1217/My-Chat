/** 与后端 Result.java 对齐 */
export interface ResultData<T = unknown> {
  code: number
  message: string
  data: T
}

export class ApiError extends Error {
  constructor(
    public code: number,
    message: string,
    public silent = false,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/** 扩展 Axios 请求配置 */
export interface RequestOptions {
  params?: Record<string, unknown>
  /** 不弹出 ElMessage */
  silent?: boolean
  /** 覆盖后端 message 作为提示文案 */
  errorMsg?: string
  /** 覆盖客户端默认 timeout（毫秒） */
  timeout?: number
}
