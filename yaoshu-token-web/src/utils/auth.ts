// Token 读写统一封装：规约-路由与权限要求统一通过 @/utils/auth 封装，避免多处 key 字符串不一致
const TOKEN_KEY = 'yaoshu_token'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

export function isLoggedIn(): boolean {
  return !!getToken()
}
