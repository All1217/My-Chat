import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { apps } from '@/apps/registry'

/** 骨架路由：首页、关于、大厅、带壳的设置。整页应用由 registerAppRoutes 挂上。 */
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
  },
  {
    path: '/about',
    name: 'about',
    component: () => import('@/views/AboutView.vue'),
  },
  {
    path: '/lobby',
    name: 'lobby',
    component: () => import('@/views/LobbyView.vue'),
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('@/views/settings/SettingsView.vue'),
    redirect: '/settings/model',
    children: [
      {
        path: 'model',
        name: 'settings-model',
        component: () => import('@/views/settings/components/ModelManagement.vue'),
      },
      {
        path: 'workspace',
        name: 'settings-workspace',
        component: () => import('@/views/settings/components/WorkspaceManagement.vue'),
      },
      {
        path: 'prompt',
        name: 'settings-prompt',
        component: () => import('@/views/settings/components/PromptManagement.vue'),
      },
      {
        path: 'role',
        name: 'settings-role',
        component: () => import('@/views/settings/components/RoleManagement.vue'),
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

/** 把已启用且尚未登记的功能包挂到路由上。 */
function registerAppRoutes() {
  for (const app of apps) {
    if (router.hasRoute(app.routeName)) {
      continue
    }
    router.addRoute({
      path: app.routePath,
      name: app.routeName,
      component: app.component,
    })
  }
}

registerAppRoutes()

export default router
