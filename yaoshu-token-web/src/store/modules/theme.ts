import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import {
  SIDEBAR_VARIANTS,
  SIDEBAR_COLLAPSIBLES,
  STORAGE_MAX_AGE,
  THEME_MODES,
  THEME_STORAGE_KEY
} from '@/components/layout/constants'

// 主题模式：'system' 由 prefers-color-scheme 解析为 'light'/'dark'
export type ThemeMode = (typeof THEME_MODES)[number]
// 已解析的实际模式（'system' 不会被消费方直接使用）
export type ResolvedThemeMode = 'light' | 'dark'
export type SidebarVariant = (typeof SIDEBAR_VARIANTS)[number]
export type SidebarCollapsible = (typeof SIDEBAR_COLLAPSIBLES)[number]

interface PersistedTheme {
  mode: ThemeMode
  sidebarVariant: SidebarVariant
  sidebarCollapsible: SidebarCollapsible
  sidebarCollapsed: boolean
  compact: boolean
}

const DEFAULT_MODE: ThemeMode = 'system'
const DEFAULT_VARIANT: SidebarVariant = 'inset'
const DEFAULT_COLLAPSIBLE: SidebarCollapsible = 'icon'
const DEFAULT_COLLAPSED = false
const DEFAULT_COMPACT = false
function readPersisted(): Partial<PersistedTheme> | null {
  try {
    const raw = localStorage.getItem(THEME_STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<PersistedTheme>
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    return null
  }
}

function writePersisted(state: PersistedTheme): void {
  try {
    localStorage.setItem(THEME_STORAGE_KEY, JSON.stringify(state))
    // localStorage 无 maxAge 概念，STORAGE_MAX_AGE 仅作文档对齐（cookie 删除时使用）
    void STORAGE_MAX_AGE
  } catch {
    /* 隐私模式/配额满：忽略持久化失败，session 内仍可用 */
  }
}

// 当前系统主题偏好（SSR 安全：window 不存在时回退 light）
function getSystemTheme(): ResolvedThemeMode {
  if (typeof window === 'undefined') return 'light'
  return window.matchMedia('(prefers-color-scheme: dark)').matches
    ? 'dark'
    : 'light'
}

function resolveTheme(mode: ThemeMode): ResolvedThemeMode {
  return mode === 'system' ? getSystemTheme() : mode
}

// 校验持久化值的类型安全（避免脏数据导致渲染异常）
function isValidMode(value: unknown): value is ThemeMode {
  return typeof value === 'string' && (THEME_MODES as readonly string[]).includes(value)
}
function isValidVariant(value: unknown): value is SidebarVariant {
  return typeof value === 'string' && (SIDEBAR_VARIANTS as readonly string[]).includes(value)
}
function isValidCollapsible(value: unknown): value is SidebarCollapsible {
  return (
    typeof value === 'string' &&
    (SIDEBAR_COLLAPSIBLES as readonly string[]).includes(value)
  )
}

export const useThemeStore = defineStore('theme', () => {
  const persisted = readPersisted()
  const mode = ref<ThemeMode>(
    isValidMode(persisted?.mode) ? persisted!.mode : DEFAULT_MODE
  )
  const sidebarVariant = ref<SidebarVariant>(
    isValidVariant(persisted?.sidebarVariant)
      ? persisted!.sidebarVariant
      : DEFAULT_VARIANT
  )
  const sidebarCollapsible = ref<SidebarCollapsible>(
    isValidCollapsible(persisted?.sidebarCollapsible)
      ? persisted!.sidebarCollapsible
      : DEFAULT_COLLAPSIBLE
  )
  const sidebarCollapsed = ref<boolean>(
    typeof persisted?.sidebarCollapsed === 'boolean'
      ? persisted!.sidebarCollapsed
      : DEFAULT_COLLAPSED
  )
  // 移动端抽屉开关：独立于 sidebarCollapsed（小屏 drawer 开/关，不影响桌面折叠态）
  const mobileSidebarOpen = ref<boolean>(false)
  const compact = ref<boolean>(
    typeof persisted?.compact === 'boolean' ? persisted!.compact : DEFAULT_COMPACT
  )

  // getter：解析后的实际模式（消费方不应直接用 mode='system'，应读 resolvedMode）
  const resolvedMode = computed<ResolvedThemeMode>(() => resolveTheme(mode.value))

  // 副作用：根 <html> class 切换（驱动 Element Plus dark + UnoCSS dark: 变体）
  function applyThemeClass(): void {
    if (typeof document === 'undefined') return
    const root = document.documentElement
    const next = resolveTheme(mode.value)
    root.classList.remove('light', 'dark')
    root.classList.add(next)
  }

  // 副作用：紧凑模式 class 切换（驱动全局 .compact SCSS 规则）
  function applyCompactClass(): void {
    if (typeof document === 'undefined') return
    document.documentElement.classList.toggle('compact', compact.value)
  }

  // 副作用：侧边栏变体 data 属性（驱动 SCSS [data-sidebar-variant=...] 规则）
  function applySidebarVariantAttr(): void {
    if (typeof document === 'undefined') return
    document.body.dataset.sidebarVariant = sidebarVariant.value
  }

  // 初始化立即应用一次（避免首屏闪烁）
  applyThemeClass()
  applyCompactClass()
  applySidebarVariantAttr()

  // 监听系统主题变化（mode='system' 时联动）
  if (typeof window !== 'undefined') {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    mediaQuery.addEventListener('change', () => {
      if (mode.value === 'system') applyThemeClass()
    })
  }

  // watch：mode 变化 → 切换 html class
  watch(mode, applyThemeClass)
  // watch：compact 变化 → 切换 html class
  watch(compact, applyCompactClass)
  // watch：sidebarVariant 变化 → 切换 body data 属性
  watch(sidebarVariant, applySidebarVariantAttr)

  // watch：任一 state 变化 → 持久化
  watch(
    [mode, sidebarVariant, sidebarCollapsible, sidebarCollapsed, compact],
    () => {
      writePersisted({
        mode: mode.value,
        sidebarVariant: sidebarVariant.value,
        sidebarCollapsible: sidebarCollapsible.value,
        sidebarCollapsed: sidebarCollapsed.value,
        compact: compact.value
      })
    }
  )

  // actions
  function setMode(next: ThemeMode): void {
    mode.value = next
  }

  function setSidebarVariant(next: SidebarVariant): void {
    sidebarVariant.value = next
  }

  function setSidebarCollapsible(next: SidebarCollapsible): void {
    sidebarCollapsible.value = next
    // 'none' 模式下强制展开（折叠无意义）
    if (next === 'none') sidebarCollapsed.value = false
  }

  function toggleSidebar(): void {
    if (sidebarCollapsible.value === 'none') return
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function toggleMobileSidebar(): void {
    mobileSidebarOpen.value = !mobileSidebarOpen.value
  }

  function setCompact(next: boolean): void {
    compact.value = next
  }

  function reset(): void {
    mode.value = DEFAULT_MODE
    sidebarVariant.value = DEFAULT_VARIANT
    sidebarCollapsible.value = DEFAULT_COLLAPSIBLE
    sidebarCollapsed.value = DEFAULT_COLLAPSED
    compact.value = DEFAULT_COMPACT
  }

  return {
    // state
    mode,
    sidebarVariant,
    sidebarCollapsible,
    sidebarCollapsed,
    mobileSidebarOpen,
    compact,
    // getter
    resolvedMode,
    // action
    setMode,
    setSidebarVariant,
    setSidebarCollapsible,
    toggleSidebar,
    toggleMobileSidebar,
    setCompact,
    reset
  }
})
