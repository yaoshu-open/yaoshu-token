/** Uptime Kuma 监控项状态 */
export interface UptimeMonitor {
  name: string
  /** 可用率 0~1 */
  uptime: number
  /** 1=up, 0=down, 2=warning, 3=maintenance */
  status: number
  group?: string
}

/** Uptime 分组结果 */
export interface UptimeGroupResult {
  categoryName: string
  monitors: UptimeMonitor[]
}
