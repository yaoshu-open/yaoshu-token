/**
 * useNameCheck 单元测试。
 * 覆盖点：
 *   1. 空名称 → status 为 idle
 *   2. 有效名称 → 防抖后 → available
 *   3. 被占用名称 → 防抖后 → taken
 *   4. 防抖 400ms 内不触发请求
 *   5. API 失败时回退到 idle
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { flushPromises } from '@vue/test-utils'

// vi.mock 被提升到顶部，工厂内不能引用外部变量 → 用 vi.mocked() 在测试中获取 mock
vi.mock('@/api/deployment', () => ({
  checkDeploymentName: vi.fn()
}))

import { checkDeploymentName } from '@/api/deployment'
import { useNameCheck } from '@/composables/deployment/useNameCheck'

const checkNameMock = vi.mocked(checkDeploymentName)

describe('useNameCheck', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('空名称时 status 为 idle', async () => {
    const name = ref('')
    const { status } = useNameCheck(name)
    name.value = ''
    await vi.advanceTimersByTimeAsync(500)
    expect(status.value).toBe('idle')
    expect(checkNameMock).not.toHaveBeenCalled()
  })

  it('有效名称防抖后变为 available', async () => {
    checkNameMock.mockResolvedValue({ available: true })
    const name = ref('')
    const { status } = useNameCheck(name)
    name.value = 'my-deploy'

    // 防抖 400ms 内不触发
    await vi.advanceTimersByTimeAsync(300)
    expect(checkNameMock).not.toHaveBeenCalled()

    // 400ms 后触发
    await vi.advanceTimersByTimeAsync(200)
    await flushPromises()
    expect(status.value).toBe('available')
    expect(checkNameMock).toHaveBeenCalledWith('my-deploy')
  })

  it('被占用名称防抖后变为 taken', async () => {
    checkNameMock.mockResolvedValue({ available: false })
    const name = ref('')
    const { status } = useNameCheck(name)
    name.value = 'test'

    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    expect(status.value).toBe('taken')
    expect(checkNameMock).toHaveBeenCalledWith('test')
  })

  it('防抖 400ms 内不触发请求', async () => {
    checkNameMock.mockResolvedValue({ available: true })
    const name = ref('')
    const { status } = useNameCheck(name)
    name.value = 'dep1'

    await vi.advanceTimersByTimeAsync(300)
    expect(checkNameMock).not.toHaveBeenCalled()
    expect(status.value).toBe('idle')

    await vi.advanceTimersByTimeAsync(200)
    await flushPromises()
    expect(checkNameMock).toHaveBeenCalled()
  })

  it('API 失败时 status 回退到 idle', async () => {
    checkNameMock.mockRejectedValue(new Error('Network'))
    const name = ref('')
    const { status } = useNameCheck(name)
    name.value = 'failed'

    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    expect(status.value).toBe('idle')
  })
})
