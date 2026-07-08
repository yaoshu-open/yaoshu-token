// 职责：构建各 provider 的 OAuth 授权 URL（含 redirect_uri/state/scope 标准参数）
// 被消费方：composables/auth/useOAuthLogin.ts

import { request } from '@/utils/request'

// GitHub OAuth URL（固定 scope=user:email）
export function buildGitHubOAuthUrl(clientId: string, state: string): string {
  return `https://github.com/login/oauth/authorize?client_id=${clientId}&state=${state}&scope=user:email`
}

// Discord OAuth URL（redirect_uri 走 /oauth/discord）
export function buildDiscordOAuthUrl(clientId: string, state: string): string {
  const url = new URL('https://discord.com/oauth2/authorize')
  url.searchParams.set('client_id', clientId)
  url.searchParams.set('redirect_uri', `${window.location.origin}/oauth/discord`)
  url.searchParams.set('response_type', 'code')
  url.searchParams.set('scope', 'identify+openid')
  url.searchParams.set('state', state)
  return url.toString()
}

// OIDC OAuth URL（authorization_endpoint 来自后端 status 配置）
export function buildOIDCOAuthUrl(
  authUrl: string,
  clientId: string,
  state: string
): string {
  const url = new URL(authUrl)
  url.searchParams.set('client_id', clientId)
  url.searchParams.set('redirect_uri', `${window.location.origin}/oauth/oidc`)
  url.searchParams.set('response_type', 'code')
  url.searchParams.set('scope', 'openid profile email')
  url.searchParams.set('state', state)
  return url.toString()
}

// LinuxDO OAuth URL（固定授权端点）
export function buildLinuxDOOAuthUrl(clientId: string, state: string): string {
  return `https://connect.linux.do/oauth2/authorize?response_type=code&client_id=${clientId}&state=${state}`
}

// 自定义 OAuth provider URL（用户配置的 OAuth provider）
export function buildCustomOAuthUrl(
  authorizationEndpoint: string,
  clientId: string,
  state: string,
  slug: string,
  scopes?: string
): string {
  const url = new URL(authorizationEndpoint)
  url.searchParams.set('client_id', clientId)
  url.searchParams.set('redirect_uri', `${window.location.origin}/oauth/${slug}`)
  url.searchParams.set('response_type', 'code')
  url.searchParams.set('state', state)
  if (scopes) {
    url.searchParams.set('scope', scopes)
  }
  return url.toString()
}

// 获取 OAuth state token（CSRF 防护）。附带 aff 邀请码（若 localStorage 存在）
// request 拦截器对 /api/* 自动解包 data，故直接返回 string
export async function getOAuthState(): Promise<string> {
  let path = '/api/oauth/state'
  const affCode = localStorage.getItem('aff')
  if (affCode && affCode.length > 0) {
    path += `?aff=${encodeURIComponent(affCode)}`
  }
  return request.get<string>(path)
}
