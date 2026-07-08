import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useStatus } from '@/composables/useStatus'
import { useAuthStore } from '@/store/modules/auth'
import type { NavGroup, NavItem } from '@/components/layout/types'

// 侧边栏模块配置（admin 层）：每个 section 含 enabled 总开关 + 模块子开关
type SidebarSectionConfig = {
  enabled: boolean
  [key: string]: boolean
}

type SidebarModulesAdminConfig = Record<string, SidebarSectionConfig>
type SidebarModulesUserConfig = SidebarModulesAdminConfig | null
const DEFAULT_SIDEBAR_MODULES: SidebarModulesAdminConfig = {
  chat: { enabled: true, playground: true, chat: true },
  console: {
    enabled: true,
    detail: true,
    analytics: true,
    token: true,
    log: true,
    midjourney: true,
    task: true
  },
  personal: { enabled: true, topup: true, personal: true },
  admin: {
    enabled: true,
    channel: true,
    models: true,
    deployment: true,
    redemption: true,
    user: true,
    setting: true,
    subscription: true
  }
}
function mergeWithDefault(
  config: SidebarModulesAdminConfig
): SidebarModulesAdminConfig {
  const merged: SidebarModulesAdminConfig = { ...config }
  Object.entries(DEFAULT_SIDEBAR_MODULES).forEach(([sectionKey, defaultSection]) => {
    const existing = merged[sectionKey]
    if (!existing) {
      merged[sectionKey] = { ...defaultSection }
      return
    }
    merged[sectionKey] = { ...defaultSection, ...existing }
    Object.keys(defaultSection).forEach((moduleKey) => {
      if (merged[sectionKey][moduleKey] === undefined) {
        merged[sectionKey][moduleKey] = defaultSection[moduleKey]
      }
    })
  })
  return merged
}
// 清理死映射 PD-01/PD-04/PD-07：删除 /dashboard/overview|models|users、/usage-logs/drawing、/keys、/models/metadata|deployments
const URL_TO_CONFIG_MAP: Record<string, { section: string; module: string }> = {
  '/playground': { section: 'chat', module: 'playground' },
  '/dashboard': { section: 'console', module: 'detail' },
  '/analytics': { section: 'console', module: 'analytics' },
  '/tokens': { section: 'console', module: 'token' },
  '/usage-logs/common': { section: 'console', module: 'log' },
  '/usage-logs/task': { section: 'console', module: 'task' },
  '/wallet': { section: 'personal', module: 'topup' },
  '/profile': { section: 'personal', module: 'personal' },
  '/channels': { section: 'admin', module: 'channel' },
  '/models': { section: 'admin', module: 'models' },
  '/deployments': { section: 'admin', module: 'deployment' },
  '/users': { section: 'admin', module: 'user' },
  '/redemption-codes': { section: 'admin', module: 'redemption' },
  '/subscriptions': { section: 'admin', module: 'subscription' },
  '/system-settings': { section: 'admin', module: 'setting' },
  '/system-settings/site': { section: 'admin', module: 'setting' }
}

function parseAdminConfig(value: unknown): SidebarModulesAdminConfig {
  if (typeof value !== 'string' || value.trim() === '') {
    return DEFAULT_SIDEBAR_MODULES
  }
  try {
    const parsed = JSON.parse(value) as SidebarModulesAdminConfig
    return mergeWithDefault(parsed)
  } catch {
    // 配置解析失败：兜底默认（不静默放行，控制台报错）
    console.error('[useSidebarConfig] Failed to parse SidebarModulesAdmin')
    return DEFAULT_SIDEBAR_MODULES
  }
}

function parseUserConfig(value: unknown): SidebarModulesUserConfig {
  if (typeof value !== 'string' || value.trim() === '') return null
  try {
    const parsed = JSON.parse(value) as SidebarModulesAdminConfig
    if (!parsed || typeof parsed !== 'object') return null
    return parsed
  } catch {
    return null
  }
}
function isModuleEnabled(
  url: string,
  adminConfig: SidebarModulesAdminConfig,
  userConfig: SidebarModulesUserConfig
): boolean {
  const mapping = URL_TO_CONFIG_MAP[url]
  if (!mapping) return true // 无映射视为默认可见（新增功能/系统设置等）

  const { section, module } = mapping
  const adminSection = adminConfig[section]
  const adminAllowed = Boolean(
    adminSection && adminSection.enabled && adminSection[module] === true
  )
  if (!adminAllowed) return false
  if (!userConfig) return true

  const userSection = userConfig[section]
  if (!userSection) return true
  if (userSection.enabled === false) return false
  return userSection[module] !== false
}
function isNavItemVisible(
  item: NavItem,
  adminConfig: SidebarModulesAdminConfig,
  userConfig: SidebarModulesUserConfig
): boolean {
  // chat-presets 类型：单独过 admin × user 闸门
  if ('type' in item && item.type === 'chat-presets') {
    const adminChat = adminConfig.chat
    const adminAllowed = Boolean(adminChat?.enabled && adminChat.chat === true)
    if (!adminAllowed) return false
    if (!userConfig) return true
    const userChat = userConfig.chat
    if (!userChat) return true
    if (userChat.enabled === false) return false
    return userChat.chat !== false
  }

  // 普通 link：支持 configUrls（多项任一可见即可）
  if ('url' in item && item.url) {
    const configUrls = item.configUrls ?? [item.url]
    return configUrls.some((url) =>
      isModuleEnabled(url as string, adminConfig, userConfig)
    )
  }

  // 折叠组：任一子项可见则父项可见
  if ('items' in item && item.items) {
    return item.items.some((sub) =>
      isModuleEnabled(sub.url as string, adminConfig, userConfig)
    )
  }

  return true
}
function filterNavItems(
  items: NavItem[],
  adminConfig: SidebarModulesAdminConfig,
  userConfig: SidebarModulesUserConfig
): NavItem[] {
  return items
    .map((item) => {
      if ('items' in item && item.items) {
        const filteredSub = item.items.filter((sub) =>
          isModuleEnabled(sub.url as string, adminConfig, userConfig)
        )
        return { ...item, items: filteredSub } as NavItem
      }
      return item
    })
    .filter((item) => isNavItemVisible(item, adminConfig, userConfig))
}

/**
 * 侧边栏分组过滤：admin × user 双层 AND 闸门。
 *
 * 解锁 M1-D-T2：消费 SystemStatusData.SidebarModulesAdmin + UserInfo.sidebarModules，
 * 两个字段已由索引签名兜底，无需后端新增契约。
 *
 * @param navGroupsGetter 响应式 getter（推荐传 `() => ref.value` 或 computed），
 *   避免数组快照丢失响应式（locale/userInfo/status 变化时联动重算）
 */
export function useSidebarConfig(
  navGroupsGetter: () => NavGroup[]
): {
  filtered: import('vue').ComputedRef<NavGroup[]>
} {
  const { status } = useStatus()
  const authStore = useAuthStore()
  const { userInfo } = storeToRefs(authStore)

  const adminConfig = computed(() => {
    const raw =
      (status.value?.sidebarModulesAdmin as unknown)
    return parseAdminConfig(raw)
  })

  const userConfig = computed<SidebarModulesUserConfig>(() => {
    // 后端标记用户不可配置侧边栏时（如 root 账号），跳过 user overlay
    if (userInfo.value?.permissions?.sidebarSettings === false) return null
    return parseUserConfig(userInfo.value?.sidebarModules)
  })

  const filtered = computed<NavGroup[]>(() =>
    navGroupsGetter()
      .map((group) => ({
        ...group,
        items: filterNavItems(group.items, adminConfig.value, userConfig.value)
      }))
      .filter((group) => group.items.length > 0)
  )

  return { filtered }
}
