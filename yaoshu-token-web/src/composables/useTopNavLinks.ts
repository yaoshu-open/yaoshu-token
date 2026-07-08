import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/store/modules/auth'
import { useStatus } from './useStatus'
import { isFeatureHidden } from '@/plugins/spi/registry'
import {
  parseHeaderNavModulesFromStatus,
  type NavModuleConfig,
  type NavModuleObjectConfig
} from '@/utils/nav-modules'

export interface TopNavLink {
  title: string
  href: string
  disabled?: boolean
  requiresAuth?: boolean
  external?: boolean
  // 激活态匹配别名：当前路由命中任一路径时视为 active（如控制台子页面不共享 /dashboard 前缀时用此字段）
  activeUrls?: string[]
}

function isModuleEnabled(cfg: NavModuleConfig | undefined): boolean {
  // 未配置（undefined）视为开启；显式 false 或 { enabled: false } 才隐藏
  if (cfg === undefined) return true
  if (typeof cfg === 'boolean') return cfg
  return cfg.enabled === true
}

function moduleRequireAuth(cfg: NavModuleConfig | undefined): boolean {
  if (!cfg || typeof cfg === 'boolean') return false
  return Boolean((cfg as NavModuleObjectConfig).requireAuth)
}

/**
 * 后端动态导航链接：解析 /api/status 返回的 header_nav_modules，
 * 配合 docs_link 外链字段渲染顶部导航。
 */
export function useTopNavLinks() {
  const { t } = useI18n()
  const { status } = useStatus()
  const authStore = useAuthStore()
  const { isLoggedIn } = storeToRefs(authStore)

  const links = computed<TopNavLink[]>(() => {
    const modules = parseHeaderNavModulesFromStatus(
      status.value as unknown as Record<string, unknown> | null
    )
    const list: TopNavLink[] = []
    const isAuthed = isLoggedIn.value

    // Home：未配置或显式 != false 时显示
    if (isModuleEnabled(modules?.home)) {
      list.push({ title: t('nav.home'), href: '/' })
    }
    // Console：activeUrls 覆盖控制台全部子页面，避免子页面切换时导航栏选中态丢失
    if (isModuleEnabled(modules?.console)) {
      list.push({
        title: t('nav.console'),
        href: '/dashboard',
        activeUrls: [
          '/dashboard',
          '/tokens',
          '/usage-logs',
          '/wallet',
          '/profile',
          '/playground',
          '/chat2link',
          '/midjourney',
          '/channels',
          '/models',
          '/deployments',
          '/users',
          '/redemption-codes',
          '/subscriptions',
          '/system-settings',
          '/analytics'
        ]
      })
    }
    // Pricing：isModuleEnabled 对 undefined 返回 true（默认开启），去除冗余 && modules?.pricing 防 API 未返回时丢失链接
    if (isModuleEnabled(modules?.pricing)) {
      const requiresAuth = moduleRequireAuth(modules?.pricing) && !isAuthed
      list.push({ title: t('nav.pricing'), href: '/pricing', requiresAuth })
    }
    // Rankings：同上，默认开启
    if (isModuleEnabled(modules?.rankings)) {
      const requiresAuth = moduleRequireAuth(modules?.rankings) && !isAuthed
      list.push({ title: t('nav.rankings'), href: '/rankings', requiresAuth })
    }
    // Docs：仅当配置了有效外链时显示（未配置 docsLink 则隐藏，无内部 /docs 路由避免死链）
    if (isModuleEnabled(modules?.docs)) {
      const docsLink = status.value?.docsLink
      if (docsLink && /^https?:\/\//.test(docsLink)) {
        list.push({
          title: t('nav.docs'),
          href: docsLink,
          external: true
        })
      }
    }
    // About
    if (isModuleEnabled(modules?.about) && !isFeatureHidden('nav-about')) {
      list.push({ title: t('nav.about'), href: '/about' })
    }

    return list
  })

  return links
}
