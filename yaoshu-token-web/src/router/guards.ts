import type { Router } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/auth'
import { useAuthStore } from '@/store/modules/auth'
import { useSystemConfigStore } from '@/store/modules/system-config'
import { useSetupStatus } from '@/composables/useSetupStatus'
import { ROLE } from '@/utils/roles'
import i18n from '@/plugins/i18n'

// 全局前置守卫：路由级鉴权 + 用户信息懒加载（规约-路由与权限）
// M1-A 升级：token 检查后追加 fetchUserInfo 拉取 + system-config 触发
// SetupWizard：setup 检测优先于 token（未初始化系统无用户）
export function setupRouterGuards(router: Router): void {
  router.beforeEach(async (to, _from, next) => {
    // setup 检测：最早执行（未初始化系统强制引导至 /setup）
    const { setupStatus, fetchSetupStatus } = useSetupStatus()
    await fetchSetupStatus()
    if (setupStatus.value?.status === false && to.path !== '/setup') {
      next({ path: '/setup' })
      return
    }
    if (setupStatus.value?.status === true && to.path === '/setup') {
      next({ path: '/' })
      return
    }

    const token = getToken()
    // 父子路由 requireAuth 继承判断
    const requireAuth = to.matched.some(
      (record) => record.meta.requireAuth === true
    )

    // 公开路由：已登录访问登录页则跳首页
    if (!requireAuth) {
      if (token && to.path === '/sign-in') {
        next({ path: '/' })
        return
      }
      next()
      return
    }

    // 需鉴权但无 token：跳登录页并携带 redirect 回跳
    if (!token) {
      next({ path: '/sign-in', query: { redirect: to.fullPath } })
      return
    }

    // 首次进入受保护路由：拉取用户信息（非阻塞路由，失败由拦截器/store 处理）
    const authStore = useAuthStore()
    if (authStore.userInfo === null && !authStore.userInfoLoading) {
      try {
        await authStore.fetchUserInfo()
      } catch {
        // fetchUserInfo 内部已捕获并写入 lastError，不阻塞路由
        // 401 由 request 拦截器统一处理（清 token + 跳登录），此处不会再捕获 401
      }
    }

    // 角色拦截：消费路由 meta.roles（管理路由已声明 roles: ['admin']）
    // 防御纵深——前端路由层拦截 + 后端 API 403 兜底
    const requiredRoles = to.meta.roles
    if (requiredRoles && requiredRoles.length > 0) {
      const userRole =
        typeof authStore.userInfo?.role === 'number'
          ? authStore.userInfo.role
          : ROLE.COMMON
      const hasAccess = requiredRoles.some((r) => {
        if (r === 'admin') return userRole >= ROLE.ADMIN
        if (r === 'root') return userRole >= ROLE.ROOT
        return false
      })
      if (!hasAccess) {
        ElMessage.warning(i18n.global.t('common.noPermission'))
        next({ path: '/' })
        return
      }
    }

    // 系统配置懒加载：首次访问时触发 /api/status 拉取（M1-A 完整接入 system-config store）
    const systemConfigStore = useSystemConfigStore()
    if (systemConfigStore.rawStatus === null && !systemConfigStore.loading) {
      // 失败不阻塞路由：站点名/Logo 用默认值，sidebar 仍可渲染
      systemConfigStore.fetch().catch(() => {
        /* 错误已由 store.lastError 暴露 + ElMessage 提示 */
      })
    }

    // 页面标题：i18n key 渲染 + 应用名兜底
    const titleKey = to.meta.title
    const appTitle = import.meta.env.VITE_APP_TITLE || 'Yaoshu Token'
    if (titleKey) {
      try {
        document.title = `${i18n.global.t(titleKey)} - ${appTitle}`
      } catch {
        document.title = appTitle
      }
    } else {
      document.title = appTitle
    }

    next()
  })
}
