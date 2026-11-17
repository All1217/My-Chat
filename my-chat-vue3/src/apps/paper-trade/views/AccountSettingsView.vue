<template>
  <div class="settings">
    <div class="settings-head">
      <h1>账户设置</h1>
      <p>初始资金、定时工作流、偏好设置、投资策略与危险操作</p>
    </div>

    <section class="panel panel-cash">
      <div class="panel-head">
        <h2>初始资金</h2>
        <el-button size="small" type="primary" plain :loading="savingCash" @click="onSaveCash">
          保存
        </el-button>
      </div>
      <div class="cash-row">
        <el-input-number
          v-model="initialCash"
          :min="1"
          :step="1000"
          :precision="2"
          controls-position="right"
        />
        <span class="hint">当前可用现金 {{ formatMoney(account?.cash) }}；无持仓时会同步现金。</span>
      </div>
    </section>

    <section class="panel panel-workflow">
      <div class="panel-head">
        <h2>定时工作流</h2>
        <el-button size="small" type="primary" plain :loading="savingSchedule" @click="onSaveSchedule">
          保存工作流
        </el-button>
      </div>
      <p class="hint schedule-zone">时区：{{ schedule.zoneId || 'Asia/Shanghai' }}（Spring 6 域 cron）</p>
      <div class="sched-row">
        <div class="sched-meta">
          <strong>搜集员</strong>
          <p>默认每天 09:00；可改 cron，如仅工作日 <code>0 0 9 * * MON-FRI</code></p>
        </div>
        <div class="sched-ops">
          <el-button size="small" :loading="researchRunning" @click="onRunResearch">执行一次</el-button>
          <el-switch v-model="schedule.researchEnabled" />
        </div>
      </div>
      <el-input v-model="schedule.researchCron" placeholder="0 0 9 * * *" class="cron-input" />
      <div class="sched-row">
        <div class="sched-meta">
          <strong>交易员（盘后单次）</strong>
          <p>与收盘价撮合一致；建议 ≥15:05，默认 <code>0 35 15 * * MON-FRI</code></p>
        </div>
        <div class="sched-ops">
          <el-button size="small" type="primary" :loading="traderRunning" @click="onRunTrader">
            执行一次
          </el-button>
          <el-switch v-model="schedule.traderEnabled" />
        </div>
      </div>
      <el-input v-model="schedule.traderCron" placeholder="0 35 15 * * MON-FRI" class="cron-input" />
    </section>

    <section class="panel panel-prefs">
      <div class="panel-head">
        <h2>偏好设置</h2>
        <el-button size="small" type="primary" plain :loading="savingPrefs" @click="onSavePrefs">
          保存偏好
        </el-button>
      </div>
      <div class="sched-row">
        <div class="sched-meta">
          <strong>是否纳入自选</strong>
          <p>开：自选股优先进入交易员候选；关：交易员列表不包含自选股</p>
        </div>
        <el-switch v-model="schedule.includeWatchlist" />
      </div>
    </section>

    <section class="panel panel-strategy">
      <div class="panel-head">
        <h2>投资策略</h2>
        <el-button
          size="small"
          type="primary"
          plain
          :loading="savingStrategy"
          @click="onSaveStrategy"
        >
          保存策略
        </el-button>
      </div>
      <el-input
        v-model="strategyText"
        type="textarea"
        :rows="12"
        placeholder="交易员策略正文（系统提示）"
      />
    </section>

    <section class="panel danger-zone">
      <h2>危险操作</h2>
      <div class="danger-row">
        <div>
          <strong>重置账户</strong>
          <p>清空持仓、订单与冷却，现金回到初始资金；保留 AI / 研究 / 阶段历史。</p>
        </div>
        <el-button
          class="btn-reset"
          :loading="resetting"
          @click="onReset"
        >
          重置账户
        </el-button>
      </div>
      <div class="danger-row">
        <div>
          <strong>注销阶段训练</strong>
          <p>彻底清空业务数据（持仓、订单、AI、研究、阶段总结）并重置账户；保留投资策略。</p>
        </div>
        <el-button type="danger" :loading="wiping" @click="onWipe">注销阶段训练</el-button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { paperTradeApi } from '@/apps/paper-trade/api'
import type { PaperAccount } from '@/apps/paper-trade/types'
import { formatMoney } from '@/apps/paper-trade/utils/format'

const account = ref<PaperAccount | null>(null)
const initialCash = ref<number>(20000)
const strategyText = ref('')
const savingCash = ref(false)
const savingStrategy = ref(false)
const savingSchedule = ref(false)
const savingPrefs = ref(false)
const researchRunning = ref(false)
const traderRunning = ref(false)
const resetting = ref(false)
const wiping = ref(false)
let researchTimer: ReturnType<typeof setInterval> | null = null
let traderTimer: ReturnType<typeof setInterval> | null = null

const schedule = reactive({
  researchEnabled: false,
  researchCron: '0 0 9 * * *',
  traderEnabled: false,
  traderCron: '0 35 15 * * MON-FRI',
  zoneId: 'Asia/Shanghai',
  includeWatchlist: true,
})

async function load() {
  const [a, s, sch] = await Promise.all([
    paperTradeApi.account(),
    paperTradeApi.getStrategy(),
    paperTradeApi.getSchedule(),
  ])
  account.value = a
  initialCash.value = Number(a?.initialCash ?? 20000)
  strategyText.value = s?.content ?? ''
  schedule.researchEnabled = !!sch.researchEnabled
  schedule.researchCron = sch.researchCron || '0 0 9 * * *'
  schedule.traderEnabled = !!sch.traderEnabled
  schedule.traderCron = sch.traderCron || '0 35 15 * * MON-FRI'
  schedule.zoneId = sch.zoneId || 'Asia/Shanghai'
  schedule.includeWatchlist = sch.includeWatchlist !== false
}

async function onSaveSchedule() {
  if (!schedule.researchCron?.trim() || !schedule.traderCron?.trim()) {
    ElMessage.warning('cron 不能为空')
    return
  }
  savingSchedule.value = true
  try {
    const saved = await paperTradeApi.saveSchedule({
      researchEnabled: schedule.researchEnabled,
      researchCron: schedule.researchCron.trim(),
      traderEnabled: schedule.traderEnabled,
      traderCron: schedule.traderCron.trim(),
      zoneId: schedule.zoneId,
      includeWatchlist: schedule.includeWatchlist,
    })
    schedule.researchEnabled = !!saved.researchEnabled
    schedule.researchCron = saved.researchCron
    schedule.traderEnabled = !!saved.traderEnabled
    schedule.traderCron = saved.traderCron
    schedule.zoneId = saved.zoneId
    schedule.includeWatchlist = saved.includeWatchlist !== false
    ElMessage.success('定时工作流已保存并重挂')
  } catch {
    /* toast */
  } finally {
    savingSchedule.value = false
  }
}

async function onSavePrefs() {
  if (!schedule.researchCron?.trim() || !schedule.traderCron?.trim()) {
    ElMessage.warning('cron 不能为空')
    return
  }
  savingPrefs.value = true
  try {
    const saved = await paperTradeApi.saveSchedule({
      researchEnabled: schedule.researchEnabled,
      researchCron: schedule.researchCron.trim(),
      traderEnabled: schedule.traderEnabled,
      traderCron: schedule.traderCron.trim(),
      zoneId: schedule.zoneId,
      includeWatchlist: schedule.includeWatchlist,
    })
    schedule.includeWatchlist = saved.includeWatchlist !== false
    ElMessage.success('偏好已保存')
  } catch {
    /* toast */
  } finally {
    savingPrefs.value = false
  }
}

function stopResearchPoll() {
  if (researchTimer) clearInterval(researchTimer)
  researchTimer = null
}

function stopTraderPoll() {
  if (traderTimer) clearInterval(traderTimer)
  traderTimer = null
}

/** 搜集员执行一次：提交后短轮询，结束时提示。 */
async function onRunResearch() {
  researchRunning.value = true
  try {
    const sub = await paperTradeApi.researchRun()
    stopResearchPoll()
    let tries = 0
    researchTimer = setInterval(async () => {
      tries += 1
      try {
        const run = await paperTradeApi.researchRunDetail(sub.runId)
        if (run.status === 'SUCCEEDED' || run.status === 'FAILED') {
          stopResearchPoll()
          researchRunning.value = false
          if (run.status === 'FAILED') ElMessage.error(run.errorMessage || '搜集失败')
          else ElMessage.success('搜集完成')
        }
      } catch {
        /* 单次轮询失败忽略 */
      }
      if (tries > 45) {
        stopResearchPoll()
        researchRunning.value = false
        ElMessage.warning('搜集仍在运行，可到历史记录查看')
      }
    }, 2000)
  } catch {
    researchRunning.value = false
  }
}

/** 交易员执行一次：提交后短轮询，结束时提示。 */
async function onRunTrader() {
  traderRunning.value = true
  try {
    const sub = await paperTradeApi.aiRun()
    stopTraderPoll()
    let tries = 0
    traderTimer = setInterval(async () => {
      tries += 1
      try {
        const run = await paperTradeApi.aiRunDetail(sub.runId)
        if (run.status === 'SUCCEEDED' || run.status === 'FAILED') {
          stopTraderPoll()
          traderRunning.value = false
          if (run.status === 'FAILED') ElMessage.error(run.errorMessage || '交易员本轮失败')
          else ElMessage.success('交易员本轮完成')
        }
      } catch {
        /* 单次轮询失败忽略 */
      }
      if (tries > 45) {
        stopTraderPoll()
        traderRunning.value = false
        ElMessage.warning('交易员仍在运行，可到历史记录查看')
      }
    }, 2000)
  } catch {
    traderRunning.value = false
  }
}

async function onSaveCash() {
  if (!initialCash.value || initialCash.value <= 0) {
    ElMessage.warning('初始资金必须大于 0')
    return
  }
  savingCash.value = true
  try {
    account.value = await paperTradeApi.updateInitialCash(initialCash.value)
    initialCash.value = Number(account.value.initialCash)
    ElMessage.success('初始资金已更新')
  } catch {
    /* toast */
  } finally {
    savingCash.value = false
  }
}

async function onSaveStrategy() {
  if (!strategyText.value.trim()) {
    ElMessage.warning('策略不能为空')
    return
  }
  savingStrategy.value = true
  try {
    const s = await paperTradeApi.saveStrategy(strategyText.value.trim())
    strategyText.value = s.content
    ElMessage.success('策略已保存')
  } catch {
    /* toast */
  } finally {
    savingStrategy.value = false
  }
}

async function onReset() {
  try {
    await ElMessageBox.confirm(
      '将清空持仓、订单与冷却，现金回到初始值。历史记录保留。确定？',
      '重置账户',
      { type: 'warning' },
    )
  } catch {
    return
  }
  resetting.value = true
  try {
    account.value = await paperTradeApi.reset()
    initialCash.value = Number(account.value.initialCash)
    ElMessage.success('账户已重置')
  } catch {
    /* toast */
  } finally {
    resetting.value = false
  }
}

async function onWipe() {
  try {
    await ElMessageBox.confirm(
      '将清空全部业务数据（含 AI、研究、阶段总结）并重置账户，仅保留投资策略。此操作不可恢复。确定？',
      '注销阶段训练',
      { type: 'error', confirmButtonText: '确认注销', confirmButtonClass: 'el-button--danger' },
    )
  } catch {
    return
  }
  wiping.value = true
  try {
    account.value = await paperTradeApi.wipe()
    initialCash.value = Number(account.value.initialCash)
    ElMessage.success('阶段训练已注销')
  } catch {
    /* toast */
  } finally {
    wiping.value = false
  }
}

onMounted(() => {
  load().catch((e: unknown) => {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  })
})

onUnmounted(() => {
  stopResearchPoll()
  stopTraderPoll()
})
</script>

<style scoped lang="less">
.settings {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: stretch;

  > .panel {
    margin-bottom: 0;
  }
}

.settings-head {
  margin-bottom: 0;

  h1 {
    margin: 0;
    font-size: 22px;
    font-weight: 800;
    color: #166534;
  }

  p {
    margin: 2px 0 0;
    font-size: 12px;
    color: #64748b;
  }
}

.panel {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 12px;

  h2 {
    margin: 0;
    font-size: 14px;
    font-weight: 700;
    color: #14532d;
  }
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.cash-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;

  .hint {
    font-size: 12px;
    color: #94a3b8;
  }
}

.hint {
  font-size: 12px;
  color: #94a3b8;
}

.schedule-zone {
  margin: 0 0 10px;
}

.sched-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;

  .sched-meta {
    min-width: 0;

    strong {
      font-size: 13px;
      color: #14532d;
    }

    p {
      margin: 2px 0 0;
      font-size: 12px;
      color: #94a3b8;
    }

    code {
      font-size: 11px;
      background: #f1f5f9;
      padding: 0 4px;
      border-radius: 3px;
    }
  }
}

.sched-ops {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.cron-input {
  margin-bottom: 12px;
}

.danger-zone {
  border-color: #fecaca;

  h2 {
    color: #b91c1c;
    margin-bottom: 10px;
  }
}

.danger-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 10px 0;
  border-top: 1px solid #fee2e2;

  &:first-of-type {
    border-top: none;
    padding-top: 0;
  }

  strong {
    display: block;
    font-size: 13px;
    color: #7f1d1d;
  }

  p {
    margin: 4px 0 0;
    font-size: 12px;
    color: #94a3b8;
    max-width: 520px;
  }
}

.btn-reset {
  background: #fff;
  border: 1px solid #ef4444;
  color: #dc2626;

  &:hover,
  &:focus {
    background: #fef2f2;
    border-color: #dc2626;
    color: #b91c1c;
  }
}
</style>
