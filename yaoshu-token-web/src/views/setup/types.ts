// SetupWizard 容器内表单聚合类型（前端表单状态，非 API 契约）
export interface SetupFormData {
  username: string
  password: string
  confirmPassword: string
  usageMode: 'external' | 'self-use'
  demoSiteEnabled: boolean
}
