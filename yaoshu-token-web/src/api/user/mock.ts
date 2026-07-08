/**
 * 用户管理 API Mock 数据。
 */
import type { GetUsersParams, SearchUsersParams, User, UserFormData, UsersListData } from './types'

function createMockUser(overrides: Partial<User> = {}): User {
  return {
    id: 1,
    username: 'admin',
    displayName: 'Administrator',
    quota: 5000000,
    usedQuota: 1200000,
    requestCount: 342,
    group: 'default',
    status: 1,
    role: 100,
    email: 'admin@example.com',
    createdAt: 1700000000,
    lastLoginAt: 1700100000,
    ...overrides,
  }
}

const MOCK_USERS: User[] = Array.from({ length: 25 }, (_, i) =>
  createMockUser({
    id: i + 1,
    username: ['admin', 'user1', 'user2', 'tester', 'guest'][i % 5] + (i > 4 ? `_${i}` : ''),
    displayName: ['Administrator', 'Regular User', 'Power User', 'Tester', 'Guest'][i % 5],
    quota: [5000000, 500000, 1000000, 200000, 100000][i % 5],
    usedQuota: Math.floor(Math.random() * 500000),
    requestCount: Math.floor(Math.random() * 1000),
    status: i % 4 === 0 ? 2 : 1,
    role: i === 0 ? 100 : (i % 5 === 0 ? 10 : 1),
    group: ['default', 'premium', 'internal', 'testing'][i % 4],
    createdAt: 1700000000 + i * 86400,
    lastLoginAt: 1700000000 + i * 3600,
  })
)

function delay(ms = 200): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export async function mockGetUsers(params: GetUsersParams = {}): Promise<UsersListData> {
  await delay()
  const pageNum = params.pageNum ?? 1
  const pageSize = params.pageSize ?? 20
  const total = MOCK_USERS.length
  const start = (pageNum - 1) * pageSize
  return { list: MOCK_USERS.slice(start, start + pageSize), total, pageNum, pageSize, pages: Math.ceil(total / pageSize), hasNextPage: pageNum * pageSize < total }
}

export async function mockSearchUsers(params: SearchUsersParams): Promise<UsersListData> {
  await delay()
  let list = [...MOCK_USERS]
  if (params.keyword) {
    const kw = params.keyword.toLowerCase()
    list = list.filter((u) => u.username.toLowerCase().includes(kw) || u.displayName.toLowerCase().includes(kw))
  }
  if (params.group && params.group !== 'all') list = list.filter((u) => u.group === params.group)
  if (params.role && params.role !== 'all') list = list.filter((u) => String(u.role) === params.role)
  if (params.status && params.status !== 'all') list = list.filter((u) => String(u.status) === params.status)
  const pageNum = params.pageNum ?? 1
  const pageSize = params.pageSize ?? 20
  const total = list.length
  const start = (pageNum - 1) * pageSize
  return { list: list.slice(start, start + pageSize), total, pageNum, pageSize, pages: Math.ceil(total / pageSize), hasNextPage: pageNum * pageSize < total }
}

export async function mockGetUser(id: number): Promise<User> {
  await delay()
  return MOCK_USERS.find((u) => u.id === id) ?? MOCK_USERS[0]
}

export async function mockCreateUser(_data: UserFormData): Promise<void> { await delay(300) }
export async function mockUpdateUser(_data: UserFormData): Promise<void> { await delay(300) }
export async function mockDeleteUser(): Promise<void> { await delay(300) }
export async function mockManageUser(): Promise<void> { await delay(300) }
export async function mockAdjustQuota(): Promise<void> { await delay(300) }
