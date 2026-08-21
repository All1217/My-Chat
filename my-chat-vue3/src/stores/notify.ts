import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElNotification } from 'element-plus'
import { jobsApi } from '@/api/jobs'
import type { AsyncJob } from '@/types/jobs'
import { isTerminalStatus } from '@/types/jobs'
import { playNotifySound, unlockNotifySound } from '@/utils/notifySound'

/**
 * 全局任务通知：挂在 App.vue，路由切换不断线。
 * 后端 Job 是事实源；本 store 只订阅 SSE 并弹出 ElNotification。
 */
export const useNotifyStore = defineStore('notify', () => {
  const activeJobs = ref<AsyncJob[]>([])
  const connected = ref(false)

  let eventSource: EventSource | null = null
  let gestureBound = false
  const terminalCallbacks: Array<(job: AsyncJob) => void> = []

  function bindUnlockGesture() {
    if (gestureBound || typeof document === 'undefined') return
    gestureBound = true
    document.addEventListener('pointerdown', unlockNotifySound, { once: true })
  }

  function upsertActive(job: AsyncJob) {
    const idx = activeJobs.value.findIndex(j => j.id === job.id)
    if (isTerminalStatus(job.status)) {
      if (idx >= 0) activeJobs.value.splice(idx, 1)
      return
    }
    if (idx >= 0) {
      activeJobs.value[idx] = job
    } else {
      activeJobs.value.push(job)
    }
  }

  function notifyTerminal(job: AsyncJob) {
    const ok = job.status === 'SUCCEEDED'
    ElNotification({
      title: ok ? '任务完成' : '任务失败',
      message: ok ? job.title : (job.errorMessage || job.title),
      type: ok ? 'success' : 'error',
      duration: 5000,
    })
    playNotifySound()
    for (const cb of terminalCallbacks) {
      try {
        cb(job)
      } catch {
        /* 业务回调失败不影响通知 */
      }
    }
  }

  function onJobEvent(job: AsyncJob) {
    upsertActive(job)
    if (isTerminalStatus(job.status)) {
      notifyTerminal(job)
    }
  }

  async function connect() {
    bindUnlockGesture()
    if (eventSource) return

    try {
      activeJobs.value = (await jobsApi.listActive()) ?? []
    } catch {
      activeJobs.value = []
    }

    eventSource = jobsApi.openJobStream()
    eventSource.addEventListener('job', (ev: MessageEvent) => {
      try {
        const job = JSON.parse(ev.data) as AsyncJob
        onJobEvent(job)
      } catch {
        /* 忽略非 JSON */
      }
    })
    eventSource.onopen = () => {
      connected.value = true
    }
    eventSource.onerror = () => {
      connected.value = false
    }
  }

  function disconnect() {
    eventSource?.close()
    eventSource = null
    connected.value = false
  }

  /** 后续知识库页可挂上刷新文档列表 */
  function onJobTerminal(cb: (job: AsyncJob) => void) {
    terminalCallbacks.push(cb)
    return () => {
      const i = terminalCallbacks.indexOf(cb)
      if (i >= 0) terminalCallbacks.splice(i, 1)
    }
  }

  return {
    activeJobs,
    connected,
    connect,
    disconnect,
    unlockSound: unlockNotifySound,
    onJobTerminal,
  }
})
