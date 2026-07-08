<template>
  <ElCard shadow="never">
    <template #header>
      <div class="security-card__header">
        <ElIcon :size="18">
          <Lock />
        </ElIcon>
        <span>{{ t('profile.securitySettings') }}</span>
      </div>
    </template>

    <div class="security-card__list">
      <!-- 修改密码 -->
      <div class="security-card__row">
        <div class="security-card__row-info">
          <ElIcon
            :size="20"
            class="security-card__row-icon"
          >
            <Key />
          </ElIcon>
          <div class="security-card__row-text">
            <span class="security-card__row-name">{{ t('profile.changePassword') }}</span>
            <span class="security-card__row-status">{{ t('profile.changePasswordDesc') }}</span>
          </div>
        </div>
        <ElButton
          size="small"
          @click="passwordDialogOpen = true"
        >
          {{ t('profile.modify') }}
        </ElButton>
      </div>

      <!-- Access Token -->
      <div class="security-card__row">
        <div class="security-card__row-info">
          <ElIcon
            :size="20"
            class="security-card__row-icon"
          >
            <Key />
          </ElIcon>
          <div class="security-card__row-text">
            <span class="security-card__row-name">{{ t('profile.accessToken') }}</span>
            <span class="security-card__row-status">
              {{ profile?.accessToken ? t('profile.tokenGenerated') : t('profile.tokenNotSet') }}
            </span>
          </div>
        </div>
        <ElButton
          size="small"
          @click="tokenDialogOpen = true"
        >
          {{ t('profile.manage') }}
        </ElButton>
      </div>

      <!-- 删除账号 -->
      <div class="security-card__row security-card__row--danger">
        <div class="security-card__row-info">
          <ElIcon
            :size="20"
            class="security-card__row-icon"
          >
            <Delete />
          </ElIcon>
          <div class="security-card__row-text">
            <span class="security-card__row-name">{{ t('profile.deleteAccount') }}</span>
            <span class="security-card__row-status">{{ t('profile.deleteAccountDesc') }}</span>
          </div>
        </div>
        <ElButton
          size="small"
          type="danger"
          plain
          @click="deleteDialogOpen = true"
        >
          {{ t('profile.delete') }}
        </ElButton>
      </div>
    </div>

    <!-- Dialogs -->
    <ChangePasswordDialog
      v-model="passwordDialogOpen"
      @changed="handleChanged"
    />
    <AccessTokenDialog
      v-model="tokenDialogOpen"
      :profile="profile"
      @changed="handleChanged"
    />
    <DeleteAccountDialog v-model="deleteDialogOpen" />
  </ElCard>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Lock, Key, Delete } from '@element-plus/icons-vue'
import ChangePasswordDialog from './dialogs/ChangePasswordDialog.vue'
import AccessTokenDialog from './dialogs/AccessTokenDialog.vue'
import DeleteAccountDialog from './dialogs/DeleteAccountDialog.vue'
import type { UserProfile } from '@/api/profile/types'

defineProps<{
  profile: UserProfile | null
}>()

const emit = defineEmits<{
  update: []
}>()

const { t } = useI18n()
const passwordDialogOpen = ref(false)
const tokenDialogOpen = ref(false)
const deleteDialogOpen = ref(false)

function handleChanged(): void {
  emit('update')
}
</script>

<style scoped>
.security-card__header {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
  font-weight: 600;
}

.security-card__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--ys-spacing-3) 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.security-card__row:last-child {
  border-bottom: none;
}

.security-card__row-info {
  display: flex;
  gap: var(--ys-spacing-3);
  align-items: center;
}

.security-card__row-icon {
  color: var(--el-text-color-secondary);
}

.security-card__row-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.security-card__row-name {
  font-size: var(--ys-font-size-base);
  font-weight: 500;
}

.security-card__row--danger .security-card__row-name {
  color: var(--el-color-danger);
}

.security-card__row-status {
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}
</style>
