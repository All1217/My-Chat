import { Coin } from '@element-plus/icons-vue'
import type { AppManifest } from '@/apps/types'

/** 模拟炒股功能包清单（虚拟盘连通性 MVP）。 */
export const paperTradeApp: AppManifest = {
  id: 'paper-trade',
  title: '模拟炒股',
  desc: '虚拟账户市价买卖，验证行情与账本连通（非实盘）',
  icon: Coin,
  color: '#16a34a',
  routeName: 'app-paper-trade',
  routePath: '/app/paper-trade',
  component: () => import('./views/PaperTradeView.vue'),
  order: 26,
}
