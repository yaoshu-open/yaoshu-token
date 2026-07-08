import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/store/modules/auth'
import { ROLE } from '@/utils/roles'

/**
 * 用户权限判定。
 *
 * 行为：依赖 authStore.userInfo.role 数值判断，role 缺失视为最低权限（COMMON）
 */
export function useUserPermissions() {
  const authStore = useAuthStore()
  const { userInfo } = storeToRefs(authStore)

  const role = computed<number>(() => {
    const r = userInfo.value?.role
    return typeof r === 'number' ? r : ROLE.COMMON
  })

  const isAdmin = computed<boolean>(() => role.value >= ROLE.ADMIN)
  const isRoot = computed<boolean>(() => role.value >= ROLE.ROOT)

  function hasRole(required: number): boolean {
    return role.value >= required
  }

  return { role, isAdmin, isRoot, hasRole }
}
