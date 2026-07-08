import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { getRoleLabel } from '@/utils/roles'
import type { UserInfo } from '@/store/modules/auth'

/**
 * 用户显示信息派生（显示名/二级标识/头像首字母/角色标签）。
 */
export function useUserDisplay(user: () => UserInfo | null | undefined) {
  const { t } = useI18n()

  const display = computed(() => {
    const u = user()
    if (!u) {
      return {
        displayName: t('common.user'),
        secondaryText: '',
        initials: 'U',
        roleLabel: ''
      }
    }

    const displayName =
      (typeof u.displayName === 'string' && u.displayName) ||
      (typeof u.username === 'string' && u.username) ||
      t('common.user')

    // 二级标识：邮箱优先，否则按 OAuth 平台 ID 顺序
    let secondaryText = ''
    if (typeof u.email === 'string' && u.email) {
      secondaryText = u.email
    } else if (u.githubId) {
      secondaryText = `GitHub ID: ${u.githubId}`
    } else if (u.oidcId) {
      secondaryText = `OIDC ID: ${u.oidcId}`
    } else if (u.wechatId) {
      secondaryText = `WeChat ID: ${u.wechatId}`
    } else if (u.telegramId) {
      secondaryText = `Telegram ID: ${u.telegramId}`
    } else if (u.linuxDoId) {
      secondaryText = `LinuxDO ID: ${u.linuxDoId}`
    } else if (typeof u.username === 'string' && u.username) {
      secondaryText = u.username
    }

    const initials = displayName
      .split(/\s+/)
      .map((n) => n[0])
      .filter(Boolean)
      .join('')
      .toUpperCase()
      .slice(0, 2)

    const labelKey = getRoleLabel(u.role)
    const roleLabel = labelKey ? t(`roles.${labelKey}`) : ''

    return { displayName, secondaryText, initials, roleLabel }
  })

  return display
}
