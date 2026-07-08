<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ElButton, ElCard, ElRadioButton, ElRadioGroup, ElSwitch } from 'element-plus'
import type { SetupFormData } from '@/views/setup/types'

defineProps<{ formData: SetupFormData }>()
const emit = defineEmits<{ next: []; back: [] }>()
const { t } = useI18n()
</script>

<template>
  <div class="setup-step">
    <el-card>
      <h3 class="setup-usage__title">
        {{ t('setup.usageMode.title') }}
      </h3>
      <el-radio-group
        v-model="formData.usageMode"
        class="setup-usage__group"
      >
        <el-radio-button value="external">
          {{ t('setup.usageMode.external') }}
        </el-radio-button>
        <el-radio-button value="self-use">
          {{ t('setup.usageMode.selfUse') }}
        </el-radio-button>
      </el-radio-group>
      <p class="setup-usage__desc">
        {{ formData.usageMode === 'self-use'
          ? t('setup.usageMode.selfUseDesc')
          : t('setup.usageMode.externalDesc')
        }}
      </p>
      <div class="setup-usage__demo">
        <span class="setup-usage__demo-label">{{ t('setup.usageMode.demoSite') }}</span>
        <el-switch v-model="formData.demoSiteEnabled" />
        <span class="setup-usage__demo-hint">{{ t('setup.usageMode.demoSiteHint') }}</span>
      </div>
    </el-card>
    <div class="setup-step__actions">
      <el-button @click="emit('back')">
        {{ t('common.back') }}
      </el-button>
      <el-button
        type="primary"
        @click="emit('next')"
      >
        {{ t('common.next') }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.setup-step__actions {
  display: flex;
  justify-content: space-between;
  margin-top: 24px;
}

.setup-usage__title {
  margin: 0 0 var(--ys-spacing-4);
  font-size: var(--ys-font-size-lg);
}

.setup-usage__group {
  display: flex;
  margin-bottom: 12px;
}

.setup-usage__desc {
  min-height: 40px;
  margin: 0 0 var(--ys-spacing-5);
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-secondary);
}

.setup-usage__demo {
  display: flex;
  gap: var(--ys-spacing-3);
  align-items: center;
  padding: var(--ys-spacing-3);
  background-color: var(--el-fill-color-light);
  border-radius: var(--ys-radius-sm);
}

.setup-usage__demo-label {
  font-size: var(--ys-font-size-base);
  font-weight: 500;
}

.setup-usage__demo-hint {
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}
</style>
