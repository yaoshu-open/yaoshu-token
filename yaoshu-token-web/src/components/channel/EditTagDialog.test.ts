/**
 * EditTagDialog 组件单元测试。
 * 覆盖点：
 *   1. initFormData：三接口并发拉取 + 拦截器解包数据消费（models/groups/tagModels）
 *   2. handleSubmit：void 返回值下的成功分支（emit success + 关闭）与失败分支（ElMessage.error）
 *   3. 无变更时 warning 提示
 *
 * Mock 策略：mock @/api/channel（API 层属外部边界）+ ElMessageBox（阻断交互）+ ElMessage（防污染）
 *           + vue-i18n（避免 JSON 模板中的 {} 被误判为插值占位符），不 mock 组件本身逻辑。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

// Mock API 模块
const apiMock = {
  editTagChannels: vi.fn(),
  getAllModels: vi.fn(),
  getGroups: vi.fn(),
  getTagModels: vi.fn()
}
vi.mock('@/api/channel', () => apiMock)

// Mock ElMessage（避免污染控制台 + 捕获调用）
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
    ElMessage: elMessageMock,
    // ElMessageBox 保持真实实现（confirm 返回 Promise）
    ElMessageBox: actual.ElMessageBox
  }
})

// Mock vue-i18n：t 函数直接返回 key，避免 JSON 模板中的 {} 触发编译错误
vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
    locale: { value: 'zh-CN' }
  })
}))

// 直接挂载 EditTagDialog
async function mountEditTagDialog(tag = 'test-c2', modelValue = true) {
  const EditTagDialog = (await import('@/components/channel/EditTagDialog.vue')).default
  return mount(EditTagDialog, {
    props: { modelValue, tag },
    global: {
      stubs: {
        // stub LoadingState 避免引入额外依赖
        LoadingState: { template: '<div class="loading-stub" />' }
      }
    }
  })
}

// <script setup> 变量默认不暴露到 vm，用类型断言访问内部状态与方法
interface EditTagDialogVm {
  modelOptions: { label: string; value: string }[]
  groupOptions: { label: string; value: string }[]
  form: {
    new_tag: string
    models: string[]
    model_mapping: string | null
    param_override: string | null
    header_override: string | null
    groups: string[]
  }
  handleSubmit: () => Promise<void>
}

function getVm(wrapper: Awaited<ReturnType<typeof mountEditTagDialog>>): EditTagDialogVm {
  return wrapper.vm as unknown as EditTagDialogVm
}

describe('EditTagDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.resetModules()
  })

  describe('initFormData', () => {
    it('三接口并发拉取并正确消费解包数据', async () => {
      apiMock.getAllModels.mockResolvedValueOnce([
        { id: 'gpt-4o' },
        { id: 'claude-3-5' }
      ])
      apiMock.getGroups.mockResolvedValueOnce(['default', 'svip'])
      apiMock.getTagModels.mockResolvedValueOnce('gpt-4o,deepseek-chat')

      const wrapper = await mountEditTagDialog('test-c2', true)
      await flushPromises()
      const vm = getVm(wrapper)

      expect(apiMock.getAllModels).toHaveBeenCalledOnce()
      expect(apiMock.getGroups).toHaveBeenCalledOnce()
      expect(apiMock.getTagModels).toHaveBeenCalledWith('test-c2')

      // 验证 modelOptions 被填充（gpt-4o 已在 models 中，deepseek-chat 被追加）
      // 验证 form.models 被正确 split
      expect(vm.modelOptions).toEqual([
        { label: 'gpt-4o', value: 'gpt-4o' },
        { label: 'claude-3-5', value: 'claude-3-5' },
        { label: 'deepseek-chat', value: 'deepseek-chat' }
      ])
      expect(vm.groupOptions).toEqual([
        { label: 'default', value: 'default' },
        { label: 'svip', value: 'svip' }
      ])
      expect(vm.form.models).toEqual(['gpt-4o', 'deepseek-chat'])
    })

    it('tagModelsRes 为空字符串时 form.models 为空数组', async () => {
      apiMock.getAllModels.mockResolvedValueOnce([])
      apiMock.getGroups.mockResolvedValueOnce([])
      apiMock.getTagModels.mockResolvedValueOnce('')

      const wrapper = await mountEditTagDialog('empty-tag', true)
      await flushPromises()

      expect(getVm(wrapper).form.models).toEqual([])
    })

    it('接口异常时 ElMessage.error 提示', async () => {
      apiMock.getAllModels.mockRejectedValueOnce(new Error('网络异常'))
      apiMock.getGroups.mockResolvedValueOnce([])
      apiMock.getTagModels.mockResolvedValueOnce('')

      await mountEditTagDialog('error-tag', true)
      await flushPromises()

      expect(elMessageMock.error).toHaveBeenCalled()
    })
  })

  describe('handleSubmit', () => {
    it('成功分支：editTagChannels 返回 void，emit success + 关闭 dialog', async () => {
      apiMock.getAllModels.mockResolvedValueOnce([{ id: 'gpt-4o' }])
      apiMock.getGroups.mockResolvedValueOnce(['default'])
      apiMock.getTagModels.mockResolvedValueOnce('gpt-4o')
      apiMock.editTagChannels.mockResolvedValueOnce(undefined)

      const wrapper = await mountEditTagDialog('test-c2', true)
      await flushPromises()
      const vm = getVm(wrapper)

      // 修改 new_tag 触发 hasChanges
      vm.form.new_tag = 'test-c2-renamed'
      await vm.handleSubmit()
      await flushPromises()

      expect(apiMock.editTagChannels).toHaveBeenCalledOnce()
      const callArgs = apiMock.editTagChannels.mock.calls[0][0]
      expect(callArgs.tag).toBe('test-c2')
      expect(callArgs.newTag).toBe('test-c2-renamed')
      expect(elMessageMock.success).toHaveBeenCalled()
      expect(wrapper.emitted('success')).toBeTruthy()
      expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    })

    it('失败分支：editTagChannels reject，ElMessage.error 提示，不 emit success', async () => {
      apiMock.getAllModels.mockResolvedValueOnce([])
      apiMock.getGroups.mockResolvedValueOnce([])
      apiMock.getTagModels.mockResolvedValueOnce('')
      apiMock.editTagChannels.mockRejectedValueOnce(new Error('权限不足'))

      const wrapper = await mountEditTagDialog('test-c2', true)
      await flushPromises()
      const vm = getVm(wrapper)

      vm.form.new_tag = 'renamed'
      await vm.handleSubmit()
      await flushPromises()

      expect(apiMock.editTagChannels).toHaveBeenCalledOnce()
      expect(elMessageMock.error).toHaveBeenCalled()
      expect(wrapper.emitted('success')).toBeFalsy()
    })

    it('无变更时 warning 提示，不调用 editTagChannels', async () => {
      apiMock.getAllModels.mockResolvedValueOnce([])
      apiMock.getGroups.mockResolvedValueOnce([])
      apiMock.getTagModels.mockResolvedValueOnce('')

      const wrapper = await mountEditTagDialog('test-c2', true)
      await flushPromises()

      // new_tag 保持等于 props.tag，无任何字段变更
      await getVm(wrapper).handleSubmit()
      await flushPromises()

      expect(apiMock.editTagChannels).not.toHaveBeenCalled()
      expect(elMessageMock.warning).toHaveBeenCalled()
    })
  })
})
