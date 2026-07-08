/**
 * 自定义 OAuth Provider API Service。
 * 后端契约：CustomOAuthProviderController — /api/custom-oauth-provider/。
 */
import { request } from '@/utils/request'
import type { CustomOAuthProvider, DiscoveryResponse, OAuthPreset } from './types'

const BASE = '/api/custom-oauth-provider'

/** 获取 Provider 列表 */
export function getCustomOAuthProviders(): Promise<CustomOAuthProvider[]> {
  return request.get<CustomOAuthProvider[]>(`${BASE}/`)
}

/** 获取单个 Provider */
export function getCustomOAuthProvider(id: number): Promise<CustomOAuthProvider> {
  return request.get<CustomOAuthProvider>(`${BASE}/${id}`)
}

/** 创建 Provider */
export function createCustomOAuthProvider(
  payload: Omit<CustomOAuthProvider, 'id'>,
): Promise<CustomOAuthProvider> {
  return request.post<CustomOAuthProvider>(`${BASE}/`, payload)
}

/** 更新 Provider */
export function updateCustomOAuthProvider(
  id: number,
  payload: Omit<CustomOAuthProvider, 'id'>,
): Promise<CustomOAuthProvider> {
  return request.put<CustomOAuthProvider>(`${BASE}/${id}`, payload)
}

/** 删除 Provider */
export function deleteCustomOAuthProvider(id: number): Promise<void> {
  return request.delete<void>(`${BASE}/${id}`)
}

/** OIDC Discovery（请求体参数名对齐后端 Controller） */
export function discoverOidcEndpoints(wellKnownUrl: string): Promise<DiscoveryResponse> {
  return request.post<DiscoveryResponse>(`${BASE}/discovery`, { wellKnownUrl })
}

/** OAuth 预设模板 */
export const OAUTH_PRESETS: OAuthPreset[] = [
  {
    key: 'github-enterprise',
    name: 'GitHub Enterprise',
    icon: 'github',
    needsBaseUrl: true,
    authorizationEndpoint: '/login/oauth/authorize',
    tokenEndpoint: '/login/oauth/access_token',
    userInfoEndpoint: '/api/v3/user',
    scopes: 'user:email',
    userIdField: 'id',
    usernameField: 'login',
    displayNameField: 'name',
    emailField: 'email',
  },
  {
    key: 'gitlab',
    name: 'GitLab',
    icon: 'gitlab',
    needsBaseUrl: true,
    authorizationEndpoint: '/oauth/authorize',
    tokenEndpoint: '/oauth/token',
    userInfoEndpoint: '/api/v4/user',
    scopes: 'openid profile email',
    userIdField: 'id',
    usernameField: 'username',
    displayNameField: 'name',
    emailField: 'email',
  },
  {
    key: 'gitea',
    name: 'Gitea',
    icon: 'gitea',
    needsBaseUrl: true,
    authorizationEndpoint: '/login/oauth/authorize',
    tokenEndpoint: '/login/oauth/access_token',
    userInfoEndpoint: '/api/v1/user',
    scopes: 'openid profile email',
    userIdField: 'id',
    usernameField: 'login',
    displayNameField: 'full_name',
    emailField: 'email',
  },
  {
    key: 'nextcloud',
    name: 'Nextcloud',
    icon: 'nextcloud',
    needsBaseUrl: true,
    authorizationEndpoint: '/apps/oauth2/authorize',
    tokenEndpoint: '/apps/oauth2/api/v1/token',
    userInfoEndpoint: '/ocs/v2.php/cloud/user?format=json',
    scopes: 'openid profile email',
    userIdField: 'ocs.data.id',
    usernameField: 'ocs.data.id',
    displayNameField: 'ocs.data.displayname',
    emailField: 'ocs.data.email',
  },
  {
    key: 'keycloak',
    name: 'Keycloak',
    icon: 'keycloak',
    needsBaseUrl: true,
    authorizationEndpoint: '/realms/{realm}/protocol/openid-connect/auth',
    tokenEndpoint: '/realms/{realm}/protocol/openid-connect/token',
    userInfoEndpoint: '/realms/{realm}/protocol/openid-connect/userinfo',
    scopes: 'openid profile email',
    userIdField: 'sub',
    usernameField: 'preferred_username',
    displayNameField: 'name',
    emailField: 'email',
  },
  {
    key: 'authentik',
    name: 'Authentik',
    icon: 'authentik',
    needsBaseUrl: true,
    authorizationEndpoint: '/application/o/authorize/',
    tokenEndpoint: '/application/o/token/',
    userInfoEndpoint: '/application/o/userinfo/',
    scopes: 'openid profile email',
    userIdField: 'sub',
    usernameField: 'preferred_username',
    displayNameField: 'name',
    emailField: 'email',
  },
  {
    key: 'ory',
    name: 'ORY Hydra',
    icon: 'openid',
    needsBaseUrl: true,
    authorizationEndpoint: '/oauth2/auth',
    tokenEndpoint: '/oauth2/token',
    userInfoEndpoint: '/userinfo',
    scopes: 'openid profile email',
    userIdField: 'sub',
    usernameField: 'preferred_username',
    displayNameField: 'name',
    emailField: 'email',
  },
]

/** 认证风格选项 */
export const AUTH_STYLE_OPTIONS = [
  { value: 0, label: 'Auto Detect' },
  { value: 1, label: 'Params (in body)' },
  { value: 2, label: 'Header (Basic Auth)' },
]
