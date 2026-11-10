<template>
  <div class="paper">
    <header class="paper-header">
      <el-button @click="goLobby">回到大厅</el-button>
      <el-button type="danger" plain :loading="resetting" @click="onReset">重置账户</el-button>
    </header>

    <main class="paper-main">
      <h1 class="paper-title">模拟炒股</h1>
      <p class="paper-banner">虚拟盘连通性测试 · 不接涨乐/基金实盘 · 成交价取东财最新收盘</p>

      <section class="paper-card">
        <div class="cash-row">
          <span class="label">可用现金</span>
          <strong>{{ formatMoney(account?.cash) }}</strong>
        </div>
        <div class="cash-row muted">
          <span class="label">初始资金</span>
          <span>{{ formatMoney(account?.initialCash) }}</span>
        </div>
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
        <p class="hint">A 股数量须为 100 的整数倍。</p>
      </section>

      <section class="paper-card">
        <h2>持仓</h2>
        <el-table :data="positions" stripe empty-text="暂无持仓" size="small">
          <el-table-column prop="symbol" label="代码" width="100" />
          <el-table-column prop="market" label="市场" width="70" />
          <el-table-column prop="qty" label="数量" width="90" />
          <el-table-column label="成本" width="110">
            <template #default="{ row }">{{ formatMoney(row.avgCost) }}</template>
          </el-table-column>
          <el-table-column label="市值" min-width="120">
            <template #default="{ row }">{{ formatMoney(row.marketValue) }}</template>
          </el-table-column>
        </el-table>
      </section>

      <section class="paper-card">
        <h2>最近订单</h2>
        <el-table :data="orders" stripe empty-text="暂无订单" size="small">
          <el-table-column prop="createdAt" label="时间" width="160" />
          <el-table-column prop="side" label="方向" width="70" />
          <el-table-column prop="symbol" label="代码" width="100" />
          <el-table-column prop="qty" label="数量" width="80" />
          <el-table-column label="成交价" width="110">
            <template #default="{ row }">{{ formatMoney(row.fillPrice) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="rejectReason" label="说明" min-width="160" show-overflow-tooltip />
        </el-table>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { paperTradeApi } from '@/apps/paper-trade/api'
import type { PaperAccount, PaperOrder, PaperPosition } from '@/apps/paper-trade/types'

const router = useRouter()
const account = ref<PaperAccount | null>(null)
const positions = ref<PaperPosition[]>([])
const orders = ref<PaperOrder[]>([])
const submitting = ref(false)
const resetting = ref(false)

const form = reactive({
  symbol: '600519',
  side: 'BUY' as 'BUY' | 'SELL',
  qty: 100,
})

/** 返回大厅。 */
function goLobby() {
  router.push({ name: 'lobby' })
}

/** 金额展示。 */
function formatMoney(v: number | null | undefined) {
  if (v === null || v === undefined || Number.isNaN(Number(v))) return '—'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 })
}

/** 刷新账户、持仓、订单。 */
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

/** 提交市价单。 */
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
    // 错误提示已由 http 拦截器弹出
    await refresh()
  } finally {
    submitting.value = false
  }
}

/** 重置虚拟账户。 */
async function onReset() {
  try {
    await ElMessageBox.confirm('将清空持仓与订单，现金回到初始值。确定？', '重置账户', {
      type: 'warning',
    })
  } catch {
    return
  }
  resetting.value = true
  try {
    account.value = await paperTradeApi.reset()
    ElMessage.success('已重置')
    await refresh()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '重置失败')
  } finally {
    resetting.value = false
  }
}

onMounted(() => {
  refresh().catch((e: unknown) => {
    ElMessage.error(e instanceof Error ? e.message : '加载失败，请确认已执行 schema.sql')
  })
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

.paper-main {
  max-width: 960px;
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
    margin: 0 0 12px;
    font-size: 16px;
    color: #14532d;
  }
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
  margin: 0;
  font-size: 12px;
  color: #94a3b8;
}
</style>
