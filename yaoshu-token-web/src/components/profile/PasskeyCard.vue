<script setup lang="ts">
// Passkey 管理卡片：对接 usePasskeyManagement composable
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElCard, ElTag, ElButton, ElIcon } from 'element-plus'
import { Key } from '@element-plus/icons-vue'
import { usePasskeyManagement } from '@/composables/auth/usePasskeyManagement'

const { t } = useI18n()
const { status, loading, registering, removing, supported, enabled, lastUsed, register, remove } =
  usePasskeyManagement()

async function handleRegister(): Promise<void> {
  await register()
}

async function handleRemove(): Promise<void> {
  try {
    const { ElMessageBox } = await import('element-plus')
    await ElMessageBox.confirm(t('profile.passkeyRemoveConfirm'), t('common.confirm'), {
      type: 'warning'
    })
    await remove()
  } catch {
    // 用户取消删除
  }
}

const backupTagType = computed(() =>
  status.value?.backupState ? 'success' : 'warning'
)
</script>

<template>
  <ElCard shadow="never">
    <template #header>
      <div class="passkey-card__header">
        <ElIcon :size="18">
          <Key />
        </ElIcon>
        <span>{{ t('profile.passkeyManagement') }}</span>
      </div>
    </template>

    <div
      v-loading="loading"
      class="passkey-card__body"
    >
      <div class="passkey-card__status">
        <ElTag
          :type="enabled ? 'success' : 'info'"
          effect="light"
          size="large"
        >
          {{ enabled ? t('profile.passkeyEnabled') : t('profile.passkeyDisabled') }}
        </ElTag>
        <template v-if="enabled && status?.backupEligible !== undefined">
          <ElTag
            :type="backupTagType"
            effect="plain"
            size="small"
          >
            {{ status.backupState ? t('profile.passkeyBackedUp') : t('profile.passkeyNotBackedUp') }}
          </ElTag>
        </template>
        <span
          v-if="lastUsed"
          class="passkey-card__last-used"
        >
          {{ t('profile.passkeyLastUsed') }}: {{ lastUsed }}
        </span>
      </div>

      <p class="passkey-card__desc">
        {{ t('profile.passkeyDesc') }}
      </p>

      <ElAlert
        v-if="!supported"
        :title="t('auth.passkey.notSupported')"
        type="warning"
        :closable="false"
        show-icon
      />

      <div class="passkey-card__actions">
        <ElButton
          v-if="!enabled"
          type="primary"
          :loading="registering"
          :disabled="!supported"
          @click="handleRegister"
        >
          {{ t('profile.passkeyRegister') }}
        </ElButton>
        <ElButton
          v-else
          type="danger"
          plain
          :loading="removing"
          @click="handleRemove"
        >
          {{ t('profile.passkeyRemove') }}
        </ElButton>
      </div>
    </div>
  </ElCard>
</template>

<style scoped>
.passkey-card__header {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
  font-weight: 600;
}

.passkey-card__body {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);
}

.passkey-card__status {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ys-spacing-3);
  align-items: center;
}

.passkey-card__last-used {
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-secondary);
}

.passkey-card__desc {
  margin: 0;
  font-size: var(--ys-font-size-base);
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.passkey-card__actions {
  display: flex;
  gap: var(--ys-spacing-3);
}
</style>
