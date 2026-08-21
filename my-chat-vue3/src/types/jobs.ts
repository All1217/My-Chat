/** 与后端 AsyncJobVO 对齐 */
export type JobStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export interface AsyncJob {
  id: string
  jobType: string
  status: JobStatus
  title: string
  refId?: string | null
  errorMessage?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  finishedAt?: string | null
}

export function isTerminalStatus(status: string | undefined): boolean {
  return status === 'SUCCEEDED' || status === 'FAILED'
}
