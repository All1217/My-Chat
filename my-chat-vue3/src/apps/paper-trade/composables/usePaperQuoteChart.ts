import * as echarts from 'echarts'
import { onUnmounted, type Ref } from 'vue'
import type { PaperKlinePoint } from '@/apps/paper-trade/types'

/** 模拟炒股看板：收盘价折线图（独立于股市分析模块）。 */
export function usePaperQuoteChart(elRef: Ref<HTMLElement | null>) {
  let chart: echarts.ECharts | null = null

  /** 绑定或复用 echarts 实例。 */
  function ensure(): echarts.ECharts | null {
    if (!elRef.value) {
      return null
    }
    if (!chart) {
      chart = echarts.init(elRef.value)
    }
    return chart
  }

  /** 用历史收盘价重绘折线。 */
  function render(history: PaperKlinePoint[]) {
    const instance = ensure()
    if (!instance) {
      return
    }
    const points = (history ?? []).filter((p) => p.date && p.close != null)
    const categories = points.map((p) => p.date)
    const closes = points.map((p) => p.close as number)
    instance.setOption(
      {
        backgroundColor: 'transparent',
        tooltip: {
          trigger: 'axis',
          valueFormatter: (v: unknown) => (typeof v === 'number' ? v.toFixed(2) : ''),
        },
        grid: { left: 48, right: 16, top: 24, bottom: 28 },
        xAxis: {
          type: 'category',
          data: categories,
          boundaryGap: false,
          axisLine: { lineStyle: { color: '#d1d5db' } },
          axisLabel: { color: '#94a3b8', hideOverlap: true, fontSize: 11 },
        },
        yAxis: {
          type: 'value',
          scale: true,
          splitLine: { lineStyle: { color: '#f1f5f9' } },
          axisLabel: { color: '#94a3b8', fontSize: 11 },
        },
        series: [
          {
            name: '收盘价',
            type: 'line',
            data: closes,
            showSymbol: false,
            smooth: 0.15,
            lineStyle: { width: 2.2, color: '#16a34a' },
            areaStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(22,163,74,0.22)' },
                { offset: 1, color: 'rgba(22,163,74,0.02)' },
              ]),
            },
          },
        ],
      },
      true,
    )
  }

  /** 清空图。 */
  function clear() {
    chart?.clear()
  }

  function onResize() {
    chart?.resize()
  }

  window.addEventListener('resize', onResize)
  onUnmounted(() => {
    window.removeEventListener('resize', onResize)
    chart?.dispose()
    chart = null
  })

  return { render, clear, ensure }
}
