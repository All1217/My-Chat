import { ChatDotRound, HomeFilled, Setting, UploadFilled } from '@element-plus/icons-vue'
import { roleplayApp } from '@/apps/roleplay'
import type { AppManifest } from '@/apps/types'

/** 内置骨架页：继续指向 views/，避免大搬家。 */
const builtinApps: AppManifest[] = [
  {
    id: 'chat',
    title: '即刻聊天',
    desc: '与 AI 自由对话，获取即时回答',
    icon: ChatDotRound,
    color: '#437dff',
    routeName: 'chat',
    routePath: '/chat',
    component: () => import('@/views/ChatView.vue'),
    order: 10,
  },
  {
    id: 'settings',
    title: '设置',
    desc: '个性化配置你的 AI 助手',
    icon: Setting,
    color: '#ff484e',
    routeName: 'settings',
    routePath: '/settings',
    component: () => import('@/views/settings/SettingsView.vue'),
    order: 30,
  },
  {
    id: 'store',
    title: '知识库管理',
    desc: '个性化配置你的 AI 助手',
    icon: UploadFilled,
    color: '#9d48ff',
    routeName: 'store',
    routePath: '/store',
    component: () => import('@/views/knowledgeStore/KnowledgeStore.vue'),
    order: 40,
  },
  {
    id: 'home',
    title: '回到首页',
    desc: '返回 My Chat 主界面',
    icon: HomeFilled,
    color: '#437dff',
    routeName: 'home',
    routePath: '/',
    component: () => import('@/views/HomeView.vue'),
    order: 50,
  },
]

/** 显式登记表：新功能在此 import 并入数组，不要改 LobbyView。 */
const registeredApps: AppManifest[] = [...builtinApps, roleplayApp]

/** 比较功能包展示顺序，order 缺省为 100。 */
function byOrder(a: AppManifest, b: AppManifest): number {
  return (a.order ?? 100) - (b.order ?? 100)
}

/** 功能是否启用（缺省启用）。 */
export function isAppEnabled(app: AppManifest): boolean {
  return app.enabled !== false
}

/** 是否在大厅展示（缺省展示）。 */
export function isLobbyApp(app: AppManifest): boolean {
  return app.showInLobby !== false
}

/** 已启用的功能包，按 order 排序。 */
export const apps: AppManifest[] = registeredApps.filter(isAppEnabled).sort(byOrder)

/** 大厅卡片数据源。 */
export const lobbyApps: AppManifest[] = apps.filter(isLobbyApp)
