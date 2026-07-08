// /api/setup 系统初始化契约
// 来源：ai-docs/后端设计/API_Contract/契约_公共与系统.md §1.1

// GET /api/setup 响应（经拦截器解包 Result.data）
export interface SetupStatus {
  status: boolean
  rootInit: boolean
  databaseType: string
}

// POST /api/setup 请求体（字段 camelCase）
export interface SetupRequest {
  username: string
  password: string
  confirmPassword: string
  selfUseModeEnabled: boolean
  demoSiteEnabled: boolean
}
