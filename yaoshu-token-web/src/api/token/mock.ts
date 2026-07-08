/**
 * 令牌管理 API Mock 数据（DEV + VITE_TOKEN_MOCK=true 时启用）。
 */
import type { GetTokensParams, SearchTokensParams, Token, TokensListData } from './types'

const MOCK_TOKENS: Token[] = Array.from({ length: 20 }, (_, i) => ({
  id: i + 1,
  name: `Token-${String.fromCharCode(65 + (i % 26))}${i}`,
  key: `sk-yaoshu-${Math.random().toString(36).slice(2, 18)}${i}`,
  status: [1, 1, 1, 2, 3][i % 5],
  remainQuota: [5000000, 10000000, 0, 3000000, 0][i % 5],
  usedQuota: [1200000, 500000, 5000000, 200000, 0][i % 5],
  unlimitedQuota: i % 7 === 0,
  expiredTime: i % 3 === 0 ? -1 : Math.floor(Date.now() / 1000) + 86400 * 30 * (i + 1),
  createdTime: 1700000000 + i * 86400,
  accessedTime: 1700000000 + i * 3600,
  group: ['default', 'premium', 'internal', ''][i % 4],
  crossGroupRetry: i % 2 === 0,
  modelLimitsEnabled: i % 3 === 0,
  modelLimits: i % 3 === 0 ? 'gpt-4o,claude-3-5-sonnet' : '',
  allowIps: i % 4 === 0 ? '192.168.1.1,10.0.0.1' : '',
}))

function delay(ms = 200): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export async function mockGetTokens(params: GetTokensParams = {}): Promise<TokensListData> {
  await delay()
  const pageNum = params.pageNum ?? 1
  const pageSize = params.pageSize ?? 10
  const start = (pageNum - 1) * pageSize
  const list = MOCK_TOKENS.slice(start, start + pageSize)
  return { list, total: MOCK_TOKENS.length, pageNum, pageSize, pages: Math.ceil(MOCK_TOKENS.length / pageSize), hasNextPage: pageNum * pageSize < MOCK_TOKENS.length }
}

export async function mockSearchTokens(params: SearchTokensParams): Promise<TokensListData> {
  await delay()
  const pageNum = params.pageNum ?? 1
  const pageSize = params.pageSize ?? 10
  let list = [...MOCK_TOKENS]
  if (params.keyword) {
    const kw = params.keyword.toLowerCase()
    list = list.filter((t) => t.name.toLowerCase().includes(kw))
  }
  if (params.token) {
    const tk = params.token.toLowerCase()
    list = list.filter((t) => t.key.toLowerCase().includes(tk))
  }
  const total = list.length
  const start = (pageNum - 1) * pageSize
  list = list.slice(start, start + pageSize)
  return { list, total, pageNum, pageSize, pages: Math.ceil(total / pageSize), hasNextPage: pageNum * pageSize < total }
}

export async function mockGetToken(id: number): Promise<Token> {
  await delay()
  const token = MOCK_TOKENS.find((t) => t.id === id)
  if (!token) throw new Error('Token not found')
  return token
}

export async function mockGetTokenKey(id: number): Promise<string> {
  await delay()
  const token = MOCK_TOKENS.find((t) => t.id === id)
  if (!token) throw new Error('Token not found')
  return token.key
}

export async function mockCreateToken(): Promise<void> {
  await delay(300)
}

export async function mockUpdateToken(): Promise<void> {
  await delay(300)
}

export async function mockDeleteToken(): Promise<void> {
  await delay(300)
}

export async function mockBatchDeleteTokens(): Promise<void> {
  await delay(400)
}
