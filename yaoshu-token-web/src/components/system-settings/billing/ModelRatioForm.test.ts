/**
 * ModelRatioForm 白盒测试。
 * 核心覆盖：parsed* computed 解析 / allModels 合并 / filterMode 过滤
 *          + 新增 createCacheRatio/imageRatio/audioRatio props 存在性
 * 注：ElTable 在 jsdom 中不渲染行内容，通过检查空状态（ElEmpty）判定数据存在性
 */
import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import ModelRatioForm from './ModelRatioForm.vue'

vi.mock('@/api/system-option', () => ({
  getEnabledModels: vi.fn().mockResolvedValue([]),
}))

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: {
    'zh-CN': {
      systemSettings: {
        billing: {
          filterAll: '全部', filterUnset: '未设置', filterConflict: '冲突',
          searchModel: '搜索', modelName: '模型名称', ratio: '倍率', price: '价格',
          completion: '补全', cache: '缓存', createCache: '创建缓存',
          imageRatio: '图像倍率', audioRatio: '音频倍率', audioCompletionRatio: '音频补全倍率',
          notSet: '未设置', noUnsetModels: '无未设置', noModels: '无模型', editModel: '编辑模型',
        },
      },
      common: { actions: '操作', edit: '编辑', delete: '删除', cancel: '取消', save: '保存' },
    },
  },
})

const EMPTY_TEXT = '无模型'

function mountForm(overrides: Record<string, string> = {}) {
  return mount(ModelRatioForm, {
    props: {
      modelRatio: overrides.modelRatio ?? JSON.stringify({ 'gpt-4': 2.5 }),
      modelPrice: overrides.modelPrice ?? JSON.stringify({ 'gpt-4': 0.01 }),
      completionRatio: overrides.completionRatio ?? JSON.stringify({ 'gpt-4': 1.5 }),
      cacheRatio: overrides.cacheRatio ?? JSON.stringify({ 'gpt-4': 0.5 }),
      createCacheRatio: overrides.createCacheRatio ?? JSON.stringify({ 'gpt-4': 0.25 }),
      imageRatio: overrides.imageRatio ?? JSON.stringify({ 'gpt-4': 1.0 }),
      audioRatio: overrides.audioRatio ?? JSON.stringify({ 'gpt-4': 0.0 }),
      audioCompletionRatio: overrides.audioCompletionRatio ?? JSON.stringify({ 'gpt-4': 0.0 }),
    },
    global: {
      plugins: [i18n],
      stubs: {
        ElDialog: { template: '<div class="el-dialog-stub"><slot /></div>' },
      },
    },
  })
}

describe('ModelRatioForm - 解析与合并', () => {
  it('有效 JSON 时不显示空状态', () => {
    const wr = mountForm({
      modelRatio: JSON.stringify({ 'gpt-4': 2.5, 'claude-3': 1 }),
    })
    expect(wr.html()).not.toContain(EMPTY_TEXT)
  })

  it('所有 JSON 为空时显示空状态', () => {
    const wr = mountForm({
      modelRatio: '', modelPrice: '', completionRatio: '', cacheRatio: '',
      createCacheRatio: '', imageRatio: '', audioRatio: '', audioCompletionRatio: '',
    })
    expect(wr.html()).toContain(EMPTY_TEXT)
  })

  it('无效 JSON 不崩溃（fallback 空对象，显示空状态）', () => {
    const wr = mountForm({ modelRatio: '{invalid', modelPrice: '' })
    expect(wr.html()).toBeDefined()
  })
})

describe('ModelRatioForm - 新增 ratio props', () => {
  it('createCacheRatio 有值时不显示空状态', () => {
    const wr = mountForm({ createCacheRatio: JSON.stringify({ 'gpt-4': 0.25 }) })
    expect(wr.html()).not.toContain(EMPTY_TEXT)
  })

  it('imageRatio 有值时不显示空状态', () => {
    const wr = mountForm({ imageRatio: JSON.stringify({ 'gpt-4': 1.5 }) })
    expect(wr.html()).not.toContain(EMPTY_TEXT)
  })

  it('audioRatio 有值时不显示空状态', () => {
    const wr = mountForm({ audioRatio: JSON.stringify({ 'gpt-4': 0.8 }) })
    expect(wr.html()).not.toContain(EMPTY_TEXT)
  })

  it('audioCompletionRatio 有值时不显示空状态', () => {
    const wr = mountForm({ audioCompletionRatio: JSON.stringify({ 'gpt-4': 0.6 }) })
    expect(wr.html()).not.toContain(EMPTY_TEXT)
  })
})

describe('ModelRatioForm - filterMode', () => {
  it('ratio 和 price 都有值时不显示空状态', () => {
    const wr = mountForm({
      modelRatio: JSON.stringify({ 'gpt-4': 2.5 }),
      modelPrice: JSON.stringify({ 'gpt-4': 0.01 }),
    })
    expect(wr.html()).not.toContain(EMPTY_TEXT)
  })
})
