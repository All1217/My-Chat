import { Coin } from '@element-plus/icons-vue'
import type { AppManifest } from '@/apps/types'

/** 模拟炒股功能包：持仓 / 历史 / 设置 / 可视化工作流。 */
export const paperTradeApp: AppManifest = {
  id: 'paper-trade',
  title: '模拟炒股',
  desc: '虚拟账户市价买卖，验证行情与账本连通（非实盘）',
  icon: Coin,
  color: '#16a34a',
  routeName: 'app-paper-trade',
  routePath: '/app/paper-trade',
  component: () => import('./views/PaperTradeLayout.vue'),
  order: 26,
  children: [
    {
      path: 'positions',
      name: 'app-paper-trade-positions',
      component: () => import('./views/PositionsView.vue'),
    },
    {
      path: 'history',
      name: 'app-paper-trade-history',
      component: () => import('./views/HistoryView.vue'),
    },
    {
      path: 'settings',
      name: 'app-paper-trade-settings',
      component: () => import('./views/AccountSettingsView.vue'),
    },
    {
      path: 'workflow',
      name: 'app-paper-trade-workflow',
      component: () => import('./views/WorkflowView.vue'),
    },
  ],
}
