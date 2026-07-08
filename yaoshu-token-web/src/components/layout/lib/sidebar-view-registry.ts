import type { NavGroup, SidebarView } from '../types'
// drill-in 视图注册表：新增视图只需在此数组追加条目
// system-settings 不使用 drill-in 模式：进入后保留全局侧边栏，页内独立 tab 导航
const SIDEBAR_VIEWS: readonly SidebarView[] = []

// 根据路径解析激活的嵌套视图，无匹配返回 null（消费方 fallback 到 root nav）
export function resolveSidebarView(pathname: string): SidebarView | null {
  return SIDEBAR_VIEWS.find((view) => view.pathPattern.test(pathname)) ?? null
}
export function getNavGroupsForPath(
  pathname: string,
  t: (key: string) => string
): NavGroup[] | null {
  const view = resolveSidebarView(pathname)
  return view ? view.getNavGroups(t) : null
}
