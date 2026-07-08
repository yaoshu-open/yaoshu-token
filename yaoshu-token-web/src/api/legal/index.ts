/**
 * 法律条款 API。
 * 后端契约：ai-docs/后端设计/API_Contract/契约_公共与系统.md §一。
 *
 * 返回值：后端返回 Result<string>，拦截器解包后业务侧直接消费 string。
 */
import { request } from '@/utils/request'

/** 获取用户协议内容（返回 string） */
export function getLegalUserAgreement(): Promise<string> {
  return request.get<string>('/api/legal/user_agreement')
}

/** 获取隐私政策内容（返回 string） */
export function getLegalPrivacyPolicy(): Promise<string> {
  return request.get<string>('/api/legal/privacy_policy')
}
