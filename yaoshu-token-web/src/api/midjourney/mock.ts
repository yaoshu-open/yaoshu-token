/**
 * Midjourney 任务日志 Mock 数据（USE_MOCK 闭环）。
 * 联调通过后由真实 API 替代。
 */
import type { GetMjLogsParams, MjLogsListData } from './types'
import type { MidjourneyLog } from './types'
import { MJ_TASK_TYPES, MJ_TASK_STATUS, MJ_SUBMIT_RESULT_CODES } from './constants'

const MOCK_LOGS: MidjourneyLog[] = Array.from({ length: 23 }, (_, i) => {
  const actions = Object.values(MJ_TASK_TYPES)
  const statuses = Object.values(MJ_TASK_STATUS)
  const codes = Object.values(MJ_SUBMIT_RESULT_CODES)
  const action = actions[i % actions.length]
  const status = statuses[i % statuses.length]
  const now = Date.now()
  const submitTime = now - (i + 1) * 60000
  const isSuccess = status === MJ_TASK_STATUS.SUCCESS
  return {
    id: i + 1,
    userId: 1,
    channelId: (i % 5) + 1,
    code: codes[i % codes.length],
    mjId: `task-${String(i + 1).padStart(4, '0')}`,
    action,
    submitTime,
    finishTime: isSuccess ? submitTime + 15000 : undefined,
    startTime: submitTime + 2000,
    failReason: status === MJ_TASK_STATUS.FAILURE ? 'Queue timeout: MJ worker unavailable' : undefined,
    progress: isSuccess ? '100%' : status === MJ_TASK_STATUS.IN_PROGRESS ? `${(i * 7) % 100}%` : '0%',
    prompt: i % 3 === 0 ? 'cyberpunk city skyline at sunset, neon lights, ultra detailed --ar 16:9 --v 6' : 'a serene mountain landscape with cherry blossoms',
    promptEn: i % 3 === 0 ? 'cyberpunk city skyline at sunset, neon lights, ultra detailed --ar 16:9 --v 6' : undefined,
    description: '',
    status,
    imageUrl: isSuccess ? `https://picsum.photos/seed/mj-${i + 1}/400/400` : undefined,
    buttons: '',
    properties: '',
    quota: 200,
    createdAt: submitTime,
  }
})

function paginate<T>(items: T[], pageNum: number, pageSize: number): { data: T[]; total: number } {
  const start = (pageNum - 1) * pageSize
  return { data: items.slice(start, start + pageSize), total: items.length }
}

export function mockGetAllMjLogs(params: GetMjLogsParams = {}): Promise<MjLogsListData> {
  const pageNum = params.pageNum ?? 1
  const pageSize = params.pageSize ?? 10
  let filtered = [...MOCK_LOGS]
  if (params.channel_id) filtered = filtered.filter((l) => String(l.channelId) === params.channel_id)
  if (params.mj_id) filtered = filtered.filter((l) => l.mjId.includes(params.mj_id!))
  const { data, total } = paginate(filtered, pageNum, pageSize)
  return Promise.resolve({ list: data, total, pageNum, pageSize, pages: Math.ceil(total / pageSize), hasNextPage: pageNum * pageSize < total })
}

export function mockGetUserMjLogs(params: GetMjLogsParams = {}): Promise<MjLogsListData> {
  const userLogs = MOCK_LOGS.filter((l) => l.userId === 1)
  const pageNum = params.pageNum ?? 1
  const pageSize = params.pageSize ?? 10
  let filtered = [...userLogs]
  if (params.mj_id) filtered = filtered.filter((l) => l.mjId.includes(params.mj_id!))
  const { data, total } = paginate(filtered, pageNum, pageSize)
  return Promise.resolve({ list: data, total, pageNum, pageSize, pages: Math.ceil(total / pageSize), hasNextPage: pageNum * pageSize < total })
}
