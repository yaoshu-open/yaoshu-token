// auth API Mock 实现：仅在 DEV 模式 + VITE_AUTH_MOCK=true 时启用
// 三重标记规避 F02 假实现：①文件名 mock.ts ②import.meta.env.DEV only ③env toggle
// 后端联调就绪后设 VITE_AUTH_MOCK=false 即切回真实 API，无需改业务代码

import type {
  LoginPayload,
  LoginResponseData,
  RegisterPayload,
  ResetPasswordPayload
} from './types'

// 模拟网络延迟
function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export async function mockLogin(payload: LoginPayload): Promise<LoginResponseData> {
  await delay(400)
  // 测试用户名 demo + 密码 demodemo → 登录成功，下发 mock token
  if (payload.username === 'demo' && payload.password === 'demodemo') {
    return {
      require2fa: false,
      id: 1,
      token: 'mock-token-demo-' + Date.now()
    }
  }
  // 测试用户名 2fa → 触发 2FA 分支
  if (payload.username === '2fa') {
    return { require2fa: true, id: 2 }
  }
  // 其他用户：模拟凭证错误（成功响应内的失败语义由 store 层处理）
  // 注：不通过抛错——抛错会被 ElMessage 拦截，无法精确返回登录失败
  // 改为返回 require2fa=false + 无 token，store 层据此判断登录失败
  return { require2fa: false }
}

export async function mockRegister(
  payload: RegisterPayload
): Promise<void> {
  await delay(500)
  // Mock：用户名已存在则抛错（被 request 拦截器或 store 层捕获）
  if (payload.username === 'existing') {
    throw new Error('用户名已存在')
  }
  // 否则注册成功（无返回数据）
}

export async function mockLogout(): Promise<void> {
  await delay(200)
}

export async function mockSendPasswordResetEmail(email: string): Promise<void> {
  await delay(400)
  if (!email || !email.includes('@')) {
    throw new Error('请输入有效的邮箱地址')
  }
}

export async function mockSendEmailVerification(email: string): Promise<void> {
  await delay(400)
  if (!email || !email.includes('@')) {
    throw new Error('请输入有效的邮箱地址')
  }
}

export async function mockLogin2FA(code: string): Promise<unknown> {
  await delay(400)
  // Mock：验证码 123456 或备用码 ABCD-EFGH 通过
  if (code === '123456' || code.toUpperCase() === 'ABCDEFGH') {
    return {
      id: 2,
      username: '2fa-user',
      role: 1,
      status: 1,
      displayName: '2FA Mock User'
    }
  }
  throw new Error('验证码错误')
}

export async function mockResetPassword(
  payload: ResetPasswordPayload
): Promise<string> {
  await delay(500)
  if (!payload.email || !payload.token) {
    throw new Error('无效的重置链接')
  }
  // Mock：返回后端生成的新密码
  return 'MockNewPwd' + Math.floor(Math.random() * 10000)
}

export async function mockGetOAuthState(): Promise<string> {
  await delay(200)
  return 'mock-oauth-state-' + Date.now()
}

export async function mockWechatLogin(code: string): Promise<unknown> {
  await delay(500)
  if (!code) throw new Error('请输入微信验证码')
  return {
    message: 'login',
    id: 3,
    username: 'wechat-user',
    role: 1,
    status: 1
  }
}

export async function mockOAuthCallback(provider: string): Promise<unknown> {
  await delay(500)
  // Mock OAuth callback：返回 UserInfo 表示登录成功
  return {
    message: 'login',
    id: 4,
    username: `${provider}-user`,
    role: 1,
    status: 1
  }
}
