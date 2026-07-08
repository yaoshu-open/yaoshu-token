/**
 * 多密钥管理纯工具函数。
 */
import {
  MULTI_KEY_STATUS_CONFIG,
  MULTI_KEY_CONFIRM_MESSAGES
} from '@/api/channel/constants'
import type { MultiKeyConfirmAction } from '@/api/channel/types'

type StatusVariant = 'success' | 'neutral' | 'danger' | 'warning' | 'info'

/** 查表返回多密钥状态对应的徽章配置 */
export function getMultiKeyStatusConfig(status: number): {
  variant: StatusVariant
  label: string
} {
  return (
    MULTI_KEY_STATUS_CONFIG[status as keyof typeof MULTI_KEY_STATUS_CONFIG] ?? {
      variant: 'neutral' as const,
      label: 'channel.multiKey.statusUnknown'
    }
  )
}

/** 根据 action 类型返回确认提示 i18n key */
export function getMultiKeyConfirmMessage(
  action: MultiKeyConfirmAction | null
): string {
  if (!action) return ''
  switch (action.type) {
    case 'delete':
      return MULTI_KEY_CONFIRM_MESSAGES.DELETE
    case 'enable':
      return MULTI_KEY_CONFIRM_MESSAGES.ENABLE
    case 'disable':
      return MULTI_KEY_CONFIRM_MESSAGES.DISABLE
    case 'enable-all':
      return MULTI_KEY_CONFIRM_MESSAGES.ENABLE_ALL
    case 'disable-all':
      return MULTI_KEY_CONFIRM_MESSAGES.DISABLE_ALL
    case 'delete-disabled':
      return MULTI_KEY_CONFIRM_MESSAGES.DELETE_DISABLED
    default:
      return ''
  }
}

/** 判断操作是否为破坏性（delete/delete-disabled/disable-all） */
export function isDestructiveAction(
  action: MultiKeyConfirmAction | null
): boolean {
  if (!action) return false
  return (
    action.type === 'delete' ||
    action.type === 'delete-disabled' ||
    action.type === 'disable-all'
  )
}
