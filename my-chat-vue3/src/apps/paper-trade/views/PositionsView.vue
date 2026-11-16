<template>
  <div class="pos">
    <div class="pos-head">
      <div>
        <h1>持仓状况</h1>
        <p class="banner">虚拟盘 · 东财收盘价撮合 · 不接实盘</p>
      </div>
      <div class="actions">
        <el-button size="small" @click="refreshAll">刷新</el-button>
      </div>
    </div>

    <section class="metrics">
      <div class="metric">
        <span class="label">可用现金</span>
        <strong>{{ formatMoney(account?.cash) }}</strong>
      </div>
      <div class="metric">
        <span class="label">账户权益</span>
        <strong>{{ formatMoney(account?.equity) }}</strong>
      </div>
      <div class="metric muted">
        <span class="label">高点 / 回撤</span>
        <span>{{ formatMoney(account?.peakEquity) }} / {{ formatPct(account?.drawdownPct) }}</span>
      </div>
      <div class="metric muted">
        <span class="label">连亏 / 暂停</span>
        <span>{{ account?.lossStreak ?? 0 }} / {{ account?.buyPauseUntil || '无' }}</span>
      </div>
      <div class="metric muted">
        <span class="label">回撤收紧</span>
        <span>{{ account?.drawdownRestricted ? '是' : '否' }}</span>
      </div>
      <div class="metric muted">
        <span class="label">初始资金</span>
        <span>{{ formatMoney(account?.initialCash) }}</span>
      </div>
    </section>
    <p v-if="account?.latestSummary" class="summary">{{ account.latestSummary }}</p>

    <section class="panel board-panel">
      <div class="panel-head">
        <h2>持仓看板</h2>
        <div class="probe">
          <el-input
            v-model="probeSymbol"
            placeholder="测试代码，如 600519"
            size="small"
            style="width: 150px"
            clearable
            @keyup.enter="onProbe"
          />
          <el-button size="small" type="primary" @click="onProbe">查询</el-button>
        </div>
      </div>
      <p class="hint board-hint">点击持仓代码查看走势；也可用上方输入框测连通性（默认 1 个月，异步加载）。</p>
      <StockBoardPanel :symbol="boardSymbol" />
    </section>

    <div class="grid">
      <section class="panel">
        <h2>市价下单</h2>
        <el-form :inline="true" class="order-form" @submit.prevent>
          <el-form-item label="代码">
            <el-input v-model="form.symbol" placeholder="600519" style="width: 120px" />
          </el-form-item>
          <el-form-item label="方向">
            <el-select v-model="form.side" style="width: 90px">
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
        <p class="hint">A 股数量须为 100 的整数倍；卖出受 T+1 约束。</p>
      </section>

      <section class="panel grow">
        <h2>持仓（{{ positions.length }}）</h2>
        <el-table :data="positions" stripe empty-text="暂无持仓" size="small" max-height="280">
          <el-table-column prop="symbol" label="代码" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="openBoard(row.symbol)">{{ row.symbol }}</el-button>
            </template>
          </el-table-column>
          <el-table-column prop="market" label="市场" width="60" />
          <el-table-column prop="qty" label="数量" width="70" />
          <el-table-column prop="availableQty" label="可卖" width="70" />
          <el-table-column label="成本" width="100">
            <template #default="{ row }">{{ formatMoney(row.avgCost) }}</template>
          </el-table-column>
          <el-table-column label="市值" min-width="100">
            <template #default="{ row }">{{ formatMoney(row.marketValue) }}</template>
          </el-table-column>
          <el-table-column label="止盈/止损" min-width="110">
            <template #default="{ row }">
              {{ formatPct(row.takeProfitPct) }} / {{ formatPct(row.stopLossPct) }}
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>

    <section class="panel">
      <div class="panel-head">
        <h2>待确认 / 近期决策</h2>
        <el-button size="small" link @click="loadDecisions">刷新</el-button>
      </div>
      <el-table :data="decisions" stripe empty-text="暂无决策" size="small" max-height="260">
        <el-table-column prop="createdAt" label="时间" width="150" />
        <el-table-column prop="action" label="动作" width="70" />
        <el-table-column prop="symbol" label="代码" width="90" />
        <el-table-column prop="qty" label="数量" width="70" />
        <el-table-column label="置信度" width="80">
          <template #default="{ row }">
            {{ row.confidence == null ? '—' : Number(row.confidence).toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column prop="execStatus" label="状态" width="100" />
        <el-table-column prop="rationale" label="理由" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <template
              v-if="row.execStatus === 'PENDING' && (row.action === 'BUY' || row.action === 'SELL')"
            >
              <el-button link type="primary" @click="onConfirm(row.id)">确认</el-button>
              <el-button link type="info" @click="onDismiss(row.id)">忽略</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel">
      <h2>最近订单</h2>
      <el-table :data="orders" stripe empty-text="暂无订单" size="small" max-height="240">
        <el-table-column prop="createdAt" label="时间" width="150" />
        <el-table-column prop="source" label="来源" width="70" />
        <el-table-column prop="side" label="方向" width="70" />
        <el-table-column prop="symbol" label="代码" width="90" />
        <el-table-column prop="qty" label="数量" width="70" />
        <el-table-column label="成交价" width="100">
          <template #default="{ row }">{{ formatMoney(row.fillPrice) }}</template>
        </el-table-column>
        <el-table-column label="费用" width="80">
          <template #default="{ row }">{{ formatMoney(row.fee) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column prop="rejectReason" label="说明" min-width="120" show-overflow-tooltip />
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import StockBoardPanel from '@/apps/paper-trade/components/StockBoardPanel.vue'
import { paperTradeApi } from '@/apps/paper-trade/api'
import type {
  PaperAccount,
  PaperAiDecision,
  PaperOrder,
  PaperPosition,
} from '@/apps/paper-trade/types'
import { formatMoney, formatPct } from '@/apps/paper-trade/utils/format'

const account = ref<PaperAccount | null>(null)
const positions = ref<PaperPosition[]>([])
const orders = ref<PaperOrder[]>([])
const decisions = ref<PaperAiDecision[]>([])
const submitting = ref(false)
const boardSymbol = ref('')
const probeSymbol = ref('600519')
let boardAutoPicked = false

const form = reactive({
  symbol: '600519',
  side: 'BUY' as 'BUY' | 'SELL',
  qty: 100,
})

/** 打开看板标的。 */
function openBoard(symbol: string) {
  if (!symbol?.trim()) return
  boardSymbol.value = symbol.trim()
  boardAutoPicked = true
}

/** 测试框查询。 */
function onProbe() {
  const s = probeSymbol.value?.trim()
  if (!s) {
    ElMessage.warning('请输入股票代码')
    return
  }
  openBoard(s)
}

/** 刷新账户 / 持仓 / 订单。 */
async function refresh() {
  const [a, p, o] = await Promise.all([
    paperTradeApi.account(),
    paperTradeApi.positions(),
    paperTradeApi.orders(),
  ])
  account.value = a
  positions.value = p ?? []
  orders.value = o ?? []
  // 首次有持仓时自动打开第一只，不打断用户已选手动标的
  if (!boardAutoPicked && !boardSymbol.value && positions.value.length) {
    boardSymbol.value = positions.value[0].symbol
  }
}

watch(positions, (list) => {
  if (!boardAutoPicked && !boardSymbol.value && list.length) {
    boardSymbol.value = list[0].symbol
  }
})

async function loadDecisions() {
  decisions.value = (await paperTradeApi.decisions(40)) ?? []
}

async function refreshAll() {
  await Promise.all([refresh(), loadDecisions()])
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

onMounted(() => {
  refreshAll().catch((e: unknown) => {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  })
})

</script>

<style scoped lang="less">
.pos {
  max-width: 1200px;
}

.pos-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 8px;

  h1 {
    margin: 0;
    font-size: 22px;
    font-weight: 800;
    color: #166534;
  }

  .banner {
    margin: 2px 0 0;
    font-size: 12px;
    color: #65a30d;
  }

  .actions {
    display: flex;
    gap: 6px;
    flex-shrink: 0;
  }
}

.metrics {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 8px;
}

.metric {
  background: #fff;
  border: 1px solid #dcfce7;
  border-radius: 8px;
  padding: 8px 10px;

  .label {
    display: block;
    font-size: 11px;
    color: #64748b;
    margin-bottom: 2px;
  }

  strong {
    font-size: 16px;
    color: #14532d;
  }

  &.muted span:last-child {
    font-size: 13px;
    color: #334155;
  }
}

.summary {
  margin: 0 0 10px;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.45;
  color: #3f6212;
  background: #f0fdf4;
  border-radius: 6px;
}

.grid {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 10px;
  margin-bottom: 10px;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
}

.panel {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 10px;

  h2 {
    margin: 0 0 8px;
    font-size: 14px;
    font-weight: 700;
    color: #14532d;
  }

  &.grow {
    margin-bottom: 0;
  }
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
  gap: 8px;

  h2 {
    margin: 0;
  }
}

.board-panel {
  .probe {
    display: flex;
    gap: 6px;
    align-items: center;
  }

  .board-hint {
    margin: 0 0 8px;
  }
}

.order-form {
  :deep(.el-form-item) {
    margin-bottom: 8px;
    margin-right: 8px;
  }
}

.hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: #94a3b8;
}
</style>
