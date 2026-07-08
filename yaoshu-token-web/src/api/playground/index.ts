/**
 * Playground API Service。
 *
 * 注意：流式 chat completion 走 utils/sse-stream.ts 直接 fetch（不经 axios 拦截器），
 * 本文件只导出非流式 + 模型/分组查询（用 @/utils/request 走 axios /api/* 解包）。
 */
import { request } from '@/utils/request'
import { API_ENDPOINTS } from './constants'
import type {
  ChatCompletionRequest,
  ChatCompletionResponse,
  ModelOption,
  GroupOption
} from './types'

/**
 * 发送非流式 Chat Completion 请求。
 * 流式场景走 utils/sse-stream.ts。
 */
export async function sendChatCompletion(
  payload: ChatCompletionRequest
): Promise<ChatCompletionResponse> {
  return request.post<ChatCompletionResponse>(API_ENDPOINTS.CHAT_COMPLETIONS, payload)
}

/**
 * 获取当前用户可用模型列表。
 * 后端返回 OpenAI 兼容格式 {object: "list", data: [{id, object: "model", ...}]}，
 * request.ts 解包 Result 信封后得到 {object: "list", data: [...]}。
 */
export async function getUserModels(): Promise<ModelOption[]> {
  const data = await request.get<unknown>(API_ENDPOINTS.USER_MODELS)
  // OpenAI 兼容格式：{object: "list", data: [{id, ...}]}
  if (data && typeof data === 'object' && 'data' in data) {
    const list = (data as { data: unknown }).data
    if (Array.isArray(list)) {
      return list
        .filter((item): item is { id?: string; max_context?: number | null } | string => {
          if (typeof item === 'string') return !!item
          return typeof item === 'object' && item !== null && !!item.id
        })
        .map((item): ModelOption => {
          if (typeof item === 'string') return { label: item, value: item }
          const id = item.id as string
          const maxContext = typeof item.max_context === 'number' ? item.max_context : undefined
          return { label: id, value: id, maxContext }
        })
    }
  }
  // 兼容 string[] 直返格式
  if (Array.isArray(data)) {
    return (data as string[]).map((model) => ({ label: model, value: model }))
  }
  return []
}

/**
 * 获取当前用户分组及倍率。
 * 后端响应 {success, message, data: {[group]: {desc, ratio}}}，request.ts 已解包为 data。
 */
export async function getUserGroups(): Promise<GroupOption[]> {
  const data = await request.get<unknown>(API_ENDPOINTS.USER_GROUPS)
  if (!data || typeof data !== 'object') return []
  const groupData = data as Record<string, { desc: string; ratio: number }>
  return Object.entries(groupData).map(([group, info]) => ({
    label: group,
    value: group,
    ratio: info.ratio,
    desc: info.desc
  }))
}
