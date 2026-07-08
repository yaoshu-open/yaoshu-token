/**
 * ChatSettingsEditor 白盒测试。
 * 核心覆盖：chats computed 解析 / syncToJson 回写 / 搜索过滤 / JSON 同步
 */
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import ChatSettingsEditor from './ChatSettingsEditor.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: {
    'zh-CN': {
      systemSettings: {
        content: {
          chatVisual: '可视化',
          chatJson: 'JSON',
          chatSearch: '搜索',
          chatAdd: '新增',
          chatEdit: '编辑',
          chatNoResults: '无结果',
          chatEmpty: '暂无数据',
          chatColumnName: '名称',
          chatColumnUrl: '链接',
          chatJsonHint: '提示',
        },
      },
      common: { actions: '操作', cancel: '取消', save: '保存' },
    },
  },
})

function mountEditor(modelValue: string) {
  return mount(ChatSettingsEditor, {
    props: { modelValue },
    global: {
      plugins: [i18n],
      stubs: {
        ElDialog: { template: '<div class="el-dialog-stub"><slot /></div>' },
      },
    },
  })
}

describe('ChatSettingsEditor - chats computed 解析', () => {
  it('正确解析标准 JSON 格式 [{name:url}]', () => {
    const json = JSON.stringify([
      { ChatGPT: 'https://chat.openai.com' },
      { Claude: 'https://claude.ai' },
    ])
    const wr = mountEditor(json)
    // 有数据时不显示空状态
    expect(wr.html()).not.toContain('暂无数据')
  })

  it('空字符串解析为空数组（显示空状态）', () => {
    const wr = mountEditor('')
    expect(wr.html()).toContain('暂无数据')
  })

  it('无效 JSON 解析为空数组（不崩溃）', () => {
    const wr = mountEditor('{invalid json')
    expect(wr.html()).toContain('暂无数据')
  })

  it('非数组 JSON 解析为空数组', () => {
    const wr = mountEditor('{"key": "value"}')
    expect(wr.html()).toContain('暂无数据')
  })

  it('多 key 对象被过滤（仅接受单 key 对象）', () => {
    const wr = mountEditor(JSON.stringify([{ name1: 'url1', name2: 'url2' }]))
    // 多 key 对象被 filter 掉，显示空状态
    expect(wr.html()).toContain('暂无数据')
  })
})

describe('ChatSettingsEditor - JSON 模式同步', () => {
  it('textarea 包含 modelValue 的 JSON 内容', () => {
    const json = JSON.stringify([{ test: 'url' }])
    const wr = mountEditor(json)
    const textarea = wr.find('textarea')
    expect(textarea.exists()).toBe(true)
    expect(textarea.element.value).toContain('test')
  })

  it('空 modelValue 时 textarea 显示 []', () => {
    const wr = mountEditor('')
    const textarea = wr.find('textarea')
    expect(textarea.element.value).toBe('[]')
  })
})
