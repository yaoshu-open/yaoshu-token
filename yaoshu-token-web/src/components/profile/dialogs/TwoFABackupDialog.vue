<template>
  <ElDialog
    :model-value="modelValue"
    :title="t('profile.backupCodes')"
    width="440px"
    @update:model-value="handleToggle"
  >
    <div
      v-loading="loading"
      class="twofa-backup__body"
    >
      <ElAlert
        :title="t('profile.backupCodesWarning')"
        type="warning"
        :closable="false"
        show-icon
        class="twofa-backup__alert"
      />
      <div
        v-if="codes.length > 0"
        class="twofa-backup__codes"
      >
        <div
          v-for="code in codes"
          :key="code"
          class="twofa-backup__code"
        >
          {{ code }}
        </div>
      </div>
      <ElEmpty
        v-else
        :description="t('profile.noBackupCodes')"
      />
    </div>

    <template #footer>
      <ElButton @click="$emit('update:modelValue', false)">
        {{ t('common.close') }}
      </ElButton>
      <ElButton
        :disabled="codes.length === 0"
        @click="downloadCodes"
      >
        <ElIcon><Download /></ElIcon>
        {{ t('profile.downloadBackupCodes') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Download } from '@element-plus/icons-vue'
import { useTwoFA } from '@/composables/profile/useTwoFA'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const { t } = useI18n()
const { backupCodes, backupLoading, fetchBackupCodes } = useTwoFA(true)

const codes = backupCodes
const loading = backupLoading

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      fetchBackupCodes()
    }
  }
)

function downloadCodes(): void {
  const content = codes.value.join('\n')
  const blob = new Blob([content], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '2fa-backup-codes.txt'
  a.click()
  URL.revokeObjectURL(url)
}

function handleToggle(val: boolean): void {
  emit('update:modelValue', val)
}
</script>

<style scoped>
.twofa-backup__body {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);
}

.twofa-backup__alert {
  margin-bottom: 0;
}

.twofa-backup__codes {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--ys-spacing-2);
  padding: var(--ys-spacing-3);
  font-family: monospace;
  font-size: var(--ys-font-size-base);
  background: var(--el-fill-color-light);
  border-radius: var(--ys-radius-md);
}

.twofa-backup__code {
  text-align: center;
  letter-spacing: 1px;
}
</style>
