import type { Component } from 'vue'
import type { RouteComponent } from 'vue-router'

/** 功能包子路由（可选）。 */
export interface AppRouteChild {
  /** 相对父路径，如 positions。 */
  path: string
  name: string
  component: RouteComponent
}

/** 大厅功能包清单：卡片文案 + 路由，由 registry 显式登记。 */
export interface AppManifest {
  /** 功能唯一 id，新功能用 kebab-case，与 /app/<id> 对齐。 */
  id: string
  title: string
  desc: string
  /** Element Plus 图标组件。 */
  icon: Component
  color: string
  routeName: string
  /** 新功能用 /app/<id>；内置页保持原路径以免书签失效。 */
  routePath: string
  component: RouteComponent
  /** 嵌套子页；有则父路由 redirect 到第一项。 */
  children?: AppRouteChild[]
  /** 默认 true；home 等返回卡也可展示。 */
  showInLobby?: boolean
  /** 默认 true；false 则大厅与路由都不挂。 */
  enabled?: boolean
  /** 越小越靠前，缺省 100。 */
  order?: number
}
