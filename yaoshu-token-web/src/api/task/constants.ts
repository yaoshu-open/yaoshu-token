/** 任务日志 API 端点常量 + Mock 开关。 */

const DEV = import.meta.env.DEV
const VITE_TASK_MOCK = import.meta.env.VITE_TASK_MOCK === 'true'
export const USE_MOCK = DEV && VITE_TASK_MOCK

export const TASK_ENDPOINTS = {
  ADMIN_LIST: '/api/task/',
  USER_LIST: '/api/task/self',
} as const

export const DEFAULT_PAGE_SIZE = 20
