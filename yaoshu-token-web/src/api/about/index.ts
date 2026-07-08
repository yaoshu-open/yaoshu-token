// 后端通过 /api/about 返回一个字符串（Markdown / HTML / URL），前端按类型渲染
import { request } from '@/utils/request'

export function getAboutContent(): Promise<string> {
  return request.get<string>('/api/about')
}
