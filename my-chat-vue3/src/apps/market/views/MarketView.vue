<template>
  <div class="market">
    <header class="market-top">
      <div>
        <h1 class="market-title">股市分析</h1>
        <p class="market-sub">历史走势即刻呈现，未来路径由 Agent 异步推演</p>
      </div>
      <el-button @click="goLobby">回到大厅</el-button>
    </header>

    <section class="search-card">
      <el-input
        v-model="symbol"
        class="symbol-input"
        placeholder="股票代码，如 600519、AAPL、159659、513500 或 NASDAQ:.IXIC"
        clearable
        @keyup.enter="analyze"
      />
      <el-radio-group v-model="range" class="range-group">
        <el-radio-button value="1M">一个月</el-radio-button>
        <el-radio-button value="3M">3个月</el-radio-button>
        <el-radio-button value="6M">半年</el-radio-button>
        <el-radio-button value="1Y">一年</el-radio-button>
        <el-radio-button value="3Y">三年</el-radio-button>
        <el-radio-button value="5Y">五年</el-radio-button>
        <el-radio-button value="10Y">十年</el-radio-button>
        <el-radio-button value="ALL">上市以来</el-radio-button>
      </el-radio-group>
      <el-button type="primary" :loading="loadingQuote" @click="analyze">分析</el-button>
    </section>

    <section v-if="quote" class="stats-row">
      <div class="stat-card name-card">
        <div class="stat-label">{{ quote.market === 'US' ? '美股' : 'A股' }}</div>
        <div class="stat-value">{{ quote.name }}</div>
        <div class="stat-sub">{{ quote.symbol }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">最新价</div>
        <div class="stat-value">{{ formatPrice(quote.lastPrice) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">涨跌幅</div>
        <div class="stat-value" :class="changeClass(quote.changePct)">{{ formatPct(quote.changePct) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">区间最高</div>
        <div class="stat-value">{{ formatPrice(quote.high) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">区间最低</div>
        <div class="stat-value">{{ formatPrice(quote.low) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">成交量</div>
        <div class="stat-value volume">{{ formatVolume(quote.volume) }}</div>
      </div>
    </section>

    <section v-else class="empty-hint">输入代码并点击分析，查看走势与 AI 情景推演。</section>

    <section v-if="quote" class="chart-card">
      <div class="chart-head">
        <h2>走势图</h2>
        <span v-if="predicting" class="chart-status">右半段预测生成中…</span>
        <span v-else-if="forecastError" class="chart-status error">{{ forecastError }}</span>
      </div>
      <div ref="chartEl" class="chart-el" />
    </section>

    <section v-if="quote" class="chart-card forecast-card">
      <div class="chart-head">
        <h2>未来预测趋势</h2>
      </div>
      <div ref="forecastChartEl" class="chart-el forecast-el" />
    </section>

    <p v-if="summary" class="ai-summary">{{ summary }}</p>
    <p class="disclaimer">本页展示的未来走势为模型情景推演，不构成投资建议。</p>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { marketApi } from '@/apps/market/api'
import { useMarketChart } from '@/apps/market/composables/useMarketChart'
import type { KlinePoint, MarketQuote } from '@/apps/market/types'
import { useNotifyStore } from '@/stores/notify'

const router = useRouter()
const notifyStore = useNotifyStore()

const symbol = ref('600519')
const range = ref('3M')
const loadingQuote = ref(false)
const predicting = ref(false)
const quote = ref<MarketQuote | null>(null)
const forecastPoints = ref<KlinePoint[]>([])
const summary = ref('')
const forecastError = ref('')
const currentForecastId = ref('')
let analyzeGen = 0
const chartEl = ref<HTMLElement | null>(null)
const forecastChartEl = ref<HTMLElement | null>(null)
const { render } = useMarketChart(chartEl)
const { render: renderForecast } = useMarketChart(forecastChartEl, 'forecast')

let unsubTerminal: (() => void) | undefined
let pollTimer: ReturnType<typeof setInterval> | undefined
let pollTicks = 0
const POLL_MS = 1500
const POLL_MAX_TICKS = 40

function stopForecastPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = undefined
  }
}

/** SSE 可能没有订阅端；轮询 GET /forecast/{id} 直到终态。 */
function startForecastPoll(id: string, gen: number) {
  stopForecastPoll()
  pollTicks = 0
  const tick = () => {
    void pollForecastOnce(id, gen)
  }
  pollTimer = setInterval(tick, POLL_MS)
  tick()
}

async function pollForecastOnce(id: string, gen: number) {
  if (gen !== analyzeGen || id !== currentForecastId.value) {
    stopForecastPoll()
    return
  }
  pollTicks += 1
  await applyForecast(id)
  if (!predicting.value) {
    stopForecastPoll()
    return
  }
  if (pollTicks >= POLL_MAX_TICKS) {
    stopForecastPoll()
    predicting.value = false
    forecastError.value = '预测等待超时'
  }
}

/** 返回功能大厅。 */
function goLobby() {
  router.push({ name: 'lobby' })
}

/** 先拉行情画左半，再提交异步预测。 */
async function analyze() {
  const code = symbol.value.trim()
  if (!code) {
    return
  }
  const gen = ++analyzeGen
  stopForecastPoll()
  loadingQuote.value = true
  predicting.value = false
  forecastPoints.value = []
  summary.value = ''
  forecastError.value = ''
  currentForecastId.value = ''
  try {
    predicting.value = true
    // #region agent log
    fetch('http://127.0.0.1:7515/ingest/8099cd9c-7c2d-438a-a2f8-a6a8ca6e190c',{method:'POST',headers:{'Content-Type':'application/json','X-Debug-Session-Id':'1a3bec'},body:JSON.stringify({sessionId:'1a3bec',runId:'pre-fix',hypothesisId:'A',location:'MarketView.vue:analyze',message:'submit start',data:{symbol:code,range:range.value},timestamp:Date.now()})}).catch(()=>{})
    // #endregion
    const row = await marketApi.submitForecast({ symbol: code, range: range.value })
    if (gen !== analyzeGen) {
      return
    }
    quote.value = {
      symbol: row.symbol,
      market: row.market,
      name: row.name,
      rangeKey: row.rangeKey,
      lastPrice: row.lastPrice,
      changePct: row.changePct,
      high: row.high,
      low: row.low,
      volume: row.volume,
      history: row.history ?? [],
    }
    currentForecastId.value = row.id
    await nextTick()
    paintCharts(quote.value.history, row.forecast ?? [])
    if (row.status === 'SUCCEEDED' || row.status === 'FAILED') {
      await applyForecast(row.id)
    } else {
      startForecastPoll(row.id, gen)
    }
  } catch (err: unknown) {
    // #region agent log
    const ax = err as { message?: string; response?: { status?: number; data?: { code?: number; message?: string } } }
    fetch('http://127.0.0.1:7515/ingest/8099cd9c-7c2d-438a-a2f8-a6a8ca6e190c',{method:'POST',headers:{'Content-Type':'application/json','X-Debug-Session-Id':'1a3bec'},body:JSON.stringify({sessionId:'1a3bec',runId:'pre-fix',hypothesisId:'E',location:'MarketView.vue:analyze',message:'submit catch',data:{err:String(err),msg:ax?.message,http:ax?.response?.status,code:ax?.response?.data?.code,apiMsg:ax?.response?.data?.message},timestamp:Date.now()})}).catch(()=>{})
    // #endregion
    if (gen !== analyzeGen) {
      return
    }
    quote.value = null
    predicting.value = false
  } finally {
    if (gen === analyzeGen) {
      loadingQuote.value = false
    }
  }
}

/** 任务结束后把右半段画上。 */
async function applyForecast(id: string) {
  try {
    const row = await marketApi.getForecast(id)
    if (row.status === 'SUCCEEDED') {
      forecastPoints.value = row.forecast ?? []
      summary.value = row.summary ?? ''
      forecastError.value = ''
      predicting.value = false
      if (quote.value) {
        paintCharts(quote.value.history, forecastPoints.value)
      }
    } else if (row.status === 'FAILED') {
      predicting.value = false
      forecastError.value = row.errorMessage || '预测失败'
    }
  } catch {
    forecastError.value = '读取预测结果失败'
  }
}

/** 价格保留两位。 */
function formatPrice(value: number | null | undefined) {
  return value == null ? '—' : value.toFixed(2)
}

/** 涨跌幅带正负号。 */
function formatPct(value: number | null | undefined) {
  if (value == null) {
    return '—'
  }
  const sign = value > 0 ? '+' : ''
  return `${sign}${value.toFixed(2)}%`
}

/** 涨红跌绿。 */
function changeClass(value: number | null | undefined) {
  if (value == null) {
    return ''
  }
  return value >= 0 ? 'up' : 'down'
}

/** 成交量缩写。 */
function formatVolume(value: number | null | undefined) {
  if (value == null) {
    return '—'
  }
  if (value >= 1e8) {
    return `${(value / 1e8).toFixed(2)}亿`
  }
  if (value >= 1e4) {
    return `${(value / 1e4).toFixed(2)}万`
  }
  return String(value)
}

/** 上下两图共用同一份历史与预测。 */
function paintCharts(history: KlinePoint[], forecast: KlinePoint[]) {
  render(history, forecast)
  renderForecast(history, forecast)
}

watch([quote, forecastPoints], () => {
  if (quote.value) {
    paintCharts(quote.value.history, forecastPoints.value)
  }
})

onMounted(() => {
  unsubTerminal = notifyStore.onJobTerminal((job) => {
    if (job.jobType !== 'market_forecast') {
      return
    }
    if (currentForecastId.value && job.refId === currentForecastId.value) {
      applyForecast(currentForecastId.value)
    }
  })
})

onUnmounted(() => {
  stopForecastPoll()
  unsubTerminal?.()
})
</script>

<style scoped lang="less">
.market {
  height: 100vh;
  overflow-y: auto;
  background: linear-gradient(135deg, #f8f5ff 0%, #f0f5ff 50%, #fff 100%);
  padding: 28px 48px 40px;
  box-sizing: border-box;
}

.market-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.market-title {
  margin: 0;
  font-size: 32px;
  font-weight: 800;
  background: linear-gradient(135deg, #9d48ff, #437dff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.market-sub {
  margin: 8px 0 0;
  color: #888;
  font-size: 14px;
}

.search-card {
  display: flex;
  gap: 12px;
  align-items: center;
  background: #fff;
  border-radius: 16px;
  padding: 16px 18px;
  box-shadow: 0 8px 28px rgba(67, 125, 255, 0.08);
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.symbol-input {
  width: 280px;
}

.range-group {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
}

.stats-row {
  display: grid;
  grid-template-columns: 1.4fr repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  background: #fff;
  border-radius: 14px;
  padding: 14px 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
}

.stat-label {
  font-size: 12px;
  color: #999;
  margin-bottom: 6px;
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #1a1a2e;
}

.stat-value.volume {
  font-size: 16px;
}

.stat-sub {
  margin-top: 4px;
  color: #aaa;
  font-size: 12px;
}

.up {
  color: #e11d48;
}

.down {
  color: #16a34a;
}

.empty-hint {
  text-align: center;
  color: #999;
  padding: 80px 0;
}

.chart-card {
  background: #fff;
  border-radius: 18px;
  padding: 12px 16px 8px;
  box-shadow: 0 10px 32px rgba(67, 125, 255, 0.08);
}

.forecast-card {
  margin-top: 16px;
}

.chart-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 8px 0;

  h2 {
    margin: 0;
    font-size: 16px;
    color: #1a1a2e;
  }
}

.chart-status {
  font-size: 13px;
  color: #9d48ff;
}

.chart-status.error {
  color: #e11d48;
}

.chart-el {
  width: 100%;
  height: 420px;
}

.forecast-el {
  height: 280px;
}

.ai-summary {
  max-width: 960px;
  margin: 18px auto 0;
  color: #555;
  line-height: 1.7;
  text-align: center;
}

.disclaimer {
  margin: 28px 0 0;
  text-align: center;
  font-size: 12px;
  color: #bbb;
}

@media (max-width: 1100px) {
  .stats-row {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 768px) {
  .market {
    padding: 20px 16px 32px;
  }

  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .symbol-input {
    width: 100%;
  }

  .chart-el {
    height: 300px;
  }

  .forecast-el {
    height: 220px;
  }
}
</style>
