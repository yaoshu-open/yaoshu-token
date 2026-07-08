import type { NavItem, NavCollapsible } from '../types'
export function normalizeHref(href: string): string {
  const withoutQuery = href.split('?')[0]
  return withoutQuery.length > 1
    ? withoutQuery.replace(/\/+$/, '')
    : withoutQuery
}
// 规则：1) activeUrls 显式匹配 2) 折叠组：任一子项匹配 3) 普通 link：url 匹配
export function checkIsActive(
  href: string,
  item: NavItem,
  mainNav = false
): boolean {
  const hrefWithoutQuery = href.split('?')[0]

  // 显式 activeUrls 别名匹配
  if (item.activeUrls?.some((url) => url === hrefWithoutQuery)) {
    return true
  }

  // 折叠组：检查子项
  if ('items' in item && item.items) {
    const collapsible = item as NavCollapsible
    if (
      collapsible.items.some((sub) => {
        if (!sub?.url) return false
        if (href === sub.url) return true
        const subWithoutQuery = sub.url.split('?')[0]
        const subHasQuery = sub.url.includes('?')
        if (subWithoutQuery === hrefWithoutQuery) {
          if (!subHasQuery) return true
          if (subHasQuery && href === sub.url) return true
        }
        return false
      })
    ) {
      return true
    }
  }

  // 普通 link：url 匹配
  if (!item.url) return false
  if (href === item.url) return true

  const itemWithoutQuery = item.url.split('?')[0]
  const itemHasQuery = item.url.includes('?')
  if (hrefWithoutQuery === itemWithoutQuery) {
    if (!itemHasQuery) return true
    if (itemHasQuery && href === item.url) return true
  }

  // 主导航：一级路径匹配（如 /usage-logs/* 匹配 /usage-logs/common）
  if (mainNav && href.split('/')[1] && item.url) {
    const hrefFirst = href.split('/')[1]
    const itemFirst = item.url.split('/')[1]
    return hrefFirst === itemFirst
  }

  return false
}
