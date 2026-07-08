// 用户角色枚举：与后端契约 `/api/user/self` 返回 role 数值字段一一对应
export enum ROLE {
  COMMON = 1,
  ADMIN = 2,
  ROOT = 10
}

// i18n key 派生：消费方组合 `t('roles.' + getRoleLabel(role))` 渲染
export function getRoleLabel(role: number | undefined | null): string {
  if (role === undefined || role === null) return ''
  if (role >= ROLE.ROOT) return 'root'
  if (role >= ROLE.ADMIN) return 'admin'
  return 'common'
}
