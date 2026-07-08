<script setup lang="ts">
/**
 * 容器选择器。
 */
import { useI18n } from 'vue-i18n'
import { ALL_CONTAINERS } from '@/api/deployment/constants'
import type { DeploymentContainer } from '@/api/deployment/types'

defineProps<{
  containers: DeploymentContainer[]
  modelValue: string
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const { t } = useI18n()
</script>

<template>
  <ElSelect
    :model-value="modelValue"
    :loading="loading"
    :placeholder="t('deployment.logs.selectContainer')"
    style="width: 100%"
    @update:model-value="(v) => emit('update:modelValue', String(v ?? ALL_CONTAINERS))"
  >
    <ElOption
      :value="ALL_CONTAINERS"
      :label="t('deployment.logs.allContainers')"
    />
    <ElOption
      v-for="ctr in containers"
      :key="ctr.container_id"
      :value="ctr.container_id"
      :label="ctr.container_id"
    >
      <div class="deployment-selector__option">
        <span class="deployment-selector__option-id">{{ ctr.container_id }}</span>
        <span class="deployment-selector__option-meta">
          {{ ctr.brand_name || 'IO.NET' }}{{ ctr.hardware ? ` · ${ctr.hardware}` : '' }}
        </span>
      </div>
    </ElOption>
  </ElSelect>
</template>

<style scoped lang="scss">
.deployment-selector__option {
  display: flex;
  flex-direction: column;

  &-id {
    font-family: var(--el-font-family-monospace, monospace);
    font-size: var(--el-font-size-small);
  }

  &-meta {
    font-size: var(--el-font-size-extra-small);
    color: var(--el-text-color-secondary);
  }
}
</style>
