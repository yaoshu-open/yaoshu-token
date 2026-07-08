import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { SIDEBAR_ICONS } from '@/components/layout/icons'
import type { SidebarData } from '@/components/layout/types'
import { getExtraNavGroups, isFeatureHidden } from '@/plugins/spi/registry'
import { useUserPermissions } from '@/composables/useUserPermissions'
import { ROLE } from '@/utils/roles'

/**
 * 侧边栏根导航数据源。
 *
 * 解锁 M1-D-T3：图标映射决策落定于 components/layout/icons.ts（lucide → i-ep-* + UnoCSS 类名）
 *
 * 国际化：title 直接绑定 t() 返回值（响应式），消费方在 locale 切换时自动重渲染
 *
 * 权限过滤：admin 分组仅 role >= ADMIN 可见；SPI 注入分组按 requiredRole 过滤。
 * 数据源统一过滤后，侧边栏与 CommandMenu 共享同一权限视图，避免点击后路由拦截。
 */
export function useSidebarData() {
  const { t } = useI18n()
  const { hasRole } = useUserPermissions()

  const sidebarData = computed<SidebarData>(() => ({
    navGroups: [
      {
        id: 'chat',
        title: t('layout.sidebar.group.chat'),
        items: [
          {
            title: t('nav.playground'),
            url: '/playground',
            icon: SIDEBAR_ICONS.playground
          },
          // SPI feature flag 控制：商业版可隐藏聊天入口
          ...(isFeatureHidden('chat')
            ? []
            : [
                {
                  title: t('nav.chat'),
                  icon: SIDEBAR_ICONS.chat,
                  url: '/chat2link',
                  type: 'chat-presets' as const
                }
              ])
        ]
      },
      {
        id: 'general',
        title: t('layout.sidebar.group.general'),
        items: [
          {
            title: t('nav.dashboard'),
            url: '/dashboard',
            icon: SIDEBAR_ICONS.dashboard
          },
          {
            title: t('nav.apiKeys'),
            url: '/tokens',
            icon: SIDEBAR_ICONS.apiKeys
          },
          // PD-08-1：调用统计与调用日志相邻（统计→日志 是自然阅读顺序）
          {
            title: t('nav.analytics'),
            url: '/analytics',
            icon: SIDEBAR_ICONS.analytics
          },
          {
            title: t('nav.usageLogs'),
            url: '/usage-logs/common',
            icon: SIDEBAR_ICONS.usageLogs
          }
        ]
      },
      {
        id: 'personal',
        title: t('layout.sidebar.group.personal'),
        items: [
          {
            title: t('nav.wallet'),
            url: '/wallet',
            icon: SIDEBAR_ICONS.wallet
          },
          {
            title: t('nav.profile'),
            url: '/profile',
            icon: SIDEBAR_ICONS.profile
          }
        ]
      },
      // admin 分组：仅管理员可见（role >= ADMIN），普通用户不显示管理菜单避免点击后被路由拦截
      ...(hasRole(ROLE.ADMIN)
        ? [
            {
              id: 'admin',
              title: t('layout.sidebar.group.admin'),
              items: [
                {
                  title: t('nav.channels'),
                  url: '/channels',
                  icon: SIDEBAR_ICONS.channels
                },
                {
                  title: t('nav.models'),
                  url: '/models',
                  icon: SIDEBAR_ICONS.models
                },
                {
                  title: t('nav.deployments'),
                  url: '/deployments',
                  icon: SIDEBAR_ICONS.deployments
                },
                {
                  title: t('nav.users'),
                  url: '/users',
                  icon: SIDEBAR_ICONS.users
                },
                {
                  title: t('nav.redemptionCodes'),
                  url: '/redemption-codes',
                  icon: SIDEBAR_ICONS.redemptionCodes
                },
                {
                  title: t('nav.subscriptions'),
                  url: '/subscriptions',
                  icon: SIDEBAR_ICONS.subscriptions
                },
                {
                  title: t('nav.systemSettings'),
                  url: '/system-settings/site',
                  activeUrls: ['/system-settings'],
                  icon: SIDEBAR_ICONS.systemSettings
                }
              ]
            }
          ]
        : []),
      // SPI 扩展点：定制实现注入的额外分组（如运营管理），按 requiredRole 过滤
      ...getExtraNavGroups().filter((g) =>
        g.requiredRole !== undefined ? hasRole(g.requiredRole) : true
      )
    ]
  }))

  return sidebarData
}
