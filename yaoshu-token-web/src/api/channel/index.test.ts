/**
 * Channel API Service 单元测试。
 * 覆盖点：
 *   1. 4 个 service 函数的真实请求路径（URL + method + params/body）
 *   2. USE_MOCK=true 时切换到 mock 实现（动态 import）
 *   3. 拦截器解包后返回值类型对齐（void / string / ChannelModel[] / string[]）
 *
 * Mock 策略：mock @/utils/request 模块（核心依赖 request 层属外部边界，单测可 mock），
 *           不 mock mock.ts（验证 mock 数据本身正确性）。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// Mock request 模块，捕获调用参数
const requestMock = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn()
}

vi.mock('@/utils/request', () => ({
  request: requestMock
}))

// 捕获 import.meta.env 以便逐用例切换 VITE_CHANNEL_MOCK
const originalEnv = { ...import.meta.env }

describe('channel API service', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // 默认真实模式
    vi.stubEnv('DEV', true)
    vi.stubEnv('VITE_CHANNEL_MOCK', '')
  })

  afterEach(() => {
    // 恢复 env
    for (const key of Object.keys(import.meta.env)) {
      if (!(key in originalEnv)) {
        vi.stubEnv(key, originalEnv[key] ?? '')
      }
    }
    vi.resetModules()
  })

  describe('editTagChannels (PUT /api/channel/tag)', () => {
    it('真实模式：调用 request.put 并透传 params', async () => {
      requestMock.put.mockResolvedValueOnce(undefined)
      const { editTagChannels } = await import('@/api/channel')
      const params = {
        tag: 'test-c2',
        models: 'gpt-4o,claude-3-5',
        groups: 'default'
      }
      const result = await editTagChannels(params)
      expect(requestMock.put).toHaveBeenCalledOnce()
      expect(requestMock.put).toHaveBeenCalledWith('/api/channel/tag', params)
      expect(result).toBeUndefined()
    })

    it('Mock 模式：切换到 mockEditTagChannels 返回 void', async () => {
      vi.stubEnv('VITE_CHANNEL_MOCK', 'true')
      const { editTagChannels } = await import('@/api/channel')
      const result = await editTagChannels({ tag: 'test-c2' })
      expect(requestMock.put).not.toHaveBeenCalled()
      expect(result).toBeUndefined()
    })
  })

  describe('getTagModels (GET /api/channel/tag/models)', () => {
    it('真实模式：调用 request.get 并透传 tag 作为 query 参数', async () => {
      const mockData = 'gpt-4o,claude-3-5-sonnet'
      requestMock.get.mockResolvedValueOnce(mockData)
      const { getTagModels } = await import('@/api/channel')
      const result = await getTagModels('prod-tag')
      expect(requestMock.get).toHaveBeenCalledOnce()
      expect(requestMock.get).toHaveBeenCalledWith('/api/channel/tag/models', {
        params: { tag: 'prod-tag' }
      })
      expect(result).toBe(mockData)
    })

    it('Mock 模式：返回逗号分隔字符串', async () => {
      vi.stubEnv('VITE_CHANNEL_MOCK', 'true')
      const { getTagModels } = await import('@/api/channel')
      const result = await getTagModels('any-tag')
      expect(requestMock.get).not.toHaveBeenCalled()
      expect(typeof result).toBe('string')
      expect(result.split(',').length).toBeGreaterThan(0)
    })

    it('真实模式：空 tag 仍透传（后端负责校验）', async () => {
      requestMock.get.mockResolvedValueOnce('')
      const { getTagModels } = await import('@/api/channel')
      const result = await getTagModels('')
      expect(requestMock.get).toHaveBeenCalledWith('/api/channel/tag/models', {
        params: { tag: '' }
      })
      expect(result).toBe('')
    })
  })

  describe('getAllModels (GET /api/channel/models)', () => {
    it('真实模式：返回 ChannelModel[]（拦截器已解包）', async () => {
      const mockModels = [
        { id: 'gpt-4o', owned_by: 'OpenAI' },
        { id: 'claude-3-5', owned_by: 'Anthropic' }
      ]
      requestMock.get.mockResolvedValueOnce(mockModels)
      const { getAllModels } = await import('@/api/channel')
      const result = await getAllModels()
      expect(requestMock.get).toHaveBeenCalledOnce()
      expect(requestMock.get).toHaveBeenCalledWith('/api/channel/models')
      expect(result).toEqual(mockModels)
      expect(result.length).toBe(2)
    })

    it('Mock 模式：返回内置 MOCK_MODELS 数组', async () => {
      vi.stubEnv('VITE_CHANNEL_MOCK', 'true')
      const { getAllModels } = await import('@/api/channel')
      const result = await getAllModels()
      expect(requestMock.get).not.toHaveBeenCalled()
      expect(Array.isArray(result)).toBe(true)
      expect(result.length).toBeGreaterThan(0)
      expect(result.every((m) => typeof m.id === 'string')).toBe(true)
    })
  })

  describe('getGroups (GET /api/group/)', () => {
    it('真实模式：返回 string[]（拦截器已解包）', async () => {
      const mockGroups = ['default', 'svip', 'vip']
      requestMock.get.mockResolvedValueOnce(mockGroups)
      const { getGroups } = await import('@/api/channel')
      const result = await getGroups()
      expect(requestMock.get).toHaveBeenCalledOnce()
      expect(requestMock.get).toHaveBeenCalledWith('/api/group/')
      expect(result).toEqual(mockGroups)
    })

    it('Mock 模式：返回内置 MOCK_GROUPS 数组', async () => {
      vi.stubEnv('VITE_CHANNEL_MOCK', 'true')
      const { getGroups } = await import('@/api/channel')
      const result = await getGroups()
      expect(requestMock.get).not.toHaveBeenCalled()
      expect(Array.isArray(result)).toBe(true)
      expect(result.length).toBeGreaterThan(0)
    })
  })

  describe('USE_MOCK 开关判定', () => {
    it('DEV=false 时即使 VITE_CHANNEL_MOCK=true 也不走 mock', async () => {
      vi.stubEnv('DEV', false)
      vi.stubEnv('VITE_CHANNEL_MOCK', 'true')
      requestMock.get.mockResolvedValueOnce([])
      const { getAllModels } = await import('@/api/channel')
      await getAllModels()
      // DEV=false 时 USE_MOCK=false，应走真实请求
      expect(requestMock.get).toHaveBeenCalledOnce()
    })
  })
})
