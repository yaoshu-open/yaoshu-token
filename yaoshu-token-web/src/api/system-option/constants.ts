/** 系统配置 API 端点常量 + Mock 开关。 */

const DEV = import.meta.env.DEV
const VITE_OPTION_MOCK = import.meta.env.VITE_OPTION_MOCK === 'true'
export const USE_MOCK = DEV && VITE_OPTION_MOCK

export const OPTION_ENDPOINTS = {
  LIST: '/api/option/',
  UPDATE: '/api/option/',
  PAYMENT_COMPLIANCE: '/api/option/payment_compliance',
  RESET_MODEL_RATIO: '/api/option/rest_model_ratio',
  DELETE_LOGS: '/api/log/',
  UPSTREAM_CHANNELS: '/api/ratio_sync/channels',
  UPSTREAM_FETCH: '/api/ratio_sync/fetch',
  ENABLED_MODELS: '/api/channel/models_enabled',
  CHANNEL_AFFINITY_STATS: '/api/channel_affinity/stats',
} as const
