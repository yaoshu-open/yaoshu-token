// /api/status 系统配置契约
// 来源：ai-docs/后端设计/API_Contract/契约_公共与系统.md §1.2 + default features/auth/types.ts SystemStatus
// M1-A：基础系统配置字段
// M1-B：扩展 OAuth/passkey/turnstile/法律条款/注册开关等 auth 相关字段
// M1-B-T2：全量 camelCase 迁移（后端 SystemController.getStatus() 已完成，破坏性变更不保留兼容期，2026-06-28 回执）

export interface SystemStatusData {
  // 基础信息（契约 §1.2）
  version?: string
  startTime?: number
  systemName?: string
  logo?: string
  footerHtml?: string
  serverAddress?: string
  // 邮箱验证
  emailVerification?: boolean
  // OAuth providers（M1-B 扩展，供 OAuthProviders/useOAuthLogin 消费）
  githubOauth?: boolean
  githubClientId?: string
  linuxdoOauth?: boolean
  linuxdoClientId?: string
  telegramOauth?: boolean
  telegramBotName?: string
  wechatLogin?: boolean
  wechatQrCodeUrl?: string
  // 以下 OAuth provider 字段契约 §1.2 未明确列出，保留为可选 camelCase（后端全量迁移）
  discordOauth?: boolean
  discordClientId?: string
  oidcEnabled?: boolean
  oidcAuthorizationEndpoint?: string
  oidcClientId?: string
  // Turnstile 人机校验
  turnstileCheck?: boolean
  turnstileSiteKey?: string
  // 注册 / 登录开关
  registerEnabled?: boolean
  passwordLoginEnabled?: boolean
  passwordRegisterEnabled?: boolean
  oauthRegisterEnabled?: boolean
  passkeyLogin?: boolean
  // 货币/配额展示
  displayInCurrency?: boolean
  quotaPerUnit?: number
  usdExchangeRate?: number
  price?: number
  stripeUnitPrice?: number
  // 以下货币字段契约 §1.2 未明确列出，保留为可选 camelCase
  quotaDisplayType?: 'TOKENS' | 'USD' | 'CUSTOM'
  customCurrencySymbol?: string
  customCurrencyExchangeRate?: number
  displayTokenStatEnabled?: boolean
  // 功能开关
  enableDrawing?: boolean
  enableTask?: boolean
  enableDataExport?: boolean
  dataExportDefaultTime?: string
  defaultCollapseSidebar?: boolean
  enableBatchUpdate?: boolean
  defaultUseAutoGroup?: boolean
  mjNotifyEnabled?: boolean
  demoSiteEnabled?: boolean
  selfUseModeEnabled?: boolean
  // 导航/文档
  docsLink?: string
  headerNavModules?: string | Record<string, unknown>
  sidebarModulesAdmin?: object
  chats?: unknown[]
  // 公告
  announcementsEnabled?: boolean
  announcements?: Announcement[]
  // 法律条款开关
  userAgreementEnabled?: boolean
  privacyPolicyEnabled?: boolean
  // 系统初始化状态
  setup?: boolean
  // 自定义 OAuth providers（管理员后台配置的额外 OAuth 入口）
  customOauthProviders?: CustomOAuthProviderInfo[]
  // Dashboard 扩展面板数据（后端 /api/status 追加，未配置时为空数组）
  apiInfo?: unknown[]
  faq?: unknown[]
  // 其他后端追加字段保留兼容
  [key: string]: unknown
}

// 自定义 OAuth provider（管理员后台配置的额外 OAuth 入口）
// 字段 camelCase 化（M1-B-T2），client_id→clientId、authorization_endpoint→authorizationEndpoint
export interface CustomOAuthProviderInfo {
  id: number
  name: string
  slug: string
  icon: string
  clientId: string
  authorizationEndpoint: string
  scopes: string
}

// /api/status 响应：经拦截器解包 Result.data 后的扁平业务数据（字段为 camelCase）
export type SystemStatus = SystemStatusData

export interface Announcement {
  id?: number | string
  publishDate?: string
  content?: string
  extra?: string
  type?: string
  title?: string
  link?: string
  [key: string]: unknown
}
