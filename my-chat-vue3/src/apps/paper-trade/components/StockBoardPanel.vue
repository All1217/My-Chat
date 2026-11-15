<template>
  <div class="board">
    <div class="board-toolbar">
      <div class="title-block">
        <template v-if="quote">
          <strong>{{ quote.name || quote.symbol }}</strong>
          <span class="code">{{ quote.symbol }}</span>
          <span v-if="quote.market" class="mkt">{{ quote.market }}</span>
        </template>
        <span v-else class="empty-title">{{ symbol ? '加载中…' : '选择或输入代码查看行情' }}</span>
      </div>
      <el-radio-group v-model="range" size="small" :disabled="!symbol">
        <el-radio-button v-for="opt in rangeOptions" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <div v-if="quote" class="metrics">
      <div class="m">
        <span class="l">现价</span>
        <strong>{{ fmtNum(quote.lastPrice) }}</strong>
      </div>
      <div class="m">
        <span class="l">涨跌幅</span>
        <strong :class="chgClass">{{ fmtChg(quote.changePct) }}</strong>
      </div>
      <div class="m">
        <span class="l">最高</span>
        <span>{{ fmtNum(quote.high) }}</span>
      </div>
      <div class="m">
        <span class="l">最低</span>
        <span>{{ fmtNum(quote.low) }}</span>
      </div>
      <div class="m">
        <span class="l">成交量</span>
        <span>{{ fmtVol(quote.volume) }}</span>
      </div>
      <div class="m">
        <span class="l">成交额</span>
        <span>{{ fmtAmount(quote.amount) }}</span>
      </div>
      <div class="m">
        <span class="l">换手率</span>
        <span>{{ fmtTurnover(quote.turnoverRate) }}</span>
      </div>
    </div>

    <div v-loading="loading" class="chart-wrap">
      <div v-show="quote && !error" ref="chartEl" class="chart" />
      <el-empty v-if="!symbol" description="暂无标的" :image-size="64" />
      <el-empty v-else-if="error" :description="error" :image-size="64" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { paperTradeApi } from '@/apps/paper-trade/api'
import { usePaperQuoteChart } from '@/apps/paper-trade/composables/usePaperQuoteChart'
import type { PaperQuote, PaperQuoteRange } from '@/apps/paper-trade/types'
import { ApiError } from '@/utils/http'

const props = defineProps<{
  /** 当前查看的代码；空则空态。 */
  symbol?: string | null
}>()

const rangeOptions: { value: PaperQuoteRange; label: string }[] = [
  { value: '1M', label: '1个月' },
  { value: '3M', label: '3个月' },
  { value: '1Y', label: '1年' },
  { value: '3Y', label: '3年' },
  { value: '5Y', label: '5年' },
  { value: 'ALL', label: '成立以来' },
]

const range = ref<PaperQuoteRange>('1M')
const quote = ref<PaperQuote | null>(null)
const loading = ref(false)
const error = ref('')
const chartEl = ref<HTMLElement | null>(null)
const { render, clear } = usePaperQuoteChart(chartEl)

let abort: AbortController | null = null
let reqSeq = 0

const chgClass = computed(() => {
  const v = quote.value?.changePct
  if (v == null || Number.isNaN(Number(v))) return ''
  return Number(v) >= 0 ? 'up' : 'down'
})

/** 拉取行情并画图；可被后续请求取消。 */
async function load() {
  const sym = props.symbol?.trim()
  if (!sym) {
    abort?.abort()
    quote.value = null
    error.value = ''
    clear()
    return
  }
  abort?.abort()
  abort = new AbortController()
  const seq = ++reqSeq
  loading.value = true
  error.value = ''
  try {
    const data = await paperTradeApi.quote(sym, range.value, abort.signal)
    if (seq !== reqSeq) return
    quote.value = data
    await nextTick()
    render(data.history ?? [])
  } catch (e: unknown) {
    if (seq !== reqSeq) return
    if (isAbort(e)) return
    quote.value = null
    clear()
    error.value = e instanceof ApiError ? e.message : e instanceof Error ? e.message : '行情加载失败'
  } finally {
    if (seq === reqSeq) {
      loading.value = false
    }
  }
}

function isAbort(e: unknown) {
  return (
    (typeof e === 'object' &&
      e !== null &&
      'code' in e &&
      (e as { code?: string }).code === 'ERR_CANCELED') ||
    (e instanceof DOMException && e.name === 'AbortError')
  )
}

function fmtNum(v: number | null | undefined) {
  if (v == null || Number.isNaN(Number(v))) return '—'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function fmtChg(v: number | null | undefined) {
  if (v == null || Number.isNaN(Number(v))) return '—'
  const n = Number(v)
  const sign = n > 0 ? '+' : ''
  return `${sign}${n.toFixed(2)}%`
}

function fmtVol(v: number | null | undefined) {
  if (v == null || Number.isNaN(Number(v))) return '—'
  const n = Number(v)
  if (n >= 1e8) return `${(n / 1e8).toFixed(2)}亿手`
  if (n >= 1e4) return `${(n / 1e4).toFixed(2)}万手`
  return `${n}`
}

function fmtAmount(v: number | null | undefined) {
  if (v == null || Number.isNaN(Number(v))) return '—'
  const n = Number(v)
  if (n >= 1e8) return `${(n / 1e8).toFixed(2)}亿`
  if (n >= 1e4) return `${(n / 1e4).toFixed(2)}万`
  return n.toFixed(0)
}

function fmtTurnover(v: number | null | undefined) {
  if (v == null || Number.isNaN(Number(v))) return '—'
  return `${Number(v).toFixed(2)}%`
}

watch(
  () => [props.symbol, range.value] as const,
  () => {
    load()
  },
  { immediate: true },
)
</script>

<style scoped lang="less">
.board {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
}

.board-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.title-block {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;

  strong {
    font-size: 16px;
    color: #14532d;
  }

  .code {
    font-size: 13px;
    color: #64748b;
  }

  .mkt {
    font-size: 11px;
    padding: 0 6px;
    border-radius: 4px;
    background: #ecfdf5;
    color: #15803d;
  }

  .empty-title {
    font-size: 13px;
    color: #94a3b8;
  }
}

.metrics {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 6px;
  margin-bottom: 8px;

  @media (max-width: 900px) {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

.m {
  background: #f8fafc;
  border-radius: 6px;
  padding: 6px 8px;

  .l {
    display: block;
    font-size: 11px;
    color: #94a3b8;
    margin-bottom: 2px;
  }

  strong,
  span {
    font-size: 13px;
    color: #334155;
  }

  .up {
    color: #dc2626;
  }

  .down {
    color: #16a34a;
  }
}

.chart-wrap {
  position: relative;
  min-height: 300px;
}

.chart {
  width: 100%;
  height: 300px;
}
</style>
