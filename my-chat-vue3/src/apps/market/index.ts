import { TrendCharts } from '@element-plus/icons-vue'
import type { AppManifest } from '@/apps/types'

/** 股市分析功能包清单。 */
export const marketApp: AppManifest = {
  id: 'market',
  title: '股市分析',
  desc: '输入代码查看走势，并由 AI 推演未来路径',
  icon: TrendCharts,
  color: '#437dff',
  routeName: 'app-market',
  routePath: '/app/market',
  component: () => import('./views/MarketView.vue'),
  order: 25,
}
