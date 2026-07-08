import type { SidebarView } from '../types'
// M1-A 占位：getNavGroups 返回空数组。M2 system-settings feature 模块迁移后通过组合 registry 扩展点注入 7 个子分组
// （Site/Auth/Billing/Models/Security/Content/Operations）。该占位非 F04 路由空壳，是侧边栏状态切换注册机制
export const SYSTEM_SETTINGS_VIEW: SidebarView = {
  id: 'system-settings',
  pathPattern: /^\/system-settings(\/|$)/,
  parent: {
    to: '/dashboard',
    label: 'layout.sidebar.backToDashboard'
  },
  // M2 system-settings feature 迁移时填充：
  //   - Site & Branding (Settings icon)
  //   - Authentication (Shield icon)
  //   - Billing & Payment (CreditCard icon)
  //   - Models & Routing (Box icon)
  //   - Security & Limits (ShieldAlert icon)
  //   - Console Content (Layout icon)
  //   - Operations (Wrench icon)
  // 现状：root 视图已覆盖 System Settings 入口；进入该路由后侧边栏切到此视图但暂时显示空 nav groups
  getNavGroups: () => []
}
