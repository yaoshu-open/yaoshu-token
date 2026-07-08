import { request } from '@/utils/request'
import type { SystemStatus } from './types'

// 获取系统状态：/api/status（系统名/Logo/品牌/可用 OAuth/passkey 启用/币种 等）
// request 拦截已对 /api/* 做 Result 解包（flag 判定 + data 提取），此处直接返回业务数据
export function getStatus() {
  return request.get<SystemStatus>('/api/status')
}

// 获取系统公告（管理员设置的全站通知）：/api/notice
// 返回 data 字段（已被 request 拦截器从 Result 中解包），即公告字符串内容；
// flag=false 时拦截器会抛错并触发全局 ElMessage，caller 不会拿到假成功值（红线 21）
export function getNotice() {
  return request.get<string>('/api/notice')
}
