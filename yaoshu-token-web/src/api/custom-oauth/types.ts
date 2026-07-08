/** 自定义 OAuth Provider 实体（字段名对齐后端 Java entity 的 Jackson 驼峰序列化） */
export interface CustomOAuthProvider {
  id: number
  name: string
  slug: string
  icon: string
  enabled: boolean
  clientId: string
  clientSecret: string
  authorizationEndpoint: string
  tokenEndpoint: string
  userInfoEndpoint: string
  scopes: string
  userIdField: string
  usernameField: string
  displayNameField: string
  emailField: string
  wellKnown: string
  authStyle: number
  accessPolicy: string
  accessDeniedMessage: string
}

/** OIDC Discovery 响应 */
export interface DiscoveryResponse {
  success: boolean
  message?: string
  data?: {
    wellKnownUrl?: string
    discovery?: {
      authorizationEndpoint?: string
      tokenEndpoint?: string
      userInfoEndpoint?: string
      scopesSupported?: string[]
    }
  }
}

/** OAuth 预设模板 */
export interface OAuthPreset {
  key: string
  name: string
  icon: string
  needsBaseUrl: boolean
  authorizationEndpoint: string
  tokenEndpoint: string
  userInfoEndpoint: string
  scopes: string
  userIdField: string
  usernameField: string
  displayNameField: string
  emailField: string
}
