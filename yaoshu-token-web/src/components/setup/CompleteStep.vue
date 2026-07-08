<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ElButton, ElCard, ElDescriptions, ElDescriptionsItem, ElTag } from 'element-plus'
import type { SetupFormData } from '@/views/setup/types'

defineProps<{
  formData: SetupFormData
  submitting: boolean
}>()
const emit = defineEmits<{ submit: []; back: [] }>()
const { t } = useI18n()
</script>

<template>
  <div class="setup-step">
    <el-card>
      <h3 class="setup-complete__title">
        {{ t('setup.complete.confirm') }}
      </h3>
      <el-descriptions
        :column="1"
        border
      >
        <el-descriptions-item
          v-if="formData.username"
          :label="t('setup.admin.username')"
        >
          {{ formData.username }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('setup.usageMode.title')">
          <el-tag>{{ t(`setup.usageMode.${formData.usageMode === 'self-use' ? 'selfUse' : 'external'}`) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('setup.usageMode.demoSite')">
          <el-tag :type="formData.demoSiteEnabled ? 'warning' : 'info'">
            {{ formData.demoSiteEnabled ? t('common.enabled') : t('common.disabled') }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <p class="setup-complete__hint">
        {{ t('setup.complete.hint') }}
      </p>
    </el-card>
    <div class="setup-step__actions">
      <el-button
        :disabled="submitting"
        @click="emit('back')"
      >
        {{ t('common.back') }}
      </el-button>
      <el-button
        type="primary"
        class="el-button--brand"
        :loading="submitting"
        @click="emit('submit')"
      >
        {{ t('setup.complete.submit') }}
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

.setup-complete__title {
  margin: 0 0 var(--ys-spacing-4);
  font-size: var(--ys-font-size-lg);
}

.setup-complete__hint {
  margin: var(--ys-spacing-4) 0 0;
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}
</style>
