import { computed, type ComputedRef } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/store/modules/auth'
import { ROLE } from '@/utils/roles'
import { resolveSidebarView } from '@/components/layout/lib/sidebar-view-registry'
import { ROOT_VIEW_KEY } from '@/components/layout/constants'
import type { NavGroup, ResolvedSidebarView } from '@/components/layout/types'
import { useSidebarConfig } from './useSidebarConfig'
import { useSidebarData } from './useSidebarData'
import { getExtraNavGroups } from '@/plugins/spi/registry'

/**
 * 解析当前路由对应的侧边栏视图。
 *
 * 解锁 M1-D-T4：sidebar-view-registry 已落盘，本 composable 完成路由→视图派生
 *
 * 行为：
 *   1. 若路由 path 匹配注册的嵌套视图（如 /system-settings/*），返回该视图的 navGroups
 *      （注：嵌套视图不走 useSidebarConfig 过滤，路由级守护已保证 admin 权限）
 *   2. 否则返回 root 视图：useSidebarData() + useSidebarConfig() admin×user 过滤 + isAdmin 闸门
 *
 * 返回 ComputedRef 而非快照：locale 切换 → t() 变化 → useSidebarData/useSidebarConfig
 * 联动重算 → resolved 重算，消费方需以 .value 访问保持响应式
 */
export function useSidebarView(): ComputedRef<ResolvedSidebarView> {
  const { t } = useI18n()
  const route = useRoute()
  const authStore = useAuthStore()
  const { userInfo } = storeToRefs(authStore)

  const rootSidebarData = useSidebarData()
  // 响应式 getter：locale 切换 → t() 变化 → rootSidebarData.value.navGroups 变化 → 联动重算
  const { filtered: configFilteredRoot } = useSidebarConfig(
    () => rootSidebarData.value.navGroups
  )

  // 路由 meta.sidebarView 显式指定优先（覆盖路径正则匹配）
  const explicitViewId = route.meta.sidebarView

  // 嵌套视图匹配（computed，路由变化时自动重算）
  const nestedView = computed(() => {
    if (explicitViewId) {
      // 显式指定：仅支持已注册视图（避免硬编码未注册 id）
      // 当前 registry 仅 SYSTEM_SETTINGS_VIEW，未来扩展走 registry 增量
    }
    return resolveSidebarView(route.path)
  })

  // root 视图：admin 分组按 isAdmin 显隐，注入分组按 requiredRole 过滤
  const rootNavGroups = computed<NavGroup[]>(() => {
    const isAdmin =
      typeof userInfo.value?.role === 'number' &&
      userInfo.value.role >= ROLE.ADMIN
    const extraGroups = getExtraNavGroups()
    return configFilteredRoot.value.filter((group) => {
      if (group.id === 'admin') return isAdmin
      // SPI 注入分组的角色门槛检查
      const injection = extraGroups.find((g) => g.id === group.id)
      if (injection?.requiredRole !== undefined) {
        const userRole = userInfo.value?.role
        return typeof userRole === 'number' && userRole >= injection.requiredRole
      }
      return true
    })
  })

  // 最终派生视图
  const resolved = computed<ResolvedSidebarView>(() => {
    const view = nestedView.value
    if (view) {
      return {
        key: view.id,
        view,
        navGroups: view.getNavGroups(t)
      }
    }
    return {
      key: ROOT_VIEW_KEY,
      view: null,
      navGroups: rootNavGroups.value
    }
  })

  return resolved
}
