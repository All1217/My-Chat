<template>
  <div class="paper">
    <header class="paper-header">
      <el-button @click="goLobby">回到大厅</el-button>
      <div class="header-actions">
        <el-button :loading="researchRunning" @click="onResearch">搜集一轮</el-button>
        <el-button type="primary" :loading="aiRunning" @click="onAiRun">AI 跑一轮</el-button>
        <el-button type="danger" plain :loading="resetting" @click="onReset">重置账户</el-button>
      </div>
    </header>

    <main class="paper-main">
      <h1 class="paper-title">模拟炒股</h1>
      <p class="paper-banner">虚拟盘 · AI 低吸高抛波段 · 不接实盘 · 成交价取东财最新收盘</p>

      <section class="paper-card">
        <div class="cash-row">
          <span class="label">可用现金</span>
          <strong>{{ formatMoney(account?.cash) }}</strong>
        </div>
        <div class="cash-row muted">
          <span class="label">账户权益</span>
          <span>{{ formatMoney(account?.equity) }}</span>
        </div>
        <div class="cash-row muted">
          <span class="label">阶段高点 / 回撤</span>
          <span>{{ formatMoney(account?.peakEquity) }} / {{ pct(account?.drawdownPct) }}</span>
        </div>
        <div class="cash-row muted">
          <span class="label">连亏 / 暂停至</span>
          <span>{{ account?.lossStreak ?? 0 }} / {{ account?.buyPauseUntil || '无' }}</span>
        </div>
        <div class="cash-row muted">
          <span class="label">回撤收紧</span>
          <span>{{ account?.drawdownRestricted ? '是，只买最流动 ETF' : '否' }}</span>
        </div>
        <div class="cash-row muted">
          <span class="label">初始资金</span>
          <span>{{ formatMoney(account?.initialCash) }}</span>
        </div>
        <p v-if="account?.latestSummary" class="summary">{{ account.latestSummary }}</p>
        <p v-if="lastRunHint" class="hint">{{ lastRunHint }}</p>
      </section>

      <section class="paper-card">
        <div class="card-head">
          <h2>AI 策略</h2>
          <el-button size="small" type="primary" plain :loading="savingStrategy" @click="onSaveStrategy">
            保存策略
          </el-button>
        </div>
        <el-input
          v-model="strategyText"
          type="textarea"
          :rows="8"
          placeholder="交易员策略正文（系统提示）"
        />
      </section>

      <section class="paper-card">
        <h2>市价下单</h2>
        <el-form :inline="true" @submit.prevent>
          <el-form-item label="代码">
            <el-input v-model="form.symbol" placeholder="如 600519" style="width: 140px" />
          </el-form-item>
          <el-form-item label="方向">
            <el-select v-model="form.side" style="width: 100px">
              <el-option label="买入" value="BUY" />
              <el-option label="卖出" value="SELL" />
            </el-select>
          </el-form-item>
          <el-form-item label="数量">
            <el-input-number v-model="form.qty" :min="1" :step="100" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submitting" @click="onSubmit">下单</el-button>
          </el-form-item>
        </el-form>
        <p class="hint">A 股数量须为 100 的整数倍；卖出受 T+1 可卖数量约束。</p>
      </section>

      <section class="paper-card">
        <h2>持仓</h2>
        <el-table :data="positions" stripe empty-text="暂无持仓" size="small">
          <el-table-column prop="symbol" label="代码" width="100" />
          <el-table-column prop="market" label="市场" width="70" />
          <el-table-column prop="qty" label="数量" width="80" />
          <el-table-column prop="availableQty" label="可卖" width="80" />
          <el-table-column label="成本" width="110">
            <template #default="{ row }">{{ formatMoney(row.avgCost) }}</template>
          </el-table-column>
          <el-table-column label="市值" min-width="110">
            <template #default="{ row }">{{ formatMoney(row.marketValue) }}</template>
          </el-table-column>
          <el-table-column label="止盈/止损" min-width="120">
            <template #default="{ row }">
              {{ pct(row.takeProfitPct) }} / {{ pct(row.stopLossPct) }}
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="paper-card">
        <div class="card-head">
          <h2>研究卡片</h2>
          <el-button size="small" @click="loadNotes">刷新</el-button>
        </div>
        <p v-if="researchHint" class="hint">{{ researchHint }}</p>
        <el-table :data="notes" stripe empty-text="还没有留下的卡片" size="small">
          <el-table-column prop="updatedAt" label="更新" width="160" />
          <el-table-column prop="symbol" label="代码" width="90" />
          <el-table-column prop="name" label="名称" width="140" />
          <el-table-column prop="story" label="故事" min-width="200" show-overflow-tooltip />
          <el-table-column prop="sourceTitles" label="来源" min-width="160" show-overflow-tooltip />
        </el-table>
      </section>

      <section class="paper-card">
        <div class="card-head">
          <h2>AI 决策</h2>
          <el-button size="small" @click="loadDecisions">刷新</el-button>
        </div>
        <el-table :data="decisions" stripe empty-text="暂无决策" size="small">
          <el-table-column prop="createdAt" label="时间" width="160" />
          <el-table-column prop="action" label="动作" width="70" />
          <el-table-column prop="symbol" label="代码" width="90" />
          <el-table-column prop="qty" label="数量" width="70" />
          <el-table-column label="置信度" width="80">
            <template #default="{ row }">
              {{ row.confidence == null ? '—' : Number(row.confidence).toFixed(2) }}
            </template>
          </el-table-column>
          <el-table-column prop="execStatus" label="状态" width="100" />
          <el-table-column prop="rationale" label="理由" min-width="160" show-overflow-tooltip />
          <el-table-column prop="skipReason" label="跳过/拒因" min-width="120" show-overflow-tooltip />
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <template v-if="row.execStatus === 'PENDING' && (row.action === 'BUY' || row.action === 'SELL')">
                <el-button link type="primary" @click="onConfirm(row.id)">确认下单</el-button>
                <el-button link type="info" @click="onDismiss(row.id)">忽略</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="paper-card">
        <h2>最近订单</h2>
        <el-table :data="orders" stripe empty-text="暂无订单" size="small">
          <el-table-column prop="createdAt" label="时间" width="160" />
          <el-table-column prop="source" label="来源" width="80" />
          <el-table-column prop="side" label="方向" width="70" />
          <el-table-column prop="symbol" label="代码" width="100" />
          <el-table-column prop="qty" label="数量" width="80" />
          <el-table-column label="成交价" width="110">
            <template #default="{ row }">{{ formatMoney(row.fillPrice) }}</template>
          </el-table-column>
          <el-table-column label="费用" width="90">
            <template #default="{ row }">{{ formatMoney(row.fee) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="rejectReason" label="说明" min-width="140" show-overflow-tooltip />
        </el-table>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { paperTradeApi } from '@/apps/paper-trade/api'
import type {
  PaperAccount,
  PaperAiDecision,
  PaperOrder,
  PaperPosition,
  PaperResearchNote,
} from '@/apps/paper-trade/types'

const router = useRouter()
const account = ref<PaperAccount | null>(null)
const positions = ref<PaperPosition[]>([])
const orders = ref<PaperOrder[]>([])
const decisions = ref<PaperAiDecision[]>([])
const notes = ref<PaperResearchNote[]>([])
const strategyText = ref('')
const submitting = ref(false)
const resetting = ref(false)
const savingStrategy = ref(false)
const aiRunning = ref(false)
const researchRunning = ref(false)
const lastRunHint = ref('')
const researchHint = ref('')
let pollTimer: ReturnType<typeof setInterval> | null = null
let researchTimer: ReturnType<typeof setInterval> | null = null

const form = reactive({
  symbol: '600519',
  side: 'BUY' as 'BUY' | 'SELL',
  qty: 100,
})

function goLobby() {
  router.push({ name: 'lobby' })
}

function formatMoney(v: number | null | undefined) {
  if (v === null || v === undefined || Number.isNaN(Number(v))) return '—'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 })
}

function pct(v: number | null | undefined) {
  if (v === null || v === undefined) return '—'
  return `${(Number(v) * 100).toFixed(1)}%`
}

async function refresh() {
  const [a, p, o] = await Promise.all([
    paperTradeApi.account(),
    paperTradeApi.positions(),
    paperTradeApi.orders(),
  ])
  account.value = a
  positions.value = p ?? []
  orders.value = o ?? []
}

async function loadStrategy() {
  const s = await paperTradeApi.getStrategy()
  strategyText.value = s?.content ?? ''
}

async function loadDecisions() {
  decisions.value = (await paperTradeApi.decisions(40)) ?? []
}

async function loadNotes() {
  notes.value = (await paperTradeApi.researchNotes(40)) ?? []
}

async function onResearch() {
  researchRunning.value = true
  researchHint.value = '已提交搜集…'
  try {
    const sub = await paperTradeApi.researchRun()
    researchHint.value = `搜集 ${sub.runId.slice(0, 8)}… 运行中`
    startResearchPoll(sub.runId)
  } catch {
    researchHint.value = '提交失败'
    researchRunning.value = false
  }
}

function startResearchPoll(runId: string) {
  if (researchTimer) {
    clearInterval(researchTimer)
  }
  let tries = 0
  researchTimer = setInterval(async () => {
    tries += 1
    try {
      const run = await paperTradeApi.researchRunDetail(runId)
      researchHint.value = `搜集状态：${run.status}`
      if (run.status === 'SUCCEEDED' || run.status === 'FAILED') {
        if (researchTimer) {
          clearInterval(researchTimer)
          researchTimer = null
        }
        researchRunning.value = false
        if (run.status === 'FAILED') {
          ElMessage.error(run.errorMessage || '搜集失败')
        } else {
          ElMessage.success('搜集完成')
        }
        await loadNotes()
      }
    } catch {
      // 忽略单次轮询失败
    }
    if (tries > 90) {
      if (researchTimer) {
        clearInterval(researchTimer)
        researchTimer = null
      }
      researchRunning.value = false
      researchHint.value = '轮询超时，请稍后刷新研究卡片'
    }
  }, 2000)
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
    // 拦截器已提示
  } finally {
    savingStrategy.value = false
  }
}

async function onAiRun() {
  aiRunning.value = true
  lastRunHint.value = '已提交 AI 轮次…'
  try {
    const sub = await paperTradeApi.aiRun()
    lastRunHint.value = `轮次 ${sub.runId.slice(0, 8)}… 任务 ${sub.jobId?.slice(0, 8) ?? ''} 运行中`
    startPoll(sub.runId)
  } catch {
    lastRunHint.value = '提交失败'
    aiRunning.value = false
  }
}

function startPoll(runId: string) {
  stopPoll()
  let tries = 0
  pollTimer = setInterval(async () => {
    tries += 1
    try {
      const run = await paperTradeApi.aiRunDetail(runId)
      lastRunHint.value = `轮次状态：${run.status}`
      if (run.status === 'SUCCEEDED' || run.status === 'FAILED') {
        stopPoll()
        aiRunning.value = false
        if (run.status === 'FAILED') {
          ElMessage.error(run.errorMessage || 'AI 轮次失败')
        } else {
          ElMessage.success('AI 轮次完成')
        }
        await Promise.all([refresh(), loadDecisions()])
      }
    } catch {
      // 忽略单次轮询失败
    }
    if (tries > 90) {
      stopPoll()
      aiRunning.value = false
      lastRunHint.value = '轮询超时，请稍后刷新决策列表'
    }
  }, 2000)
}

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function onConfirm(id: string) {
  try {
    await paperTradeApi.confirmDecision(id)
    ElMessage.success('已确认下单')
    await Promise.all([refresh(), loadDecisions()])
  } catch {
    await loadDecisions()
  }
}

async function onDismiss(id: string) {
  try {
    await paperTradeApi.dismissDecision(id)
    ElMessage.success('已忽略')
    await loadDecisions()
  } catch {
    await loadDecisions()
  }
}

async function onSubmit() {
  if (!form.symbol?.trim()) {
    ElMessage.warning('请输入股票代码')
    return
  }
  submitting.value = true
  try {
    const order = await paperTradeApi.placeOrder({
      symbol: form.symbol.trim(),
      side: form.side,
      qty: form.qty,
    })
    ElMessage.success(`${order.side} ${order.symbol} x${order.qty} @${order.fillPrice}`)
    await refresh()
  } catch {
    await refresh()
  } finally {
    submitting.value = false
  }
}

async function onReset() {
  try {
    await ElMessageBox.confirm('将清空持仓、订单与冷却，现金回到初始值。确定？', '重置账户', {
      type: 'warning',
    })
  } catch {
    return
  }
  resetting.value = true
  try {
    account.value = await paperTradeApi.reset()
    ElMessage.success('已重置')
    await Promise.all([refresh(), loadDecisions()])
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '重置失败')
  } finally {
    resetting.value = false
  }
}

onMounted(() => {
  Promise.all([refresh(), loadStrategy(), loadDecisions(), loadNotes()]).catch((e: unknown) => {
    ElMessage.error(e instanceof Error ? e.message : '加载失败，请确认已执行 schema 增量')
  })
})

onUnmounted(() => {
  stopPoll()
  if (researchTimer) {
    clearInterval(researchTimer)
  }
})
</script>

<style scoped lang="less">
.paper {
  min-height: 100vh;
  background: linear-gradient(160deg, #ecfdf5 0%, #f0fdf4 40%, #fff 100%);
  padding: 24px 40px 48px;
}

.paper-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.paper-main {
  max-width: 1100px;
  margin: 24px auto 0;
}

.paper-title {
  margin: 0 0 8px;
  font-size: 32px;
  font-weight: 800;
  color: #166534;
}

.paper-banner {
  margin: 0 0 24px;
  color: #3f6212;
  font-size: 14px;
}

.paper-card {
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid #dcfce7;
  border-radius: 12px;
  padding: 16px 20px;
  margin-bottom: 16px;

  h2 {
    margin: 0;
    font-size: 16px;
    color: #14532d;
  }
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.cash-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  font-size: 20px;
  margin-bottom: 8px;

  &.muted {
    font-size: 14px;
    color: #64748b;
  }

  .label {
    color: #64748b;
    font-size: 14px;
  }
}

.hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #94a3b8;
}

.summary {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: #3f6212;
}
</style>
