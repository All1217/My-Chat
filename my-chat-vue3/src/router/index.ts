import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

// 定义路由配置
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HomeView.vue')  // 懒加载
  },
  {
    path: '/about',
    name: 'about',
    component: () => import('@/views/AboutView.vue')
  },
  {
    path: '/chat',
    name: 'chat',
    component: () => import('@/views/ChatView.vue')
  },
  {
    path: '/ManageTableDemo',
    name: 'ManageTableDemo',
    component: () => import('@/views/ManageTableDemo.vue')
  },
  {
    path: '/lobby',
    name: 'lobby',
    component: () => import('@/views/LobbyView.vue')
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('@/views/settings/SettingsView.vue'),
    redirect: '/settings/model',  // 默认跳转到第一个子页面
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

// 创建路由实例
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),  // HTML5 模式
  routes
})
export default router