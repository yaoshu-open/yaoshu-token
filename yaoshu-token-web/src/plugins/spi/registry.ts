import { ref } from 'vue'
import type { Component } from 'vue'
import type { RouteMeta } from 'vue-router'
import type { NavGroup, FooterColumn } from '@/components/layout/types'

// ============================================================================
// 组件注册表：覆盖/替换页面组件（后注册覆盖先注册）
// ============================================================================

const componentRegistry = new Map<string, Component>()

export function registerComponent(slot: string, component: Component): void {
  componentRegistry.set(slot, component)
}

export function resolveComponent(slot: string): Component | undefined {
  return componentRegistry.get(slot)
}

// ============================================================================
// 功能开关注册表：隐藏/禁用功能模块
// ============================================================================

const featureFlags = new Map<string, boolean>()

export function registerFeatureFlag(key: string, hidden: boolean): void {
  featureFlags.set(key, hidden)
}

export function isFeatureHidden(key: string): boolean {
  return featureFlags.get(key) ?? false
}

// ============================================================================
// 路由覆盖注册表：新增定制页面
// ============================================================================

interface RouteEntry {
  path: string
  component: Component
  meta?: RouteMeta
}

const routeEntries: RouteEntry[] = []

export function registerRoute(
  path: string,
  component: Component,
  meta?: RouteMeta
): void {
  // 同 path 覆盖
  const idx = routeEntries.findIndex((r) => r.path === path)
  if (idx >= 0) {
    routeEntries[idx] = { path, component, meta }
  } else {
    routeEntries.push({ path, component, meta })
  }
}

export function resolveRoute(path: string): Component | undefined {
  return routeEntries.find((r) => r.path === path)?.component
}

export function getRegisteredRoutes(): RouteEntry[] {
  return routeEntries
}

// ============================================================================
// 配置简化注册表：简化默认配置
// ============================================================================

const configSimplifiers = new Map<string, (config: unknown) => unknown>()

export function registerConfigSimplifier(
  key: string,
  simplifier: (config: unknown) => unknown
): void {
  configSimplifiers.set(key, simplifier)
}

export function simplifyConfig<T>(key: string, defaultConfig: T): T {
  const simplifier = configSimplifiers.get(key)
  return simplifier ? (simplifier(defaultConfig) as T) : defaultConfig
}

// ============================================================================
// 菜单注入注册表：新增侧边栏分组（第五层）
// ============================================================================

interface NavGroupInjection extends NavGroup {
  /** 分组级角色门槛，用户 role 低于此值时隐藏（默认不限制） */
  requiredRole?: number
}

const navGroupInjections: NavGroupInjection[] = []

export function registerNavGroup(group: NavGroupInjection): void {
  navGroupInjections.push(group)
}

export function getExtraNavGroups(): NavGroupInjection[] {
  return navGroupInjections
}

// ============================================================================
// Footer 列注入注册表
// ============================================================================

const footerColumnInjections = ref<FooterColumn[]>([])

export function registerFooterColumns(columns: FooterColumn[]): void {
  footerColumnInjections.value.push(...columns)
}

export function setFooterColumns(columns: FooterColumn[]): void {
  footerColumnInjections.value = columns
}

export function getExtraFooterColumns(): FooterColumn[] {
  return footerColumnInjections.value
}
