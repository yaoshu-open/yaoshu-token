import { request } from '@/utils/request'
import type { SetupRequest, SetupStatus } from './types'

// 获取系统初始化状态（公开接口，无需鉴权）
export function getSetupStatus() {
  return request.get<SetupStatus>('/api/setup')
}

// 提交系统初始化（interactive 模式创建管理员账号）
// 成功返回 data:null；已初始化时 flag:false → 拦截器 reject + ElMessage
export function setupSystem(data: SetupRequest) {
  return request.post<null>('/api/setup', data)
}
