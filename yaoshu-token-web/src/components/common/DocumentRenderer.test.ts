/**
 * DocumentRenderer 组件单元测试。
 * 覆盖点：
 *   1. contentType 判断：URL / HTML / Markdown / empty
 *   2. 加载状态渲染
 *   3. 空状态渲染
 *   4. URL 态链接卡片渲染
 *   5. HTML 态 v-html 渲染（DOMPurify 清洗）
 *   6. Markdown 态 renderMarkdown 渲染
 *
 * Mock 策略：mock @/utils/request + @/utils/markdown + element-plus + vue-i18n
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

// Mock request
const requestMock = { get: vi.fn() }
vi.mock('@/utils/request', () => ({
  request: requestMock
}))

// Mock renderMarkdown
const renderMarkdownMock = vi.fn((src: string) => `<p>${src}</p>`)
vi.mock('@/utils/markdown', () => ({
  renderMarkdown: renderMarkdownMock
}))

// Mock ElMessage
const elMessageMock = {
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
  info: vi.fn()
}
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: elMessageMock
  }
})

// Mock vue-i18n
vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      if (params && typeof params === 'object') {
        let result = key
        for (const [k, v] of Object.entries(params)) {
          result = result.replace(`{${k}}`, String(v))
        }
        return result
      }
      return key
    },
    locale: { value: 'zh-CN' }
  })
}))

async function mountDocumentRenderer(props: Record<string, unknown> = {}) {
  const DocumentRenderer = (await import('@/components/common/DocumentRenderer.vue')).default
  return mount(DocumentRenderer, {
    props: {
      apiEndpoint: '/api/legal/user_agreement',
      title: '用户协议',
      cacheKey: 'test_doc',
      ...props
    }
  })
}

describe('DocumentRenderer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('渲染加载状态', async () => {
    requestMock.get.mockReturnValue(new Promise(() => {})) // 永不 resolve
    const wrapper = await mountDocumentRenderer()
    await flushPromises()
    expect(wrapper.find('.document-renderer__loading').exists()).toBe(true)
  })

  it('渲染空状态（后端返回空字符串）', async () => {
    requestMock.get.mockResolvedValue('')
    const wrapper = await mountDocumentRenderer()
    await flushPromises()
    expect(wrapper.find('.document-renderer__empty').exists()).toBe(true)
  })

  it('渲染 URL 内容（链接卡片）', async () => {
    requestMock.get.mockResolvedValue('https://example.com/terms')
    const wrapper = await mountDocumentRenderer()
    await flushPromises()
    expect(wrapper.find('.document-renderer__url-card').exists()).toBe(true)
    expect(wrapper.find('a.document-renderer__url-link').attributes('href')).toBe('https://example.com/terms')
  })

  it('渲染 HTML 内容（v-html + DOMPurify 清洗）', async () => {
    requestMock.get.mockResolvedValue('<p>Hello <strong>World</strong></p>')
    const wrapper = await mountDocumentRenderer()
    await flushPromises()
    expect(wrapper.find('.document-renderer__html').exists()).toBe(true)
    expect(wrapper.find('.document-renderer__html').html()).toContain('<strong>World</strong>')
  })

  it('渲染 Markdown 内容（调用 renderMarkdown）', async () => {
    renderMarkdownMock.mockReturnValue('<p>rendered markdown</p>')
    requestMock.get.mockResolvedValue('# Title\n\nSome content')
    const wrapper = await mountDocumentRenderer()
    await flushPromises()
    expect(wrapper.find('.document-renderer__markdown').exists()).toBe(true)
    expect(renderMarkdownMock).toHaveBeenCalledWith('# Title\n\nSome content')
  })

  it('localStorage 缓存命中时立即渲染缓存', async () => {
    localStorage.setItem('test_doc', '# Cached Content')
    requestMock.get.mockResolvedValue('# Cached Content')
    const wrapper = await mountDocumentRenderer()
    await flushPromises()
    // 缓存命中应渲染 Markdown 态
    expect(wrapper.find('.document-renderer__markdown').exists()).toBe(true)
    expect(renderMarkdownMock).toHaveBeenCalledWith('# Cached Content')
  })

  it('后端失败且无缓存时显示错误', async () => {
    requestMock.get.mockRejectedValue(new Error('Network error'))
    const wrapper = await mountDocumentRenderer()
    await flushPromises()
    expect(wrapper.find('.document-renderer__error').exists()).toBe(true)
    expect(elMessageMock.error).toHaveBeenCalled()
  })

  it('后端失败但有缓存时保留缓存内容', async () => {
    localStorage.setItem('test_doc', '# Cached')
    requestMock.get.mockRejectedValue(new Error('Network error'))
    const wrapper = await mountDocumentRenderer()
    await flushPromises()
    // 应渲染缓存内容而非错误
    expect(wrapper.find('.document-renderer__markdown').exists()).toBe(true)
    expect(wrapper.find('.document-renderer__error').exists()).toBe(false)
  })
})
