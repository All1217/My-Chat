<template>
  <div class="market">
    <header class="market-top">
      <div>
        <h1 class="market-title">股市分析</h1>
        <p class="market-sub">历史走势即刻呈现，未来路径由 Agent 异步推演</p>
      </div>
      <el-button @click="goLobby">回到大厅</el-button>
    </header>

    <div class="market-body">
      <div class="market-left">
        <section class="index-pe-card">
          <div class="chart-head">
            <h2>美股指数估值</h2>
          </div>
          <p v-if="loadingIndexPe" class="eval-status index-pe-status">正在检索指数估值…</p>
          <div v-else-if="indexPeRows.length === 0" class="eval-placeholder index-pe-status">暂无指数估值</div>
          <div v-else class="index-pe-grid">
            <div v-for="row in indexPeRows" :key="row.code" class="index-pe-item">
              <div class="stat-label">{{ row.name }}</div>
              <div class="stat-value">{{ formatPe(row.pe) }}</div>
              <div class="index-pe-pct">近五年分位 {{ formatPercentile(row.percentile) }}</div>
              <div class="index-pe-bar">
                <div class="index-pe-bar-fill" :style="{ width: percentileWidth(row.percentile) }" />
              </div>
              <p class="index-pe-comment">{{ row.comment || '—' }}</p>
            </div>
          </div>
          <p class="index-pe-note">根据公开网络资料整理，仅为参考，不构成投资建议。</p>
        </section>

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

        <section class="chart-card watchlist-card">
          <div class="chart-head">
            <h2>自选股抄底提醒</h2>
          </div>
          <div class="watch-add">
            <el-input
              v-model="watchAdd"
              placeholder="输入代码加入自选，最多 8 只"
              clearable
              @keyup.enter="addWatchSymbol"
            />
            <el-button type="primary" :loading="savingWatchlist" @click="addWatchSymbol">加入</el-button>
          </div>
          <div class="watch-tags">
            <el-tag
              v-for="code in watchSymbols"
              :key="code"
              closable
              class="watch-tag"
              @close="removeWatchSymbol(code)"
            >
              {{ code }}
            </el-tag>
            <span v-if="watchSymbols.length === 0" class="eval-placeholder">还没有自选股</span>
          </div>
          <p v-if="loadingAlert" class="eval-status">正在根据日 K 与策略判断加仓机会…</p>
          <div v-else-if="alertPick" class="alert-pick">
            <div class="alert-title">可酌情加仓：{{ alertName || alertPick }}</div>
            <p class="eval-text">{{ alertReason }}</p>
          </div>
          <p v-else class="eval-placeholder">{{ alertReason || '暂无加仓提醒' }}</p>
        </section>
      </div>

      <aside class="market-right">
        <section class="side-card">
          <div class="side-head">
            <h2>我的投资策略</h2>
            <el-button type="primary" :loading="savingStrategy" :disabled="optimizingStrategy" @click="saveStrategy">保存</el-button>
          </div>
          <el-input
            v-model="strategyDraft"
            type="textarea"
            :rows="10"
            maxlength="4000"
            show-word-limit
            placeholder="写下仓位、止损、持有周期等规则。保存后才会请 AI 评价。"
          />
        </section>
        <section class="side-card eval-card">
          <div class="side-head">
            <h2>AI 评价</h2>
          </div>
          <p v-if="savingStrategy" class="eval-status">正在生成评价…</p>
          <p v-else-if="optimizingStrategy" class="eval-status">正在优化策略…</p>
          <p v-else-if="evaluation" class="eval-text">{{ evaluation }}</p>
          <p v-else class="eval-placeholder">保存策略后，AI 会在此给出评价</p>
          <el-button
            v-if="evaluation"
            class="optimize-btn"
            :loading="optimizingStrategy"
            :disabled="savingStrategy"
            @click="optimizeStrategy"
          >
            让AI直接优化策略
          </el-button>
        </section>
        <section class="side-card crash-card">
          <div class="side-head">
            <h2>股灾风险预警</h2>
          </div>
          <p v-if="crashRefreshing" class="eval-status">正在更新本周预警…</p>
          <template v-if="crashWindow || crashTrigger || crashImpact">
            <div class="crash-block">
              <div class="crash-label">时间窗口</div>
              <p class="eval-text">{{ crashWindow || '—' }}</p>
            </div>
            <div class="crash-block">
              <div class="crash-label">触发因素</div>
              <p class="eval-text">{{ crashTrigger || '—' }}</p>
            </div>
            <div class="crash-block">
              <div class="crash-label">可能影响</div>
              <p class="eval-text">{{ crashImpact || '—' }}</p>
            </div>
          </template>
          <p v-else-if="!crashRefreshing" class="eval-placeholder">本周预警生成后将显示在这里</p>
          <p v-if="crashError" class="eval-status crash-error">{{ crashError }}</p>
          <p class="crash-disclaimer">以上为联网检索后的情景分析，不是可兑现的预测，不构成投资建议。</p>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { marketApi } from '@/apps/market/api'
import { useMarketChart } from '@/apps/market/composables/useMarketChart'
import type { KlinePoint, MarketIndexPeItem, MarketQuote } from '@/apps/market/types'
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

const strategyDraft = ref('')
const evaluation = ref('')
const savingStrategy = ref(false)
const optimizingStrategy = ref(false)
const watchAdd = ref('')
const watchSymbols = ref<string[]>([])
const savingWatchlist = ref(false)
const loadingAlert = ref(false)
const alertPick = ref('')
const alertName = ref('')
const alertReason = ref('')
const crashWindow = ref('')
const crashTrigger = ref('')
const crashImpact = ref('')
const crashRefreshing = ref(false)
const crashError = ref('')
const loadingIndexPe = ref(false)
const indexPeRows = ref<MarketIndexPeItem[]>([])

let unsubTerminal: (() => void) | undefined
let pollTimer: ReturnType<typeof setInterval> | undefined
let crashPollTimer: ReturnType<typeof setInterval> | undefined
let pollTicks = 0
let crashPollTicks = 0
const POLL_MS = 1500
const POLL_MAX_TICKS = 40

function stopForecastPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = undefined
  }
}

function stopCrashPoll() {
  if (crashPollTimer) {
    clearInterval(crashPollTimer)
    crashPollTimer = undefined
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

/** 进页只读已保存策略，不触发评价。 */
async function loadStrategy() {
  try {
    const row = await marketApi.getStrategy()
    strategyDraft.value = row.strategyText ?? ''
    evaluation.value = row.evaluation ?? ''
  } catch {
    strategyDraft.value = ''
    evaluation.value = ''
  }
}

/** 保存后才调用 AI 评价一次。 */
async function saveStrategy() {
  savingStrategy.value = true
  try {
    const row = await marketApi.saveStrategy({ strategyText: strategyDraft.value })
    strategyDraft.value = row.strategyText ?? ''
    evaluation.value = row.evaluation ?? ''
  } catch {
    // 失败提示由 http 拦截器弹出
  } finally {
    savingStrategy.value = false
  }
  void loadAlert()
}

/** 用当前已保存策略生成优化稿并覆盖原文。 */
async function optimizeStrategy() {
  if (!evaluation.value || optimizingStrategy.value) {
    return
  }
  optimizingStrategy.value = true
  try {
    const row = await marketApi.optimizeStrategy()
    strategyDraft.value = row.strategyText ?? ''
    evaluation.value = row.evaluation ?? ''
  } catch {
    // 失败提示由 http 拦截器弹出
  } finally {
    optimizingStrategy.value = false
  }
  void loadAlert()
}

/** 读取自选列表。 */
async function loadWatchlist() {
  try {
    const row = await marketApi.getWatchlist()
    watchSymbols.value = row.symbols ?? []
  } catch {
    watchSymbols.value = []
  }
}

/** 进页拉取抄底提醒，可能命中当日缓存。 */
async function loadAlert() {
  loadingAlert.value = true
  try {
    const row = await marketApi.getWatchlistAlert()
    alertPick.value = row.pickSymbol ?? ''
    alertName.value = row.pickName ?? ''
    alertReason.value = row.reason ?? '暂无加仓提醒'
  } catch {
    alertPick.value = ''
    alertName.value = ''
    alertReason.value = '暂无加仓提醒'
  } finally {
    loadingAlert.value = false
  }
}

/** 覆盖保存自选并刷新提醒。 */
async function persistWatchlist(next: string[]) {
  savingWatchlist.value = true
  try {
    const row = await marketApi.saveWatchlist({ symbols: next })
    watchSymbols.value = row.symbols ?? []
    await loadAlert()
  } catch {
    // 失败提示由 http 拦截器弹出
  } finally {
    savingWatchlist.value = false
  }
}

/** 加入一只自选股。 */
async function addWatchSymbol() {
  const code = watchAdd.value.trim()
  if (!code) {
    return
  }
  await persistWatchlist([...watchSymbols.value, code])
  watchAdd.value = ''
}

/** 从自选中移除。 */
async function removeWatchSymbol(code: string) {
  await persistWatchlist(watchSymbols.value.filter((item) => item !== code))
}

/** 把预警接口结果填进右列。 */
function applyCrashRisk(row: {
  window?: string
  trigger?: string
  impact?: string
  status?: string
  errorMessage?: string
}) {
  crashWindow.value = row.window ?? ''
  crashTrigger.value = row.trigger ?? ''
  crashImpact.value = row.impact ?? ''
  crashRefreshing.value = row.status === 'RUNNING'
  crashError.value = row.status === 'FAILED' ? (row.errorMessage || '本周预警更新失败') : ''
}

/** 进页拉取预警；本周任务进行中则轮询。 */
async function loadCrashRisk() {
  try {
    const row = await marketApi.getCrashRisk()
    applyCrashRisk(row)
    if (row.status === 'RUNNING') {
      startCrashPoll()
    } else {
      stopCrashPoll()
    }
  } catch {
    crashRefreshing.value = false
    if (!crashWindow.value && !crashTrigger.value && !crashImpact.value) {
      crashError.value = ''
    }
  }
}

/** 轮询 GET /crash-risk 直到本周成功或失败。 */
function startCrashPoll() {
  if (crashPollTimer) {
    return
  }
  crashPollTicks = 0
  crashPollTimer = setInterval(() => {
    void pollCrashOnce()
  }, POLL_MS)
}

async function pollCrashOnce() {
  crashPollTicks += 1
  try {
    const row = await marketApi.getCrashRisk()
    applyCrashRisk(row)
    if (row.status !== 'RUNNING') {
      stopCrashPoll()
      return
    }
  } catch {
    // 下次再试
  }
  if (crashPollTicks >= POLL_MAX_TICKS) {
    stopCrashPoll()
    crashRefreshing.value = false
    crashError.value = '预警等待超时'
  }
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
  } catch {
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

/** 进页拉取三大指数市盈率；同日命中缓存，跨日同步刷新。 */
async function loadIndexPe() {
  loadingIndexPe.value = true
  try {
    const row = await marketApi.getIndexPe()
    indexPeRows.value = row.indices ?? []
  } catch {
    indexPeRows.value = []
  } finally {
    loadingIndexPe.value = false
  }
}

/** 市盈率保留一位小数。 */
function formatPe(value: number | null | undefined) {
  if (value == null || Number.isNaN(value)) {
    return '—'
  }
  return value.toFixed(1)
}

/** 分位显示为整数百分数。 */
function formatPercentile(value: number | null | undefined) {
  if (value == null || Number.isNaN(value)) {
    return '—'
  }
  return `${Math.round(value)}%`
}

/** 分位进度条宽度。 */
function percentileWidth(value: number | null | undefined) {
  if (value == null || Number.isNaN(value)) {
    return '0%'
  }
  return `${Math.max(0, Math.min(100, value))}%`
}

onMounted(() => {
  void loadStrategy()
  void loadWatchlist().then(() => loadAlert())
  void loadCrashRisk()
  void loadIndexPe()
  unsubTerminal = notifyStore.onJobTerminal((job) => {
    if (job.jobType === 'market_crash_risk') {
      void loadCrashRisk()
      return
    }
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
  stopCrashPoll()
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

.market-body {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 20px;
  align-items: start;
}

.market-left {
  min-width: 0;
}

.market-right {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.side-card {
  background: #fff;
  border-radius: 16px;
  padding: 16px 18px;
  box-shadow: 0 8px 28px rgba(67, 125, 255, 0.08);
}

.side-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;

  h2 {
    margin: 0;
    font-size: 16px;
    color: #1a1a2e;
  }
}

.eval-card {
  min-height: 180px;
}

.eval-text {
  margin: 0;
  color: #444;
  line-height: 1.7;
  font-size: 14px;
  white-space: pre-wrap;
}

.eval-placeholder,
.eval-status {
  margin: 0;
  color: #999;
  font-size: 14px;
  line-height: 1.7;
}

.optimize-btn {
  width: 100%;
  margin-top: 12px;
}

.crash-card {
  min-height: 160px;
}

.crash-block {
  margin-bottom: 10px;
}

.crash-label {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}

.crash-disclaimer {
  margin: 12px 0 0;
  font-size: 12px;
  color: #bbb;
  line-height: 1.6;
}

.crash-error {
  color: #e11d48;
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

.index-pe-card {
  background: #fff;
  border-radius: 16px;
  padding: 12px 16px 10px;
  box-shadow: 0 8px 28px rgba(67, 125, 255, 0.08);
  margin-bottom: 16px;
}

.index-pe-status {
  padding: 8px 8px 4px;
}

.index-pe-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding: 8px 4px 4px;
}

.index-pe-item {
  background: #f8f6ff;
  border-radius: 12px;
  padding: 12px 14px;
}

.index-pe-pct {
  margin-top: 6px;
  font-size: 12px;
  color: #888;
}

.index-pe-bar {
  margin-top: 8px;
  height: 6px;
  border-radius: 999px;
  background: #ece6ff;
  overflow: hidden;
}

.index-pe-bar-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #9d48ff, #437dff);
}

.index-pe-comment {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #444;
}

.index-pe-note {
  margin: 8px 8px 4px;
  font-size: 12px;
  color: #bbb;
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

.watchlist-card {
  margin-top: 16px;
}

.watch-add {
  display: flex;
  gap: 8px;
  margin: 12px 8px 8px;
}

.watch-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 4px 8px 12px;
  min-height: 32px;
  align-items: center;
}

.watch-tag {
  --el-tag-bg-color: #f3edff;
  --el-tag-border-color: #d8c8ff;
  --el-tag-text-color: #5b3cc4;
}

.alert-pick {
  padding: 4px 8px 12px;
}

.alert-title {
  font-weight: 700;
  color: #1a1a2e;
  margin-bottom: 8px;
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
  .market-body {
    grid-template-columns: 1fr;
  }

  .market-right {
    position: static;
  }

  .stats-row {
    grid-template-columns: repeat(3, 1fr);
  }

  .index-pe-grid {
    grid-template-columns: 1fr;
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
