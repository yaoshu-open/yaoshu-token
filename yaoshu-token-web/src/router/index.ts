import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getRegisteredRoutes } from '@/plugins/spi/registry'

// 路由元信息契约：规约-路由与权限要求路由表与菜单解耦，权限信息走 meta
// M1-A 扩展 sidebarView（drill-in 视图显式绑定）
declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requireAuth?: boolean
    roles?: string[]
    sidebarView?: string
    /** 公开页布局：内容区是否包裹 max-width 容器（默认 true） */
    container?: boolean
    /** 列表页缓存：true 时组件被 keep-alive 包裹，切换不重新挂载（B 端标准：列表页缓存、表单/详情页不缓存） */
    keepAlive?: boolean
  }
}

const routes: RouteRecordRaw[] = [
  // 系统初始化向导（公开，独立全屏布局；未初始化系统由守卫强制重定向至此）
  {
    path: '/setup',
    name: 'Setup',
    component: () => import('@/views/setup/index.vue'),
    meta: { title: 'setup.title' }
  },
  // 公共认证页：用 layouts/auth.vue 作为布局壳，M1-B 扩展 sign-up/forgot-password/reset-password/otp/oauth-callback
  {
    path: '/sign-in',
    component: () => import('@/layouts/auth.vue'),
    meta: { title: 'auth.signIn.title' },
    children: [
      {
        path: '',
        name: 'SignIn',
        component: () => import('@/views/auth/sign-in/index.vue'),
        meta: { title: 'auth.signIn.title' }
      }
    ]
  },
  {
    path: '/sign-up',
    component: () => import('@/layouts/auth.vue'),
    meta: { title: 'auth.signUp.title' },
    children: [
      {
        path: '',
        name: 'SignUp',
        component: () => import('@/views/auth/sign-up/index.vue'),
        meta: { title: 'auth.signUp.title' }
      }
    ]
  },
  {
    path: '/forgot-password',
    component: () => import('@/layouts/auth.vue'),
    meta: { title: 'auth.forgotPassword.title' },
    children: [
      {
        path: '',
        name: 'ForgotPassword',
        component: () => import('@/views/auth/forgot-password/index.vue'),
        meta: { title: 'auth.forgotPassword.title' }
      }
    ]
  },
  {
    path: '/reset-password',
    component: () => import('@/layouts/auth.vue'),
    meta: { title: 'auth.resetPassword.title' },
    children: [
      {
        path: '',
        name: 'ResetPassword',
        component: () => import('@/views/auth/reset-password/index.vue'),
        meta: { title: 'auth.resetPassword.title' }
      }
    ]
  },
  {
    path: '/otp',
    component: () => import('@/layouts/auth.vue'),
    meta: { title: 'auth.otp.title' },
    children: [
      {
        path: '',
        name: 'Otp',
        component: () => import('@/views/auth/otp/index.vue'),
        meta: { title: 'auth.otp.title' }
      }
    ]
  },
  // OAuth provider 回调：共用 auth.vue 布局（无 requireAuth，回调页自身处理鉴权）
  {
    path: '/oauth/:provider',
    component: () => import('@/layouts/auth.vue'),
    meta: { title: 'auth.oauthCallback.title' },
    children: [
      {
        path: '',
        name: 'OAuthCallback',
        component: () => import('@/views/auth/oauth-callback/index.vue'),
        meta: { title: 'auth.oauthCallback.title' }
      }
    ]
  },
  // 公开落地页：public.vue 布局，无 requireAuth，未登录用户可见 Hero
  // 双 / 父路由——vue-router 4 按叶子路径编译 matcher：公开父 '' 子 → / matcher，鉴权父 playground 等子 → /playground 等 matcher，不冲突
  {
    path: '/',
    component: () => import('@/layouts/public.vue'),
    children: [
      {
        path: '',
        name: 'Landing',
        component: () => import('@/views/landing/index.vue'),
        meta: { title: 'landing.title', container: false }
      }
    ]
  },
  // 鉴权态：用 layouts/default.vue 包裹 AuthenticatedLayout
  // 原 '' (Home) 子路由已被公开落地页取代，移除后已登录用户访问 / 同样看到落地页
  {
    path: '/',
    component: () => import('@/layouts/default.vue'),
    meta: { requireAuth: true },
    children: [
      // M2 业务模块路由：Playground（P0 首启动，M3 ai-elements 升级）
      {
        path: 'playground',
        name: 'Playground',
        component: () => import('@/views/playground/ai-playground.vue'),
        meta: { title: 'nav.playground', requireAuth: true, keepAlive: true }
      },
      // M2 业务模块路由：ModelDeployment 部署增强（P1 第一项，T-MD-01/02/03）
      {
        path: 'deployments',
        name: 'Deployments',
        component: () => import('@/views/deployment/index.vue'),
        meta: { title: 'nav.deployments', requireAuth: true, roles: ['admin'], keepAlive: true }
      },
      // M2 业务模块路由：Channel 标签编辑（C1 路由接入占位，主表格模块待迁移）
      {
        path: 'channels',
        name: 'Channels',
        component: () => import('@/views/channel/index.vue'),
        meta: { title: 'nav.channels', requireAuth: true, roles: ['admin'], keepAlive: true }
      },
      // M2 业务模块路由：Model 模型管理（P2 第一批，T-MO-01 紧凑模式）
      {
        path: 'models',
        name: 'Models',
        component: () => import('@/views/model/index.vue'),
        meta: { title: 'nav.models', requireAuth: true, roles: ['admin'], keepAlive: true }
      },
      // M2 业务模块路由：Token 令牌管理（P2 第三批，T-TK-01/02）
      {
        path: 'tokens',
        name: 'Tokens',
        component: () => import('@/views/token/index.vue'),
        meta: { title: 'nav.apiKeys', requireAuth: true, keepAlive: true }
      },
      // M2 业务模块路由：User 用户管理（P2 第四批，T-US-01/02/03）
      {
        path: 'users',
        name: 'Users',
        component: () => import('@/views/user/index.vue'),
        meta: { title: 'nav.users', requireAuth: true, roles: ['admin'], keepAlive: true }
      },
      // M2 补充批次：Midjourney 任务日志（classic 独有模块迁移，T-MJ-01）
      {
        path: 'midjourney',
        name: 'Midjourney',
        component: () => import('@/views/midjourney/index.vue'),
        meta: { title: 'nav.midjourney', requireAuth: true, keepAlive: true }
      },
      // M4 联调补齐：Dashboard 数据看板（侧边栏 /dashboard/overview + 顶栏 /dashboard 均指此页）
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: 'nav.dashboard', requireAuth: true, keepAlive: true }
      },
      // PD-08-1：独立调用统计看板（原 Dashboard 内违规嵌入的 models/users 板块迁移至此）
      {
        path: 'analytics',
        name: 'Analytics',
        component: () => import('@/views/analytics/index.vue'),
        meta: { title: 'nav.analytics', requireAuth: true, keepAlive: true }
      },
      // M4 联调补齐：Profile 个人资料（侧边栏+ProfileDropdown 均指此页）
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: 'nav.profile', requireAuth: true }
      },
      // 调用日志与任务日志（:tab 动态参数切换 section，PD-04 drawing 不独立）
      {
        path: 'usage-logs/drawing',
        redirect: '/midjourney'
      },
      {
        path: 'usage-logs/:tab',
        name: 'UsageLogs',
        component: () => import('@/views/usage-logs/index.vue'),
        meta: { title: 'nav.usageLogs', requireAuth: true, keepAlive: true }
      },
      // M4-L1-3 钱包（L2 扩充余额/充值/账单）
      {
        path: 'wallet',
        name: 'Wallet',
        component: () => import('@/views/wallet/index.vue'),
        meta: { title: 'nav.wallet', requireAuth: true, keepAlive: true }
      },
      // M4-L1-4 兑换码管理（L2 扩充表格增删改查）
      {
        path: 'redemption-codes',
        name: 'RedemptionCodes',
        component: () => import('@/views/redemption/index.vue'),
        meta: { title: 'nav.redemptionCodes', requireAuth: true, roles: ['admin'], keepAlive: true }
      },
      // M4-L1-5 订阅管理（L2 扩充方案表格）
      {
        path: 'subscriptions',
        name: 'Subscriptions',
        component: () => import('@/views/subscription/index.vue'),
        meta: { title: 'nav.subscriptions', requireAuth: true, roles: ['admin'], keepAlive: true }
      },
      // M4-L1-6 系统设置（L2 扩充 7 个子分组 + M1-A-T2）
      {
        path: 'system-settings/:tab?',
        name: 'SystemSettings',
        component: () => import('@/views/system-settings/index.vue'),
        meta: { title: 'nav.systemSettings', requireAuth: true, roles: ['admin'] }
      },
      // Chat 功能页：chat2link 跳转 + chat/:chatId iframe 嵌入
      {
        path: 'chat2link',
        name: 'Chat2Link',
        component: () => import('@/views/chat/chat2link.vue'),
        meta: { title: 'chat.title', requireAuth: true }
      },
      {
        path: 'chat/:chatId',
        name: 'Chat',
        component: () => import('@/views/chat/chat.vue'),
        meta: { title: 'chat.title', requireAuth: true }
      }
    ]
  },
  // 公开页：about 关于（无侧边栏，用 public.vue 布局）
  {
    path: '/about',
    component: () => import('@/layouts/public.vue'),
    children: [
      {
        path: '',
        name: 'About',
        component: () => import('@/views/about/index.vue'),
        meta: { title: 'nav.about', container: true }
      }
    ]
  },
  // 公开页：rankings 排行榜（无侧边栏，用 public.vue 布局，闭环 M1-A-T3）
  {
    path: '/rankings',
    component: () => import('@/layouts/public.vue'),
    children: [
      {
        path: '',
        name: 'Rankings',
        component: () => import('@/views/rankings/index.vue'),
        meta: { title: 'rankings.title', container: false }
      }
    ]
  },
  // 公开页：performance-metrics 独立性能指标页（全平台性能概览 + 单模型详情，与 rankings/pricing 并列）
  {
    path: '/perf-metrics',
    component: () => import('@/layouts/public.vue'),
    children: [
      {
        path: '',
        name: 'PerfMetrics',
        component: () => import('@/views/perf-metrics/index.vue'),
        meta: { title: 'performance.title', container: true }
      }
    ]
  },
  // 公开页：pricing 模型广场（M3-4，对齐 rankings 模式）
  {
    path: '/pricing',
    component: () => import('@/layouts/public.vue'),
    children: [
      {
        path: '',
        name: 'Pricing',
        component: () => import('@/views/pricing/index.vue'),
        meta: { title: 'pricing.title', container: false }
      },
      // T-PRICING-02 独立模型详情页路由（model_name 作为参数）
      {
        path: ':modelId',
        name: 'PricingModelDetails',
        component: () => import('@/views/pricing/model-details.vue'),
        meta: { title: 'pricing.title', container: false }
      }
    ]
  },
  // 公开页：法律条款（M1-B-T4，无 requireAuth，用 public.vue 布局）
  {
    path: '/user-agreement',
    component: () => import('@/layouts/public.vue'),
    children: [
      {
        path: '',
        name: 'UserAgreement',
        component: () => import('@/views/legal/user-agreement.vue'),
        meta: { title: 'legal.userAgreement.title', container: true }
      }
    ]
  },
  {
    path: '/privacy-policy',
    component: () => import('@/layouts/public.vue'),
    children: [
      {
        path: '',
        name: 'PrivacyPolicy',
        component: () => import('@/views/legal/privacy-policy.vue'),
        meta: { title: 'legal.privacyPolicy.title', container: true }
      }
    ]
  },
  // 错误页：403 禁止访问、500 服务器内部错误
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/forbidden.vue'),
    meta: { title: 'error.forbidden.title' }
  },
  {
    path: '/500',
    name: 'ServerError',
    component: () => import('@/views/error/server-error.vue'),
    meta: { title: 'error.serverError.title' }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/not-found/index.vue'),
    meta: { title: 'common.notFound' }
  }
]

// 合并 SPI 注入的路由（定制页面）
// 约定：/ee/* 路由自动包裹鉴权布局（含侧边栏/header），其他路由作为顶级独立路由
const extraRouteEntries = getRegisteredRoutes()
const eeRoutes = extraRouteEntries.filter((r) => r.path.startsWith('/ee/'))
const standaloneRoutes = extraRouteEntries.filter(
  (r) => !r.path.startsWith('/ee/')
)

const allRoutes: RouteRecordRaw[] = [
  ...routes,
  ...standaloneRoutes.map((r) => ({
    path: r.path,
    component: r.component,
    meta: r.meta ?? { requireAuth: true }
  })),
  ...(eeRoutes.length > 0
    ? [
        {
          path: '/ee',
          component: () => import('@/layouts/default.vue'),
          meta: { requireAuth: true },
          children: eeRoutes.map(
            (r): RouteRecordRaw => ({
              path: r.path.replace('/ee/', ''),
              component: r.component,
              meta: r.meta ?? { requireAuth: true }
            })
          )
        } as RouteRecordRaw
      ]
    : [])
]

const router = createRouter({
  history: createWebHistory(),
  routes: allRoutes,
  scrollBehavior: () => ({ top: 0 })
})

export default router
