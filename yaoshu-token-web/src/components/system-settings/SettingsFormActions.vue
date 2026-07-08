<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Check, RefreshLeft } from '@element-plus/icons-vue'

interface SettingsFormActionsProps {
  saving?: boolean
  dirty?: boolean
}

defineProps<SettingsFormActionsProps>()
const emit = defineEmits<{ (e: 'save'): void; (e: 'reset'): void }>()
const { t } = useI18n()
</script>

<template>
  <div class="settings-form-actions">
    <ElTag
      v-if="dirty"
      type="warning"
      size="small"
      effect="light"
    >
      {{ t('systemSettings.unsavedChanges') }}
    </ElTag>
    <ElButton
      :icon="RefreshLeft"
      size="small"
      :disabled="saving || !dirty"
      @click="emit('reset')"
    >
      {{ t('common.reset') }}
    </ElButton>
    <ElButton
      type="primary"
      :icon="Check"
      size="small"
      :loading="saving"
      @click="emit('save')"
    >
      {{ t('common.save') }}
    </ElButton>
  </div>
</template>

<style scoped lang="scss">
.settings-form-actions {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
}
</style>
