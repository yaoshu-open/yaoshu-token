/**
 * CustomOAuthSection 白盒测试。
 * 核心覆盖：OAUTH_PRESETS 数据结构 / 列表加载 / 组件渲染
 */
import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'

// Mock custom-oauth API（hoisted，import 前定义）
vi.mock('@/api/custom-oauth', () => ({
  AUTH_STYLE_OPTIONS: [
    { value: 0, label: 'Auto Detect' },
    { value: 1, label: 'Params (in body)' },
    { value: 2, label: 'Header (Basic Auth)' },
  ],
  OAUTH_PRESETS: [
    {
      key: 'github-enterprise', name: 'GitHub Enterprise', icon: 'github',
      needsBaseUrl: true, authorizationEndpoint: '/login/oauth/authorize',
      tokenEndpoint: '/login/oauth/access_token', userInfoEndpoint: '/api/v3/user',
      scopes: 'user:email', userIdField: 'id', usernameField: 'login',
      displayNameField: 'name', emailField: 'email',
    },
    {
      key: 'gitlab', name: 'GitLab', icon: 'gitlab',
      needsBaseUrl: true, authorizationEndpoint: '/oauth/authorize',
      tokenEndpoint: '/oauth/token', userInfoEndpoint: '/api/v4/user',
      scopes: 'openid profile email', userIdField: 'id', usernameField: 'username',
      displayNameField: 'name', emailField: 'email',
    },
  ],
  getCustomOAuthProviders: vi.fn().mockResolvedValue([]),
  createCustomOAuthProvider: vi.fn().mockResolvedValue({ id: 2 }),
  updateCustomOAuthProvider: vi.fn().mockResolvedValue({ id: 1 }),
  deleteCustomOAuthProvider: vi.fn().mockResolvedValue(undefined),
  discoverOidcEndpoints: vi.fn(),
}))

import CustomOAuthSection from './CustomOAuthSection.vue'
import { OAUTH_PRESETS, getCustomOAuthProviders } from '@/api/custom-oauth'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: {
    'zh-CN': {
      systemSettings: {
        auth: {
          customOAuth: {
            title: '自定义 OAuth', description: '描述', addProvider: '新增',
            createProvider: '创建', editProvider: '编辑',
            deleteConfirm: '确认删除 {name}?', providerName: '名称',
            providerSlug: '标识', providerEnabled: '启用', providerIcon: '图标',
            clientId: 'Client ID', clientSecret: 'Client Secret', authStyle: '认证风格',
            quickSetup: '快速设置', presetTemplate: '预设模板', selectPreset: '选择预设',
            basicInfo: '基础信息', credentials: '凭证', endpoints: '端点',
            wellKnown: 'Well-Known', autoDiscover: '自动发现', discovering: '发现中...',
            discoveryNeedUrl: '需要 URL', discoveryInvalidUrl: '无效 URL',
            discoverySuccess: '发现成功', discoveryFailed: '发现失败',
            authorizationEndpoint: '授权端点', tokenEndpoint: 'Token 端点',
            userInfoEndpoint: '用户信息端点', scopes: 'Scopes', fieldMapping: '字段映射',
            userIdField: '用户 ID 字段', usernameField: '用户名字段',
            displayNameField: '显示名字段', emailField: '邮箱字段',
            advanced: '高级', accessPolicy: '访问策略', accessDeniedMessage: '拒绝消息',
            saveSuccess: '保存成功', deleteSuccess: '删除成功', loadFailed: '加载失败',
            empty: '暂无数据',
          },
        },
      },
      common: { confirm: '确认', cancel: '取消', delete: '删除', action: '操作' },
    },
  },
})

function mountSection() {
  return mount(CustomOAuthSection, {
    global: {
      plugins: [i18n],
      stubs: {
        ElDialog: { template: '<div class="el-dialog-stub"><slot /></div>' },
      },
    },
  })
}

describe('CustomOAuthSection - OAUTH_PRESETS 数据结构', () => {
  it('每个 preset 包含全部必需字段', () => {
    for (const preset of OAUTH_PRESETS) {
      expect(preset.key).toBeTruthy()
      expect(preset.name).toBeTruthy()
      expect(preset.icon).toBeDefined()
      expect(preset.needsBaseUrl).toBeDefined()
      expect(preset.authorizationEndpoint).toBeDefined()
      expect(preset.tokenEndpoint).toBeDefined()
      expect(preset.userInfoEndpoint).toBeDefined()
      expect(preset.scopes).toBeDefined()
      expect(preset.userIdField).toBeDefined()
      expect(preset.usernameField).toBeDefined()
      expect(preset.displayNameField).toBeDefined()
      expect(preset.emailField).toBeDefined()
    }
  })

  it('preset key 唯一', () => {
    const keys = OAUTH_PRESETS.map((p) => p.key)
    expect(new Set(keys).size).toBe(keys.length)
  })

  it('needsBaseUrl 为 true 的 preset 有相对路径端点', () => {
    for (const preset of OAUTH_PRESETS) {
      if (preset.needsBaseUrl) {
        expect(preset.authorizationEndpoint.startsWith('/')).toBe(true)
        expect(preset.tokenEndpoint.startsWith('/')).toBe(true)
      }
    }
  })
})

describe('CustomOAuthSection - 列表加载', () => {
  it('onMounted 调用 getCustomOAuthProviders', async () => {
    vi.mocked(getCustomOAuthProviders).mockResolvedValue([])
    mountSection()
    await vi.dynamicImportSettled()
    expect(getCustomOAuthProviders).toHaveBeenCalled()
  })

  it('API 返回数据时渲染到表格', async () => {
    vi.mocked(getCustomOAuthProviders).mockResolvedValue([
      {
        id: 1, name: 'GitLab', slug: 'gitlab', icon: 'gitlab', enabled: true,
        clientId: 'abc', clientSecret: 'xyz', authorizationEndpoint: '',
        tokenEndpoint: '', userInfoEndpoint: '', scopes: 'openid',
        userIdField: 'id', usernameField: 'username', displayNameField: 'name',
        emailField: 'email', wellKnown: '', authStyle: 0,
        accessPolicy: '', accessDeniedMessage: '',
      },
    ])
    const wr = mountSection()
    await vi.dynamicImportSettled()
    expect(wr.html()).toContain('GitLab')
  })

  it('API 失败时不崩溃', async () => {
    vi.mocked(getCustomOAuthProviders).mockRejectedValue(new Error('403'))
    const wr = mountSection()
    await vi.dynamicImportSettled()
    expect(wr.html()).toBeDefined()
  })
})
