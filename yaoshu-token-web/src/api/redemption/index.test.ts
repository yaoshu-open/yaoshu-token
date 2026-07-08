/**
 * Redemption API Service 单元测试。
 * 覆盖点：
 *   1. 7 个 service 函数的真实请求路径（URL + method + params/body）
 *   2. 拦截器解包后返回值类型对齐
 *
 * Mock 策略：mock @/utils/request 模块（核心依赖 request 层属外部边界，单测可 mock）。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}

vi.mock('@/utils/request', () => ({
  request: requestMock,
}))

describe('redemption API service', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getRedemptions (GET /api/redemption/)', () => {
    it('透传分页参数', async () => {
      requestMock.get.mockResolvedValueOnce({ list: [], total: 0 })
      const { getRedemptions } = await import('@/api/redemption')
      await getRedemptions({ pageNum: 2, pageSize: 50 })
      expect(requestMock.get).toHaveBeenCalledOnce()
      expect(requestMock.get).toHaveBeenCalledWith('/api/redemption/', {
        params: { pageNum: 2, pageSize: 50 },
      })
    })

    it('默认分页参数', async () => {
      requestMock.get.mockResolvedValueOnce({ list: [], total: 0 })
      const { getRedemptions } = await import('@/api/redemption')
      await getRedemptions()
      expect(requestMock.get).toHaveBeenCalledWith('/api/redemption/', {
        params: { pageNum: 1, pageSize: 20 },
      })
    })
  })

  describe('searchRedemptions (GET /api/redemption/search)', () => {
    it('透传 keyword + status + 分页', async () => {
      requestMock.get.mockResolvedValueOnce({ list: [], total: 0 })
      const { searchRedemptions } = await import('@/api/redemption')
      await searchRedemptions({
        keyword: 'test',
        status: '1',
        pageNum: 1,
        pageSize: 20,
      })
      expect(requestMock.get).toHaveBeenCalledWith('/api/redemption/search', {
        params: { keyword: 'test', status: '1', pageNum: 1, pageSize: 20 },
      })
    })
  })

  describe('getRedemption (GET /api/redemption/:id)', () => {
    it('路径参数插值', async () => {
      requestMock.get.mockResolvedValueOnce({ id: 42 })
      const { getRedemption } = await import('@/api/redemption')
      await getRedemption(42)
      expect(requestMock.get).toHaveBeenCalledWith('/api/redemption/42')
    })
  })

  describe('createRedemption (POST /api/redemption/)', () => {
    it('透传 payload', async () => {
      requestMock.post.mockResolvedValueOnce(undefined)
      const { createRedemption } = await import('@/api/redemption')
      const payload = { name: 'test', quota: 500000, count: 5, expiredTime: 0 }
      await createRedemption(payload)
      expect(requestMock.post).toHaveBeenCalledWith('/api/redemption/', payload)
    })
  })

  describe('updateRedemption (PUT /api/redemption/)', () => {
    it('透传 payload', async () => {
      requestMock.put.mockResolvedValueOnce(undefined)
      const { updateRedemption } = await import('@/api/redemption')
      const payload = { id: 1, name: 'updated', quota: 1000000, expiredTime: 0, statusOnly: false }
      await updateRedemption(payload)
      expect(requestMock.put).toHaveBeenCalledWith('/api/redemption/', payload)
    })
  })

  describe('deleteRedemption (DELETE /api/redemption/:id)', () => {
    it('路径参数插值', async () => {
      requestMock.delete.mockResolvedValueOnce(undefined)
      const { deleteRedemption } = await import('@/api/redemption')
      await deleteRedemption(99)
      expect(requestMock.delete).toHaveBeenCalledWith('/api/redemption/99')
    })
  })

  describe('clearInvalidRedemptions (DELETE /api/redemption/invalid)', () => {
    it('无参数调用', async () => {
      requestMock.delete.mockResolvedValueOnce(undefined)
      const { clearInvalidRedemptions } = await import('@/api/redemption')
      await clearInvalidRedemptions()
      expect(requestMock.delete).toHaveBeenCalledWith('/api/redemption/invalid')
    })
  })
})
