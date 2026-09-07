import { User } from '@element-plus/icons-vue'
import type { AppManifest } from '@/apps/types'

/** 角色扮演功能包：清单 + 独立页面，不往 LobbyView / views 根目录堆文件。 */
export const roleplayApp: AppManifest = {
  id: 'roleplay',
  title: '角色扮演',
  desc: '选择角色，沉浸式情景互动',
  icon: User,
  color: '#9d48ff',
  routeName: 'app-roleplay',
  routePath: '/app/roleplay',
  component: () => import('./views/RoleplayView.vue'),
  order: 20,
}
