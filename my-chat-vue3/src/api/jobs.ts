import { ragClient } from '@/utils/http'
import type { AsyncJob } from '@/types/jobs'

export const jobsApi = {
  /** 未完成任务，刷新后补洞 */
  listActive: () => ragClient.get<AsyncJob[]>('/ai/jobs/active'),

  /**
   * 任务状态流。不要走 axios：拦截器会当 Result 解包，且 25s 超时。
   */
  openJobStream: () => new EventSource('/rag/ai/jobs/events'),
}
