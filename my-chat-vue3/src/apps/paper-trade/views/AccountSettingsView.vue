<template>
  <div class="settings">
    <div class="settings-head">
      <h1>账户设置</h1>
      <p>常规设置、定时工作流、投资策略与危险操作</p>
    </div>

    <section class="panel panel-general">
      <div class="panel-head">
        <h2>常规设置</h2>
        <el-button size="small" type="primary" plain :loading="savingGeneral" @click="onSaveGeneral">
          保存常规
        </el-button>
      </div>
      <div class="cash-row">
        <span class="field-label">初始资金</span>
        <el-input-number
          v-model="initialCash"
          :min="1"
          :step="1000"
          :precision="2"
          controls-position="right"
        />
        <span class="hint">当前可用现金 {{ formatMoney(account?.cash) }}；无持仓时会同步现金。</span>
      </div>
      <div class="sched-row">
        <div class="sched-meta">
          <strong>是否纳入自选</strong>
          <p>开：自选股优先进入交易员候选；关：交易员列表不包含自选股</p>
        </div>
        <el-switch v-model="schedule.includeWatchlist" />
      </div>
      <div class="cash-row">
        <span class="field-label">观察票滞留天数</span>
        <el-input-number
          v-model="schedule.warehouseTier2RetainDays"
          :min="0"
          :max="365"
          :step="1"
          controls-position="right"
        />
        <span class="hint">价值等级=2 超过该自然日数后，清理任务会改为 3（移出）。</span>
      </div>
      <div class="sched-row">
        <div class="sched-meta">
          <strong>测试本地爬虫连通性</strong>
          <p>检查 mcp-crawler-server 是否已启用，再调 list_adapters 探测本机 mcp-server-crawler（8103）</p>
        </div>
        <div class="sched-ops">
          <el-button size="small" :loading="crawlerProbing" @click="onProbeCrawler">测试连通性</el-button>
        </div>
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
          <p>{{ cronHintLine(DEFAULT_CRONS.research, schedule.researchCron) }}</p>
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
          <p>{{ cronHintLine(DEFAULT_CRONS.trader, schedule.traderCron) }}</p>
        </div>
        <div class="sched-ops">
          <el-button size="small" type="primary" :loading="traderRunning" @click="onRunTrader">
            执行一次
          </el-button>
          <el-switch v-model="schedule.traderEnabled" />
        </div>
      </div>
      <el-input v-model="schedule.traderCron" placeholder="0 35 15 * * MON-FRI" class="cron-input" />
      <div class="sched-row">
        <div class="sched-meta">
          <strong>票仓分析员</strong>
          <p>{{ cronHintLine(DEFAULT_CRONS.analyst, schedule.analystCron) }}</p>
        </div>
        <div class="sched-ops">
          <el-button size="small" :loading="analystRunning" @click="onRunAnalyst">执行一次</el-button>
          <el-switch v-model="schedule.analystEnabled" />
        </div>
      </div>
      <el-input v-model="schedule.analystCron" placeholder="0 0 18 * * *" class="cron-input" />
      <div class="sched-row">
        <div class="sched-meta">
          <strong>票仓清理</strong>
          <p>{{ cronHintLine(DEFAULT_CRONS.cleanup, schedule.cleanupCron) }}</p>
        </div>
        <div class="sched-ops">
          <el-button size="small" :loading="cleanupRunning" @click="onRunCleanup">执行一次</el-button>
          <el-switch v-model="schedule.cleanupEnabled" />
        </div>
      </div>
      <el-input v-model="schedule.cleanupCron" placeholder="0 0 23 * * *" class="cron-input" />
      <div class="sched-row">
        <div class="sched-meta">
          <strong>交易评估员</strong>
          <p>{{ cronHintLine(DEFAULT_CRONS.evaluator, schedule.evaluatorCron) }}</p>
        </div>
        <div class="sched-ops">
          <el-button size="small" :loading="evaluatorRunning" @click="onRunEvaluator">执行一次</el-button>
          <el-switch v-model="schedule.evaluatorEnabled" />
        </div>
      </div>
      <el-input v-model="schedule.evaluatorCron" placeholder="0 0 10 * * SAT" class="cron-input" />
      <div class="sched-row">
        <div class="sched-meta">
          <strong>评估结果收件人</strong>
          <p>空则跳过发信；默认 QQ 邮箱</p>
        </div>
      </div>
      <el-input
        v-model="schedule.evaluatorEmail"
        placeholder="3116459510@qq.com"
        class="cron-input"
      />
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
          保存策略与风控
        </el-button>
      </div>
      <p class="hint strategy-hint">
        数值改动会同步闸门与锁定文案；风格区只影响模型推理。输出格式 / value_tier / 整手等平台硬约束不可改。
      </p>

      <h3 class="sub-title">结构化风控</h3>
      <div class="risk-grid">
        <label>最多持仓
          <el-input-number v-model="risk.maxPositions" :min="1" :max="20" controls-position="right" />
        </label>
        <label>单票上限
          <el-input-number
            v-model="risk.maxSingleAllocation"
            :min="0.05"
            :max="1"
            :step="0.05"
            :precision="2"
            controls-position="right"
          />
        </label>
        <label>风险预算
          <el-input-number
            v-model="risk.riskBudgetPct"
            :min="0.001"
            :max="0.2"
            :step="0.005"
            :precision="3"
            controls-position="right"
          />
        </label>
        <label>收紧预算
          <el-input-number
            v-model="risk.riskBudgetTightPct"
            :min="0.001"
            :max="0.2"
            :step="0.005"
            :precision="3"
            controls-position="right"
          />
        </label>
        <label>回撤熔断
          <el-input-number
            v-model="risk.drawdownRestrictPct"
            :min="0.01"
            :max="0.5"
            :step="0.01"
            :precision="2"
            controls-position="right"
          />
        </label>
        <label>连亏笔数
          <el-input-number v-model="risk.lossStreakLimit" :min="1" :max="20" controls-position="right" />
        </label>
        <label>暂停天数
          <el-input-number v-model="risk.buyPauseDays" :min="0" :max="30" controls-position="right" />
        </label>
        <label>置信度阈值
          <el-input-number
            v-model="risk.reviewConfidenceThreshold"
            :min="0"
            :max="1"
            :step="0.05"
            :precision="2"
            controls-position="right"
          />
        </label>
        <label class="switch-label">自动执行
          <el-switch v-model="risk.autoExecute" />
        </label>
        <label>股冷却日
          <el-input-number v-model="risk.cooldownDaysStock" :min="0" :max="30" controls-position="right" />
        </label>
        <label>ETF冷却日
          <el-input-number v-model="risk.cooldownDaysEtf" :min="0" :max="30" controls-position="right" />
        </label>
        <label>股止盈
          <el-input-number
            v-model="risk.stockTakeProfitPct"
            :min="0.005"
            :max="0.5"
            :step="0.005"
            :precision="3"
            controls-position="right"
          />
        </label>
        <label>股止损
          <el-input-number
            v-model="risk.stockStopLossPct"
            :min="0.005"
            :max="0.5"
            :step="0.005"
            :precision="3"
            controls-position="right"
          />
        </label>
        <label>股最长持有
          <el-input-number v-model="risk.stockMaxHoldDays" :min="1" :max="120" controls-position="right" />
        </label>
        <label>ETF止盈
          <el-input-number
            v-model="risk.etfTakeProfitPct"
            :min="0.005"
            :max="0.5"
            :step="0.005"
            :precision="3"
            controls-position="right"
          />
        </label>
        <label>ETF止损
          <el-input-number
            v-model="risk.etfStopLossPct"
            :min="0.005"
            :max="0.5"
            :step="0.005"
            :precision="3"
            controls-position="right"
          />
        </label>
        <label>ETF最长持有
          <el-input-number v-model="risk.etfMaxHoldDays" :min="1" :max="120" controls-position="right" />
        </label>
        <label>QDII止盈
          <el-input-number
            v-model="risk.qdiiTakeProfitPct"
            :min="0.005"
            :max="0.5"
            :step="0.005"
            :precision="3"
            controls-position="right"
          />
        </label>
        <label>QDII止损
          <el-input-number
            v-model="risk.qdiiStopLossPct"
            :min="0.005"
            :max="0.5"
            :step="0.005"
            :precision="3"
            controls-position="right"
          />
        </label>
        <label>QDII最长持有
          <el-input-number v-model="risk.qdiiMaxHoldDays" :min="1" :max="120" controls-position="right" />
        </label>
      </div>

      <h3 class="sub-title">平台锁定策略 <span class="lock-tag">只读</span></h3>
      <el-input
        :model-value="lockedContent"
        type="textarea"
        :rows="10"
        readonly
        class="locked-area"
      />

      <h3 class="sub-title">可编辑风格与偏好</h3>
      <el-input
        v-model="customContent"
        type="textarea"
        :rows="10"
        placeholder="身份、风格、低点/企稳叙事与原则口吻"
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
import type { PaperAccount, PaperRiskSettings } from '@/apps/paper-trade/types'
import { cronHintLine, DEFAULT_CRONS } from '@/apps/paper-trade/utils/cronZh'
import { formatMoney } from '@/apps/paper-trade/utils/format'

const account = ref<PaperAccount | null>(null)
const initialCash = ref<number>(20000)
const lockedContent = ref('')
const customContent = ref('')
const risk = reactive<PaperRiskSettings>({
  maxPositions: 2,
  maxSingleAllocation: 0.95,
  riskBudgetPct: 0.02,
  riskBudgetTightPct: 0.01,
  drawdownRestrictPct: 0.08,
  lossStreakLimit: 3,
  buyPauseDays: 2,
  reviewConfidenceThreshold: 0.55,
  autoExecute: true,
  cooldownDaysStock: 2,
  cooldownDaysEtf: 1,
  stockTakeProfitPct: 0.04,
  stockStopLossPct: 0.03,
  stockMaxHoldDays: 10,
  etfTakeProfitPct: 0.03,
  etfStopLossPct: 0.02,
  etfMaxHoldDays: 15,
  qdiiTakeProfitPct: 0.045,
  qdiiStopLossPct: 0.03,
  qdiiMaxHoldDays: 10,
})
const savingGeneral = ref(false)
const savingStrategy = ref(false)
const crawlerProbing = ref(false)

function applyRisk(r?: Partial<PaperRiskSettings> | null) {
  if (!r) return
  Object.assign(risk, {
    maxPositions: r.maxPositions ?? 2,
    maxSingleAllocation: r.maxSingleAllocation ?? 0.95,
    riskBudgetPct: r.riskBudgetPct ?? 0.02,
    riskBudgetTightPct: r.riskBudgetTightPct ?? 0.01,
    drawdownRestrictPct: r.drawdownRestrictPct ?? 0.08,
    lossStreakLimit: r.lossStreakLimit ?? 3,
    buyPauseDays: r.buyPauseDays ?? 2,
    reviewConfidenceThreshold: r.reviewConfidenceThreshold ?? 0.55,
    autoExecute: r.autoExecute !== false,
    cooldownDaysStock: r.cooldownDaysStock ?? 2,
    cooldownDaysEtf: r.cooldownDaysEtf ?? 1,
    stockTakeProfitPct: r.stockTakeProfitPct ?? 0.04,
    stockStopLossPct: r.stockStopLossPct ?? 0.03,
    stockMaxHoldDays: r.stockMaxHoldDays ?? 10,
    etfTakeProfitPct: r.etfTakeProfitPct ?? 0.03,
    etfStopLossPct: r.etfStopLossPct ?? 0.02,
    etfMaxHoldDays: r.etfMaxHoldDays ?? 15,
    qdiiTakeProfitPct: r.qdiiTakeProfitPct ?? 0.045,
    qdiiStopLossPct: r.qdiiStopLossPct ?? 0.03,
    qdiiMaxHoldDays: r.qdiiMaxHoldDays ?? 10,
  })
}
const savingSchedule = ref(false)
const researchRunning = ref(false)
const traderRunning = ref(false)
const analystRunning = ref(false)
const cleanupRunning = ref(false)
const evaluatorRunning = ref(false)
const resetting = ref(false)
const wiping = ref(false)
let researchTimer: ReturnType<typeof setInterval> | null = null
let traderTimer: ReturnType<typeof setInterval> | null = null
let analystTimer: ReturnType<typeof setInterval> | null = null
let cleanupTimer: ReturnType<typeof setInterval> | null = null
let evaluatorTimer: ReturnType<typeof setInterval> | null = null

const schedule = reactive({
  researchEnabled: false,
  researchCron: DEFAULT_CRONS.research,
  traderEnabled: false,
  traderCron: DEFAULT_CRONS.trader,
  analystEnabled: true,
  analystCron: DEFAULT_CRONS.analyst,
  cleanupEnabled: true,
  cleanupCron: DEFAULT_CRONS.cleanup,
  warehouseTier2RetainDays: 2,
  evaluatorEnabled: true,
  evaluatorCron: DEFAULT_CRONS.evaluator,
  evaluatorEmail: '3116459510@qq.com',
  zoneId: 'Asia/Shanghai',
  includeWatchlist: true,
})

function applySchedule(sch: {
  researchEnabled?: boolean
  researchCron?: string
  traderEnabled?: boolean
  traderCron?: string
  analystEnabled?: boolean
  analystCron?: string
  cleanupEnabled?: boolean
  cleanupCron?: string
  warehouseTier2RetainDays?: number
  evaluatorEnabled?: boolean
  evaluatorCron?: string
  evaluatorEmail?: string
  zoneId?: string
  includeWatchlist?: boolean
}) {
  schedule.researchEnabled = !!sch.researchEnabled
  schedule.researchCron = sch.researchCron || DEFAULT_CRONS.research
  schedule.traderEnabled = !!sch.traderEnabled
  schedule.traderCron = sch.traderCron || DEFAULT_CRONS.trader
  schedule.analystEnabled = sch.analystEnabled !== false
  schedule.analystCron = sch.analystCron || DEFAULT_CRONS.analyst
  schedule.cleanupEnabled = sch.cleanupEnabled !== false
  schedule.cleanupCron = sch.cleanupCron || DEFAULT_CRONS.cleanup
  schedule.warehouseTier2RetainDays =
    sch.warehouseTier2RetainDays == null ? 2 : Number(sch.warehouseTier2RetainDays)
  schedule.evaluatorEnabled = sch.evaluatorEnabled !== false
  schedule.evaluatorCron = sch.evaluatorCron || DEFAULT_CRONS.evaluator
  schedule.evaluatorEmail = sch.evaluatorEmail || '3116459510@qq.com'
  schedule.zoneId = sch.zoneId || 'Asia/Shanghai'
  schedule.includeWatchlist = sch.includeWatchlist !== false
}

function schedulePayload() {
  return {
    researchEnabled: schedule.researchEnabled,
    researchCron: schedule.researchCron.trim(),
    traderEnabled: schedule.traderEnabled,
    traderCron: schedule.traderCron.trim(),
    analystEnabled: schedule.analystEnabled,
    analystCron: schedule.analystCron.trim(),
    cleanupEnabled: schedule.cleanupEnabled,
    cleanupCron: schedule.cleanupCron.trim(),
    warehouseTier2RetainDays: schedule.warehouseTier2RetainDays,
    evaluatorEnabled: schedule.evaluatorEnabled,
    evaluatorCron: schedule.evaluatorCron.trim(),
    evaluatorEmail: schedule.evaluatorEmail.trim(),
    zoneId: schedule.zoneId,
    includeWatchlist: schedule.includeWatchlist,
  }
}

async function load() {
  const [a, s, sch] = await Promise.all([
    paperTradeApi.account(),
    paperTradeApi.getStrategy(),
    paperTradeApi.getSchedule(),
  ])
  account.value = a
  initialCash.value = Number(a?.initialCash ?? 20000)
  lockedContent.value = s?.lockedContent ?? ''
  customContent.value = s?.customContent ?? s?.content ?? ''
  applyRisk(s?.risk)
  applySchedule(sch)
}

async function onSaveSchedule() {
  if (
    !schedule.researchCron?.trim() ||
    !schedule.traderCron?.trim() ||
    !schedule.analystCron?.trim() ||
    !schedule.cleanupCron?.trim() ||
    !schedule.evaluatorCron?.trim()
  ) {
    ElMessage.warning('cron 不能为空')
    return
  }
  savingSchedule.value = true
  try {
    const saved = await paperTradeApi.saveSchedule(schedulePayload())
    applySchedule(saved)
    ElMessage.success('定时工作流已保存并重挂')
  } catch {
    /* toast */
  } finally {
    savingSchedule.value = false
  }
}

/** 探测本地爬虫 MCP：未启用则提醒，已启用再测连通。 */
async function onProbeCrawler() {
  crawlerProbing.value = true
  try {
    const r = await paperTradeApi.crawlerProbe()
    if (r.status === 'DISABLED') {
      ElMessage.warning(r.message || '爬虫 MCP 未启用，请先到设置页开启')
      return
    }
    if (r.ok) {
      ElMessage.success(r.message || '爬虫 MCP 连通正常')
      return
    }
    ElMessage.error(r.message || '爬虫 MCP 连通失败')
  } catch {
    /* toast */
  } finally {
    crawlerProbing.value = false
  }
}

/** 保存常规：初始资金 + 自选 + 滞留天数。 */
async function onSaveGeneral() {
  if (!initialCash.value || initialCash.value <= 0) {
    ElMessage.warning('初始资金必须大于 0')
    return
  }
  if (
    !schedule.researchCron?.trim() ||
    !schedule.traderCron?.trim() ||
    !schedule.analystCron?.trim() ||
    !schedule.cleanupCron?.trim() ||
    !schedule.evaluatorCron?.trim()
  ) {
    ElMessage.warning('cron 不能为空')
    return
  }
  savingGeneral.value = true
  try {
    account.value = await paperTradeApi.updateInitialCash(initialCash.value)
    initialCash.value = Number(account.value.initialCash)
    const saved = await paperTradeApi.saveSchedule(schedulePayload())
    applySchedule(saved)
    ElMessage.success('常规设置已保存')
  } catch {
    /* toast */
  } finally {
    savingGeneral.value = false
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

function stopAnalystPoll() {
  if (analystTimer) clearInterval(analystTimer)
  analystTimer = null
}

function stopCleanupPoll() {
  if (cleanupTimer) clearInterval(cleanupTimer)
  cleanupTimer = null
}

function stopEvaluatorPoll() {
  if (evaluatorTimer) clearInterval(evaluatorTimer)
  evaluatorTimer = null
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

async function onRunAnalyst() {
  analystRunning.value = true
  try {
    const sub = await paperTradeApi.warehouseAnalystRun()
    stopAnalystPoll()
    let tries = 0
    analystTimer = setInterval(async () => {
      tries += 1
      try {
        const run = await paperTradeApi.warehouseAnalystRunDetail(sub.runId)
        if (run.status === 'SUCCEEDED' || run.status === 'FAILED') {
          stopAnalystPoll()
          analystRunning.value = false
          if (run.status === 'FAILED') ElMessage.error(run.errorMessage || '分析员失败')
          else ElMessage.success('票仓分析完成')
        }
      } catch {
        /* ignore */
      }
      if (tries > 60) {
        stopAnalystPoll()
        analystRunning.value = false
        ElMessage.warning('分析员仍在运行')
      }
    }, 2000)
  } catch {
    analystRunning.value = false
  }
}

async function onRunCleanup() {
  cleanupRunning.value = true
  try {
    const sub = await paperTradeApi.warehouseCleanupRun()
    stopCleanupPoll()
    let tries = 0
    cleanupTimer = setInterval(async () => {
      tries += 1
      try {
        const run = await paperTradeApi.warehouseCleanupRunDetail(sub.runId)
        if (run.status === 'SUCCEEDED' || run.status === 'FAILED') {
          stopCleanupPoll()
          cleanupRunning.value = false
          if (run.status === 'FAILED') ElMessage.error(run.errorMessage || '清理失败')
          else ElMessage.success('票仓清理完成')
        }
      } catch {
        /* ignore */
      }
      if (tries > 30) {
        stopCleanupPoll()
        cleanupRunning.value = false
        ElMessage.warning('清理仍在运行')
      }
    }, 1500)
  } catch {
    cleanupRunning.value = false
  }
}

async function onRunEvaluator() {
  evaluatorRunning.value = true
  try {
    const sub = await paperTradeApi.evaluatorRun()
    if (!sub?.runId) {
      evaluatorRunning.value = false
      ElMessage.warning(sub?.status === 'SKIPPED_ACTIVE' ? '评估员已有活动轮' : '未提交成功')
      return
    }
    stopEvaluatorPoll()
    let tries = 0
    evaluatorTimer = setInterval(async () => {
      tries += 1
      try {
        const run = await paperTradeApi.evaluatorRunDetail(sub.runId)
        if (run.status === 'SUCCEEDED' || run.status === 'FAILED') {
          stopEvaluatorPoll()
          evaluatorRunning.value = false
          if (run.status === 'FAILED') ElMessage.error(run.errorMessage || '评估员失败')
          else ElMessage.success('交易评估完成')
        }
      } catch {
        /* ignore */
      }
      if (tries > 60) {
        stopEvaluatorPoll()
        evaluatorRunning.value = false
        ElMessage.warning('评估员仍在运行')
      }
    }, 2000)
  } catch {
    evaluatorRunning.value = false
  }
}

async function onSaveStrategy() {
  if (!customContent.value.trim()) {
    ElMessage.warning('可编辑风格不能为空')
    return
  }
  savingStrategy.value = true
  try {
    const s = await paperTradeApi.saveStrategy({
      customContent: customContent.value.trim(),
      risk: { ...risk },
    })
    lockedContent.value = s.lockedContent ?? ''
    customContent.value = s.customContent ?? ''
    applyRisk(s.risk)
    ElMessage.success('策略与风控已保存')
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
  stopAnalystPoll()
  stopCleanupPoll()
  stopEvaluatorPoll()
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
  margin-bottom: 10px;

  .field-label {
    font-size: 13px;
    font-weight: 600;
    color: #14532d;
  }

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

.strategy-hint {
  margin: 0 0 10px;
}

.sub-title {
  margin: 12px 0 8px;
  font-size: 13px;
  font-weight: 700;
  color: #14532d;
  display: flex;
  align-items: center;
  gap: 8px;
}

.lock-tag {
  font-size: 11px;
  font-weight: 600;
  color: #92400e;
  background: #fef3c7;
  border: 1px solid #fcd34d;
  border-radius: 4px;
  padding: 0 6px;
}

.risk-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 8px 10px;

  label {
    display: flex;
    flex-direction: column;
    gap: 4px;
    font-size: 12px;
    font-weight: 600;
    color: #334155;
  }

  .switch-label {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    padding-top: 18px;
  }

  :deep(.el-input-number) {
    width: 100%;
  }
}

.locked-area {
  :deep(textarea) {
    background: #f8fafc;
    color: #475569;
    cursor: default;
  }
}
</style>
