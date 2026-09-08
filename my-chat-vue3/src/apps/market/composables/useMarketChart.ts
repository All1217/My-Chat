import * as echarts from 'echarts'
import { onUnmounted, type Ref } from 'vue'
import type { KlinePoint } from '@/apps/market/types'

type ChartMode = 'full' | 'forecast'

/** 历史实线 + 预测虚线，或仅预测放大图。 */
export function useMarketChart(elRef: Ref<HTMLElement | null>, mode: ChartMode = 'full') {
  let chart: echarts.ECharts | null = null

  /** 确保实例已绑到 DOM。 */
  function ensure(): echarts.ECharts | null {
    if (!elRef.value) {
      return null
    }
    if (!chart) {
      chart = echarts.init(elRef.value)
    }
    return chart
  }

  /** 用历史与预测点重绘。 */
  function render(history: KlinePoint[], forecast: KlinePoint[]) {
    const instance = ensure()
    if (!instance) {
      return
    }
    if (mode === 'forecast') {
      renderForecastOnly(instance, history, forecast)
      return
    }
    renderFull(instance, history, forecast)
  }

  /** 窗口变化时重算尺寸。 */
  function onResize() {
    chart?.resize()
  }

  window.addEventListener('resize', onResize)
  onUnmounted(() => {
    window.removeEventListener('resize', onResize)
    chart?.dispose()
    chart = null
  })

  return { render, ensure }
}

/** 上图：全段历史 + 预测虚线。 */
function renderFull(
  instance: echarts.ECharts,
  history: KlinePoint[],
  forecast: KlinePoint[],
) {
  const hist = history.filter(p => p.date && p.close != null)
  const fut = forecast.filter(p => p.date && p.close != null)
  const categories = [...hist.map(p => p.date), ...fut.map(p => p.date)]
  const histSeries: (number | null)[] = [
    ...hist.map(p => p.close),
    ...fut.map(() => null),
  ]
  const forecastSeries: (number | null)[] = [
    ...hist.map(() => null),
    ...fut.map(p => p.close),
  ]
  if (hist.length && fut.length) {
    const split = hist.length - 1
    forecastSeries[split] = hist[hist.length - 1].close
  }
  const splitDate = hist.length ? hist[hist.length - 1].date : ''
  instance.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v: unknown) => (typeof v === 'number' ? v.toFixed(2) : ''),
    },
    legend: {
      data: ['历史走势', 'AI 预测'],
      top: 8,
      textStyle: { color: '#666' },
    },
    grid: { left: 48, right: 24, top: 48, bottom: 36 },
    xAxis: {
      type: 'category',
      data: categories,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#ddd' } },
      axisLabel: { color: '#999', hideOverlap: true },
    },
    yAxis: {
      type: 'value',
      scale: true,
      splitLine: { lineStyle: { color: '#f0f0f5' } },
      axisLabel: { color: '#999' },
    },
    series: [
      {
        name: '历史走势',
        type: 'line',
        data: histSeries,
        showSymbol: false,
        smooth: 0.15,
        lineStyle: { width: 2.4, color: '#437dff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(67,125,255,0.22)' },
            { offset: 1, color: 'rgba(67,125,255,0.02)' },
          ]),
        },
      },
      {
        name: 'AI 预测',
        type: 'line',
        data: forecastSeries,
        showSymbol: false,
        smooth: 0.15,
        lineStyle: { width: 2.4, type: 'dashed', color: '#9d48ff' },
        markLine: splitDate
          ? {
              symbol: 'none',
              label: { formatter: '今日', color: '#9d48ff' },
              lineStyle: { color: '#9d48ff', type: 'solid', width: 1 },
              data: [{ xAxis: splitDate }],
            }
          : undefined,
      },
    ],
  }, true)
}

/** 下图：只画预测段，Y 轴按预测价格拉开；末根历史接到起点。 */
function renderForecastOnly(
  instance: echarts.ECharts,
  history: KlinePoint[],
  forecast: KlinePoint[],
) {
  const hist = history.filter(p => p.date && p.close != null)
  const fut = forecast.filter(p => p.date && p.close != null)
  const last = hist.length ? hist[hist.length - 1] : null
  const categories = last && fut.length
    ? [last.date, ...fut.map(p => p.date)]
    : fut.map(p => p.date)
  const data = last && fut.length
    ? [last.close, ...fut.map(p => p.close)]
    : fut.map(p => p.close)
  instance.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v: unknown) => (typeof v === 'number' ? v.toFixed(2) : ''),
    },
    legend: {
      data: ['AI 预测'],
      top: 8,
      textStyle: { color: '#666' },
    },
    grid: { left: 48, right: 24, top: 48, bottom: 36 },
    xAxis: {
      type: 'category',
      data: categories,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#ddd' } },
      axisLabel: { color: '#999', hideOverlap: true },
    },
    yAxis: {
      type: 'value',
      scale: true,
      splitLine: { lineStyle: { color: '#f0f0f5' } },
      axisLabel: { color: '#999' },
    },
    series: [
      {
        name: 'AI 预测',
        type: 'line',
        data,
        showSymbol: true,
        symbolSize: 6,
        smooth: 0.15,
        lineStyle: { width: 2.4, type: 'dashed', color: '#9d48ff' },
        itemStyle: { color: '#9d48ff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(157,72,255,0.22)' },
            { offset: 1, color: 'rgba(157,72,255,0.02)' },
          ]),
        },
      },
    ],
  }, true)
}
