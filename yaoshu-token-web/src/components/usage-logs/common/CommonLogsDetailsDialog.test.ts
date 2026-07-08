/**
 * CommonLogsDetailsDialog 白盒测试。
 * 核心覆盖：billingRows computed 的三模式分支（per-token/per-call/tiered）
 *          + 缓存/音频/图像附加维度 + 附加调用费用 + Total Cost
 */
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createPinia } from 'pinia'
import CommonLogsDetailsDialog from './CommonLogsDetailsDialog.vue'
import type { UsageLog } from '@/api/usage-log/types'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: {
    'zh-CN': {
      usageLogs: {
        details: {
          billingBreakdown: '计费详情',
          billingMode: '计费模式',
          billingPerToken: '按 Token 计费',
          billingPerCall: '按次计费',
          billingTiered: '阶梯计费',
          inputPrice: '输入价格',
          outputPrice: '输出价格',
          modelPrice: '模型价格',
          matchedTier: '匹配阶梯',
          userGroupRatio: '用户分组倍率',
          groupRatioDetail: '分组倍率',
          cacheRead: '缓存读取',
          cacheWrite: '缓存写入',
          cacheWrite5m: '缓存写入 5m',
          cacheWrite1h: '缓存写入 1h',
          audioInput: '音频输入',
          audioOutput: '音频输出',
          imageInput: '图像输入',
          webSearch: 'Web 搜索',
          fileSearch: '文件搜索',
          imageGeneration: '图像生成',
          audioInputPrice: '音频输入价格',
          totalCost: '总费用',
          upstreamModel: '上游模型',
          title: '日志详情',
        },
        columns: {
          time: '时间',
          type: '类型',
          model: '模型',
          token: '令牌',
          timing: '耗时',
          promptTokens: '输入 Token',
          completionTokens: '输出 Token',
          quota: '配额',
          content: '内容',
        },
      },
    },
  },
})

function makeLog(other: Record<string, unknown>): UsageLog {
  return {
    id: 1,
    createdAt: 1700000000,
    type: 1,
    modelName: 'test-model',
    tokenName: 'test-token',
    useTime: 100,
    promptTokens: 100,
    completionTokens: 50,
    quota: 500000,
    other: JSON.stringify(other),
    content: '',
  } as unknown as UsageLog
}

function mountDialog(log: UsageLog | null) {
  // stub ElDialog 避免 teleport/lazy 渲染问题，使内容直接渲染在 wrapper 内
  return mount(CommonLogsDetailsDialog, {
    props: { open: true, log, billingDisplayMode: 'usd' },
    global: {
      plugins: [i18n, createPinia()],
      stubs: {
        ElDialog: { template: '<div class="el-dialog-stub"><slot /></div>' },
      },
    },
  })
}

/** 提取渲染的计费详情区域行文本 */
function getBillingRows(wr: ReturnType<typeof mountDialog>): string[] {
  const rows = wr.findAll('.logs-details__row')
  return rows.map((r) => r.text())
}

describe('CommonLogsDetailsDialog - billingRows', () => {
  it('per-token 模式：modelRatio>0 且 modelPrice=0 时显示输入/输出价格', () => {
    const wr = mountDialog(
      makeLog({ modelRatio: 0.5, completionRatio: 2, modelPrice: 0 }),
    )
    const rows = getBillingRows(wr)
    const combined = rows.join('\n')
    expect(combined).toContain('按 Token 计费')
    expect(combined).toContain('输入价格')
    // baseInputUSD = 0.5 * 2.0 = 1.0 → $1/M（formatCurrencyValue minFrac:0 去尾零）
    expect(combined).toContain('$1/M')
    // outputPrice = 1.0 * 2 = 2.0 → $2/M
    expect(combined).toContain('输出价格')
    expect(combined).toContain('$2/M')
  })

  it('per-call 模式：modelPrice>0 时显示模型价格', () => {
    const wr = mountDialog(makeLog({ modelRatio: 0, modelPrice: 0.01 }))
    const rows = getBillingRows(wr)
    const combined = rows.join('\n')
    expect(combined).toContain('按次计费')
    expect(combined).toContain('模型价格')
    // formatPrice(0.01) = $0.01（minFrac:0 去尾零，非 $0.0100）
    expect(combined).toContain('$0.01')
  })

  it('tiered 模式：billingMode=tiered_expr 时显示阶梯计费', () => {
    const wr = mountDialog(
      makeLog({ billingMode: 'tiered_expr', matchedTier: 'tier-1' }),
    )
    const rows = getBillingRows(wr)
    const combined = rows.join('\n')
    expect(combined).toContain('阶梯计费')
    expect(combined).toContain('tier-1')
  })

  it('tiered 模式优先于 per-call（modelPrice>0 但 billingMode=tiered）', () => {
    const wr = mountDialog(
      makeLog({ billingMode: 'tiered_expr', modelPrice: 0.01, modelRatio: 0.5 }),
    )
    const combined = getBillingRows(wr).join('\n')
    expect(combined).toContain('阶梯计费')
    expect(combined).not.toContain('按次计费')
    expect(combined).not.toContain('按 Token 计费')
  })

  it('缓存维度：cacheRatio≠1 且有 cacheTokens 时显示缓存读取', () => {
    const wr = mountDialog(
      makeLog({
        modelRatio: 1,
        modelPrice: 0,
        cacheTokens: 500,
        cacheRatio: 0.5,
        cacheCreationTokens: 200,
        cacheCreationRatio: 0.75,
      }),
    )
    const combined = getBillingRows(wr).join('\n')
    expect(combined).toContain('缓存读取')
    expect(combined).toContain('缓存写入')
  })

  it('音频维度：audioRatio>0 且≠1 时显示音频输入', () => {
    const wr = mountDialog(
      makeLog({ modelRatio: 1, modelPrice: 0, audio_ratio: 1.5 }),
    )
    const combined = getBillingRows(wr).join('\n')
    expect(combined).toContain('音频输入')
  })

  it('图像维度：imageRatio>0 且≠1 时显示图像输入', () => {
    const wr = mountDialog(
      makeLog({ modelRatio: 1, modelPrice: 0, image_ratio: 2 }),
    )
    const combined = getBillingRows(wr).join('\n')
    expect(combined).toContain('图像输入')
  })

  it('附加调用费用：web_search_call_count>0 时显示 Web 搜索', () => {
    const wr = mountDialog(
      makeLog({
        modelRatio: 1,
        modelPrice: 0,
        web_search_call_count: 3,
        web_search_price: 0.005,
      }),
    )
    const combined = getBillingRows(wr).join('\n')
    expect(combined).toContain('Web 搜索')
    expect(combined).toContain('3')
  })

  it('Total Cost 行始终存在', () => {
    const wr = mountDialog(makeLog({ modelRatio: 1, modelPrice: 0 }))
    const highlight = wr.find('.logs-details__row--highlight')
    expect(highlight.exists()).toBe(true)
    expect(highlight.text()).toContain('总费用')
  })

  it('用户分组倍率：userGroupRatio 有效且≠-1 时显示', () => {
    const wr = mountDialog(
      makeLog({ modelRatio: 1, modelPrice: 0, userGroupRatio: 1.5 }),
    )
    const combined = getBillingRows(wr).join('\n')
    expect(combined).toContain('用户分组倍率')
    expect(combined).toContain('1.5000')
  })

  it('log=null 时 billingRows 为空', () => {
    const wr = mountDialog(null)
    expect(wr.find('.logs-details__row--highlight').exists()).toBe(false)
  })

  it('other 无效 JSON 时不崩溃', () => {
    const wr = mountDialog({ ...makeLog({}), other: '{invalid' } as UsageLog)
    // 不崩溃即可，billingRows 为空
    expect(wr.find('.logs-details__row--highlight').exists()).toBe(false)
  })
})
