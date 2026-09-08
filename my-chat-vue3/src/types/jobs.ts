/** 与后端 AsyncJobVO 对齐 */
export type JobStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export interface AsyncJob {
  id: string
  jobType: string
  status: JobStatus
  title: string
  refId?: string | null
  errorMessage?: string | null
  /** 缺省 true：成功弹窗+音效；false 时成功静默，失败仍提醒 */
  notifyOnSuccess?: boolean
  createdAt?: string | null
  updatedAt?: string | null
  finishedAt?: string | null
}

export function isTerminalStatus(status: string | undefined): boolean {
  return status === 'SUCCEEDED' || status === 'FAILED'
}
