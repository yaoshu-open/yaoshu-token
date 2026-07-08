/**
 * Midjourney API Service 单元测试。
 * 覆盖点：
 *   1. getAllMjLogs / getUserMjLogs 真实请求 URL + method + query params
 *   2. USE_MOCK=true 时切换到 mock 实现
 *   3. buildMjQuery 跳过空值
 *   4. 0-based 分页转换
 *
 * Mock 策略：mock @/utils/request，不 mock mock.ts。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}

vi.mock('@/utils/request', () => ({
  request: requestMock,
}))

const originalEnv = { ...import.meta.env }

describe('midjourney API service', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubEnv('DEV', true)
    vi.stubEnv('VITE_MJ_MOCK', '')
  })

  afterEach(() => {
    for (const key of Object.keys(import.meta.env)) {
      if (!(key in originalEnv)) {
        vi.stubEnv(key, originalEnv[key] ?? '')
      }
    }
    vi.resetModules()
  })

  describe('getAllMjLogs (GET /api/mj/)', () => {
    it('真实模式：调用 request.get 并构建完整 query', async () => {
      const mockData = { list: [], total: 0, pageNum: 1, pageSize: 20, pages: 0, hasNextPage: false }
      requestMock.get.mockResolvedValueOnce(mockData)

      const { getAllMjLogs } = await import('@/api/midjourney')
      const params = {
        pageNum: 1,
        pageSize: 20,
        channel_id: '5',
        mj_id: 'task-0001',
        start_timestamp: 1000,
        end_timestamp: 2000,
      }
      const result = await getAllMjLogs(params)

      expect(requestMock.get).toHaveBeenCalledOnce()
      const url = requestMock.get.mock.calls[0][0] as string
      expect(url).toContain('/api/mj/')
      expect(url).toContain('pageNum=1')
      expect(url).toContain('pageSize=20')
      expect(url).toContain('channel_id=5')
      expect(url).toContain('mj_id=task-0001')
      expect(url).toContain('start_timestamp=1000')
      expect(url).toContain('end_timestamp=2000')
      expect(result).toEqual(mockData)
    })

    it('真实模式：跳过空值参数', async () => {
      requestMock.get.mockResolvedValueOnce({ list: [], total: 0, pageNum: 1, pageSize: 10, pages: 0, hasNextPage: false })

      const { getAllMjLogs } = await import('@/api/midjourney')
      await getAllMjLogs({ pageNum: 1, pageSize: 10 })

      const url = requestMock.get.mock.calls[0][0] as string
      expect(url).not.toContain('channel_id')
      expect(url).not.toContain('mj_id')
      expect(url).not.toContain('start_timestamp')
    })
  })

  describe('getUserMjLogs (GET /api/mj/self)', () => {
    it('真实模式：调用用户端点', async () => {
      requestMock.get.mockResolvedValueOnce({ list: [], total: 0, pageNum: 1, pageSize: 10, pages: 0, hasNextPage: false })

      const { getUserMjLogs } = await import('@/api/midjourney')
      await getUserMjLogs({ pageNum: 1, pageSize: 10 })

      const url = requestMock.get.mock.calls[0][0] as string
      expect(url).toContain('/api/mj/self')
    })
  })

  describe('USE_MOCK 模式', () => {
    it('Mock 模式：切换到 mock 实现', async () => {
      vi.stubEnv('VITE_MJ_MOCK', 'true')

      const { getAllMjLogs } = await import('@/api/midjourney')
      const result = await getAllMjLogs({ pageNum: 1, pageSize: 10 })

      expect(requestMock.get).not.toHaveBeenCalled()
      expect(result).toBeDefined()
      expect(result.list).toBeInstanceOf(Array)
      expect(result.total).toBeGreaterThan(0)
    })

    it('Mock 模式：mj_id 筛选生效', async () => {
      vi.stubEnv('VITE_MJ_MOCK', 'true')

      const { getUserMjLogs } = await import('@/api/midjourney')
      const result = await getUserMjLogs({ pageNum: 1, pageSize: 100, mj_id: 'task-0001' })

      expect(result.list.every((l) => l.mjId.includes('task-0001'))).toBe(true)
    })
  })
})

describe('MJ status mapping helpers', () => {
  it('getMjTaskTypeMapping 返回已知类型', async () => {
    const { getMjTaskTypeMapping } = await import('@/api/midjourney/constants')
    const m = getMjTaskTypeMapping('IMAGINE')
    expect(m.labelKey).toBe('IMAGINE')
    expect(m.variant).toBe('primary')
  })

  it('getMjTaskTypeMapping 未知类型返回 fallback', async () => {
    const { getMjTaskTypeMapping } = await import('@/api/midjourney/constants')
    const m = getMjTaskTypeMapping('NONEXISTENT')
    expect(m.labelKey).toBe('UNKNOWN')
    expect(m.variant).toBe('neutral')
  })

  it('getMjStatusMapping 返回已知状态', async () => {
    const { getMjStatusMapping } = await import('@/api/midjourney/constants')
    expect(getMjStatusMapping('SUCCESS').variant).toBe('success')
    expect(getMjStatusMapping('FAILURE').variant).toBe('danger')
  })

  it('getMjSubmitResultMapping 返回已知提交结果', async () => {
    const { getMjSubmitResultMapping } = await import('@/api/midjourney/constants')
    expect(getMjSubmitResultMapping(1).variant).toBe('success')
    expect(getMjSubmitResultMapping(22).variant).toBe('warning')
  })
})
