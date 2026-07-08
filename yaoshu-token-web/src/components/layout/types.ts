import type { Component } from 'vue'
interface BaseNavItem {
  title: string
  badge?: string
  // Vue3 图标：Component 引用或 i-ep-xxx 字符串类名（与 M1-C EmptyState 一致）
  icon?: Component | string
  activeUrls?: string[]
  configUrls?: string[]
}

// 单链接项（叶子节点）
export interface NavLink extends BaseNavItem {
  url: string
  items?: never
  type?: never
}

// 折叠组项（带子项）
export interface NavCollapsible extends BaseNavItem {
  items: (BaseNavItem & { url: string })[]
  url?: never
  type?: never
}

// 动态 chat-presets 项：点击跳转至 url（通常为 /chat2link 触发预设重定向）
export interface NavChatPresets extends BaseNavItem {
  type: 'chat-presets'
  url?: string
  items?: never
}

export type NavItem = NavCollapsible | NavLink | NavChatPresets

export interface NavGroup {
  id?: string
  title: string
  items: NavItem[]
}

export interface SidebarData {
  navGroups: NavGroup[]
}

// 顶部导航链接（与 composables/useTopNavLinks.ts TopNavLink 解耦，避免循环依赖）
export interface TopNavLink {
  title: string
  href: string
  isActive?: boolean
  disabled?: boolean
  requiresAuth?: boolean
  external?: boolean
  // 激活态匹配别名：当前路由命中任一路径时视为 active
  activeUrls?: string[]
}

// drill-in 视图回退描述符
export interface SidebarViewParent {
  to: string
  // i18n key（如 'layout.sidebar.backToDashboard'），消费方 t() 渲染
  label: string
}

// 注册的嵌套侧边栏视图（Vercel/Cloudflare drill-in 模式）
export interface SidebarView {
  id: string
  pathPattern: RegExp
  parent: SidebarViewParent
  // 接收 t 函数，返回该视图的 nav groups（避免在 registry 内直接 useI18n 造成 store/composable 边界混乱）
  getNavGroups: (t: (key: string) => string) => NavGroup[]
}

// useSidebarView() 返回值
export interface ResolvedSidebarView {
  // 动画 key（root 视图用 '__root' 哨兵，嵌套视图用 view.id）
  key: string
  view: SidebarView | null
  navGroups: NavGroup[]
}

// 业务页脚列定义（PageFooter Portal 注入用）
export interface FooterColumn {
  title: string
  links: { label: string; href: string; external?: boolean }[]
}
