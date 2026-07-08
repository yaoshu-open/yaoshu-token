import { request } from '@/utils/request'
import type { UptimeGroupResult } from './types'

/** 获取 Uptime Kuma 可用性分组数据（公开端点，无需鉴权） */
export function getUptimeStatus() {
  return request.get<UptimeGroupResult[]>('/api/uptime/status')
}
