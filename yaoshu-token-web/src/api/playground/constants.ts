/**
 * Playground API 端点常量。
 */
export const API_ENDPOINTS = {
  /** Playground Chat Completions（流式/非流式） */
  CHAT_COMPLETIONS: '/pg/chat/completions',
  /** 当前用户可用模型 */
  USER_MODELS: '/api/user/models',
  /** 当前用户分组及倍率 */
  USER_GROUPS: '/api/user/self/groups'
} as const
