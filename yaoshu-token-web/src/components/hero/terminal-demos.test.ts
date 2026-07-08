import { describe, expect, it } from 'vitest'
import {
  API_DEMOS,
  CYCLE_INTERVAL,
  TRANSITION_MS,
  tokenizeJsonLine,
  tokenizeResponseLine,
  truncateResponse
} from './terminal-demos'

describe('terminal-demos 常量与数据', () => {
  it('轮播间隔与过渡时长为正数', () => {
    expect(CYCLE_INTERVAL).toBe(4500)
    expect(TRANSITION_MS).toBe(220)
  })

  it('API_DEMOS 有 4 条演示数据', () => {
    expect(API_DEMOS).toHaveLength(4)
  })

  it('每条 demo 有合法 accent 与 method', () => {
    const validAccents = ['amber', 'blue', 'emerald', 'violet']
    const validMethods = ['GET', 'POST']
    for (const demo of API_DEMOS) {
      expect(validAccents).toContain(demo.accent)
      expect(validMethods).toContain(demo.method)
      expect(demo.tokens).toBeGreaterThan(0)
      expect(demo.latency).toBeGreaterThan(0)
    }
  })
})

describe('tokenizeJsonLine', () => {
  it('空行返回单个 muted 空格 token', () => {
    const tokens = tokenizeJsonLine('   ')
    expect(tokens).toHaveLength(1)
    expect(tokens[0].type).toBe('muted')
  })

  it('"key": 格式的串判定为 key 类型', () => {
    const tokens = tokenizeJsonLine('"model": "gpt-4o",')
    const keyToken = tokens.find((t) => t.text === '"model"')
    expect(keyToken).toBeDefined()
    expect(keyToken!.type).toBe('key')
  })

  it('"value" 格式的串判定为 string 类型', () => {
    const tokens = tokenizeJsonLine('"model": "gpt-4o",')
    const valueTokens = tokens.filter((t) => t.type === 'string')
    expect(valueTokens.length).toBeGreaterThan(0)
    expect(valueTokens.some((t) => t.text === '"gpt-4o"')).toBe(true)
  })

  it('非串部分判定为 muted', () => {
    const tokens = tokenizeJsonLine('"model": "gpt-4o",')
    const mutedTokens = tokens.filter((t) => t.type === 'muted')
    expect(mutedTokens.length).toBeGreaterThan(0)
    expect(mutedTokens.some((t) => t.text.includes(':'))).toBe(true)
    expect(mutedTokens.some((t) => t.text.includes(','))).toBe(true)
  })

  it('拼接所有 token 文本等于原始行', () => {
    const line = '"messages": ['
    const tokens = tokenizeJsonLine(line)
    const reconstructed = tokens.map((t) => t.text).join('')
    expect(reconstructed).toBe(line)
  })

  it('不含引号的行全部为 muted', () => {
    const tokens = tokenizeJsonLine('{')
    expect(tokens.every((t) => t.type === 'muted')).toBe(true)
  })
})

describe('tokenizeResponseLine', () => {
  const gptDemo = API_DEMOS[0] // gpt-chat

  it('无占位符的行走 tokenizeJsonLine', () => {
    const tokens = tokenizeResponseLine('{', gptDemo)
    expect(tokens.every((t) => t.type === 'muted')).toBe(true)
  })

  it('<text> 占位符替换为 accent token', () => {
    const line = '  "choices": [{ "message": { "content": <text> } }],'
    const tokens = tokenizeResponseLine(line, gptDemo)
    const accentToken = tokens.find((t) => t.type === 'accent')
    expect(accentToken).toBeDefined()
    expect(accentToken!.text).toBe(`"${truncateResponse(gptDemo)}"`)
  })

  it('<tokens> 占位符替换为 number token', () => {
    const line = '  "usage": { "total_tokens": <tokens> }'
    const tokens = tokenizeResponseLine(line, gptDemo)
    const numberToken = tokens.find((t) => t.type === 'number')
    expect(numberToken).toBeDefined()
    expect(numberToken!.text).toBe(String(gptDemo.tokens))
  })

  it('<in>/<out> 占位符替换为 number token', () => {
    const claudeDemo = API_DEMOS[2] // claude
    const line = '  "usage": { "input_tokens": <in>, "output_tokens": <out> }'
    const tokens = tokenizeResponseLine(line, claudeDemo)
    const numberTokens = tokens.filter((t) => t.type === 'number')
    expect(numberTokens).toHaveLength(2)
    expect(numberTokens[0].text).toBe(String(Math.floor(claudeDemo.tokens * 0.4)))
    expect(numberTokens[1].text).toBe(String(Math.ceil(claudeDemo.tokens * 0.6)))
  })

  it('空行返回单个 muted 空格 token', () => {
    const tokens = tokenizeResponseLine('  ', gptDemo)
    expect(tokens).toHaveLength(1)
    expect(tokens[0].type).toBe('muted')
  })
})

describe('truncateResponse', () => {
  it('每个 demo 有对应的响应文本', () => {
    for (const demo of API_DEMOS) {
      const text = truncateResponse(demo)
      expect(text).toBeTruthy()
      expect(text).not.toBe('...')
    }
  })
})
