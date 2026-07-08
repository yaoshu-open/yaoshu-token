<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElDropdown, ElDropdownMenu, ElDropdownItem, ElAvatar } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/store/modules/auth'
import { useUserDisplay } from '@/composables/useUserDisplay'
import { PROFILE_ICONS } from '@/components/layout/icons'
import ConfirmDialog from './ConfirmDialog.vue'

interface Props {
  size?: 'small' | 'default' | 'large'
}

withDefaults(defineProps<Props>(), {
  size: 'default'
})

const emit = defineEmits<{
  (e: 'sign-out'): void
}>()

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const { userInfo } = storeToRefs(authStore)
const display = useUserDisplay(() => userInfo.value)

// 退出确认对话框
const signOutOpen = ref(false)

function goProfile() {
  router.push('/profile')
}
function goWallet() {
  router.push('/wallet')
}
function openSignOut() {
  signOutOpen.value = true
}
function confirmSignOut() {
  emit('sign-out')
  authStore.clearAuthToken()
  signOutOpen.value = false
  router.push('/sign-in')
}
</script>

<template>
  <ElDropdown trigger="click">
    <button
      type="button"
      class="profile-dropdown__trigger"
      :aria-label="t('layout.header.profile')"
    >
      <ElAvatar :size="28">
        {{ display.initials }}
      </ElAvatar>
    </button>
    <template #dropdown>
      <ElDropdownMenu>
        <!-- 用户头部信息 -->
        <div class="profile-dropdown__header">
          <ElAvatar :size="36">
            {{ display.initials }}
          </ElAvatar>
          <div class="profile-dropdown__meta">
            <div class="profile-dropdown__name">
              {{ display.displayName }}
            </div>
            <div class="profile-dropdown__sub">
              <span
                v-if="display.roleLabel"
                class="profile-dropdown__role"
              >{{ display.roleLabel }}</span>
              <span
                v-if="display.secondaryText"
                class="profile-dropdown__secondary"
              >{{ display.secondaryText }}</span>
            </div>
          </div>
        </div>

        <ElDropdownItem
          divided
          @click="goProfile"
        >
          <i :class="PROFILE_ICONS.profile" />
          <span>{{ t('user.menu.profile') }}</span>
        </ElDropdownItem>
        <ElDropdownItem @click="goWallet">
          <i :class="PROFILE_ICONS.wallet" />
          <span>{{ t('user.menu.wallet') }}</span>
        </ElDropdownItem>
        <ElDropdownItem
          divided
          @click="openSignOut"
        >
          <i :class="PROFILE_ICONS.signOut" />
          <span>{{ t('user.menu.signOut') }}</span>
        </ElDropdownItem>
      </ElDropdownMenu>
    </template>
  </ElDropdown>

  <ConfirmDialog
    v-model="signOutOpen"
    :title="t('user.menu.signOutConfirmTitle')"
    :description="t('user.menu.signOutConfirmDesc')"
    :destructive="true"
    :confirm-text="t('user.menu.signOut')"
    :cancel-text="t('common.cancel')"
    @confirm="confirmSignOut"
  />
</template>

<style scoped lang="scss">
.profile-dropdown {
  &__trigger {
    display: inline-flex;
    align-items: center;
    padding: 2px;
    cursor: pointer;
    background: transparent;
    border: 0;
    border-radius: 50%;
    transition: background-color 0.2s;

    &:hover {
      background: var(--el-fill-color-light);
    }
  }

  &__header {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
    padding: var(--ys-spacing-2) var(--ys-spacing-4) var(--ys-spacing-3);
  }

  &__meta {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  &__name {
    max-width: 180px;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: var(--el-font-size-base);
    font-weight: 500;
    color: var(--el-text-color-primary);
    white-space: nowrap;
  }

  &__sub {
    display: flex;
    gap: var(--ys-spacing-1);
    align-items: center;
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-secondary);
  }

  &__role {
    flex-shrink: 0;
  }

  &__secondary {
    max-width: 140px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
