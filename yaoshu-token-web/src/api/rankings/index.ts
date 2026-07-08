/**
 * 排行榜 API Service。
 * 后端契约：ai-docs/后端设计/API_Contract/契约_公共与系统.md §二 GET /api/rankings。
 *
 * Mock 闭环：DEV + VITE_RANKINGS_MOCK=true 时切换 mock 实现（动态 import 避免污染
 * production build），与 deployment/channel 模式一致。
 */
import { request } from '@/utils/request'
import { ENDPOINTS, USE_MOCK } from './constants'
import type { RankingPeriod, RankingsSnapshot } from './types'

/** 拉取排行榜快照（按时间窗口） */
export function getRankings(period: RankingPeriod): Promise<RankingsSnapshot> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetRankings(period))
  }
  return request.get<RankingsSnapshot>(ENDPOINTS.RANKINGS, { params: { period } })
}
