<template>
  <ElCard shadow="never">
    <template #header>
      <div class="twofa-card__header">
        <ElIcon :size="18">
          <CircleCheck />
        </ElIcon>
        <span>{{ t('profile.twoFactorAuth') }}</span>
      </div>
    </template>

    <div
      v-loading="loading"
      class="twofa-card__body"
    >
      <div class="twofa-card__status">
        <ElTag
          :type="status?.enabled ? 'success' : 'info'"
          effect="light"
          size="large"
        >
          {{ status?.enabled ? t('profile.twofaEnabled') : t('profile.twofaDisabled') }}
        </ElTag>
        <span
          v-if="status?.enabled"
          class="twofa-card__backup-info"
        >
          {{ t('profile.backupCodesRemaining') }}: {{ status.backupCodesRemaining }}
        </span>
      </div>

      <p class="twofa-card__desc">
        {{ t('profile.twofaDesc') }}
      </p>

      <div class="twofa-card__actions">
        <ElButton
          v-if="!status?.enabled"
          type="primary"
          @click="setupDialogOpen = true"
        >
          {{ t('profile.enableTwofa') }}
        </ElButton>
        <template v-else>
          <ElButton @click="backupDialogOpen = true">
            {{ t('profile.viewBackupCodes') }}
          </ElButton>
          <ElButton
            type="danger"
            plain
            @click="disableDialogOpen = true"
          >
            {{ t('profile.disableTwofa') }}
          </ElButton>
        </template>
      </div>
    </div>

    <!-- Dialogs -->
    <TwoFASetupDialog
      v-model="setupDialogOpen"
      @enabled="handleEnabled"
    />
    <TwoFADisableDialog
      v-model="disableDialogOpen"
      @disabled="handleDisabled"
    />
    <TwoFABackupDialog v-model="backupDialogOpen" />
  </ElCard>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { CircleCheck } from '@element-plus/icons-vue'
import { useTwoFA } from '@/composables/profile/useTwoFA'
import TwoFASetupDialog from './dialogs/TwoFASetupDialog.vue'
import TwoFADisableDialog from './dialogs/TwoFADisableDialog.vue'
import TwoFABackupDialog from './dialogs/TwoFABackupDialog.vue'

withDefaults(defineProps<{ enabled?: boolean }>(), { enabled: true })

const { t } = useI18n()
const { status, loading, fetchStatus } = useTwoFA(true)

const setupDialogOpen = ref(false)
const disableDialogOpen = ref(false)
const backupDialogOpen = ref(false)

function handleEnabled(): void {
  setupDialogOpen.value = false
  fetchStatus()
}

function handleDisabled(): void {
  disableDialogOpen.value = false
  fetchStatus()
}

onMounted(() => {
  fetchStatus()
})
</script>

<style scoped>
.twofa-card__header {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
  font-weight: 600;
}

.twofa-card__body {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);
}

.twofa-card__status {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ys-spacing-4);
  align-items: center;
}

.twofa-card__backup-info {
  font-size: var(--ys-font-size-base);
  color: var(--el-text-color-secondary);
}

.twofa-card__desc {
  margin: 0;
  font-size: var(--ys-font-size-base);
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.twofa-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ys-spacing-3);
}
</style>
