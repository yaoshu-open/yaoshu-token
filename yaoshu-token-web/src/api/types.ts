/**
 * 全局 API 共享契约类型。
 * 后端分页统一走 yue-library PageHelper + PageInfo（见 ai-docs/后端设计/模块设计/设计_分页迁移至PageHelper.md）。
 * request 拦截器对 /api/* 已解包外层信封，业务侧直接消费以下业务数据类型。
 */

/**
 * 后端 PageInfo 标准分页响应（经拦截器解包后的业务数据）。
 * 对应后端 `PageInfo.of(list)`，字段名与 yue-library PageInfo 完全一致。
 */
export interface PageInfo<T> {
  /** 当前页数据数组 */
  list: T[]
  /** 总记录数 */
  total: number
  /** 当前页码（1-based） */
  pageNum: number
  /** 每页条数 */
  pageSize: number
  /** 总页数 */
  pages: number
  /** 是否有下一页 */
  hasNextPage: boolean
}

/**
 * 分页请求参数（URL query）。
 * 后端 PageHelper 从请求参数自动解析 pageNum/pageSize。
 */
export interface PageParams {
  pageNum?: number
  pageSize?: number
}
