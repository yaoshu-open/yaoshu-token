<script setup lang="ts">
/**
 * Step 2: 硬件配置（硬件类型 + GPU 数 + 副本数）。
 * 硬件类型列表由 Wizard 容器加载后透传。
 * 可用副本数由 useAvailableReplicas composable 计算，透传 totalAvailable。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DeploymentFormState } from '@/composables/deployment/useDeploymentForm'
import type { HardwareType } from '@/api/deployment/types'

interface Props {
  form: DeploymentFormState
  hardwareTypes: HardwareType[]
  totalAvailable: number
  loading?: boolean
}

const props = defineProps<Props>()
const { t } = useI18n()

const hardwareOptions = computed(() =>
  props.hardwareTypes.map((h) => ({
    value: h.id,
    label: h.display_name || `${h.brand_name ?? ''} ${h.name}`.trim(),
    gpu_count: h.gpu_count
  }))
)

const selectedHardware = computed(() =>
  props.hardwareTypes.find((h) => h.id === props.form.hardware_id)
)

const maxGpuCount = computed(() => selectedHardware.value?.gpu_count ?? 8)
</script>

<template>
  <el-form
    label-position="top"
    class="step-hardware"
  >
    <el-form-item :label="t('deployment.create.hardware.type')">
      <el-select
        v-model="props.form.hardware_id"
        :placeholder="t('deployment.create.hardware.typePlaceholder')"
        :loading="props.loading"
        filterable
      >
        <el-option
          v-for="opt in hardwareOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </el-form-item>

    <el-form-item :label="t('deployment.create.hardware.gpuCount')">
      <el-input-number
        v-model="props.form.gpus_per_container"
        :min="1"
        :max="maxGpuCount"
        :step="1"
      />
      <div
        v-if="selectedHardware"
        class="form-hint"
      >
        {{ t('deployment.create.hardware.type') }}: {{ selectedHardware.name }} ({{ selectedHardware.gpu_count }} GPU)
      </div>
    </el-form-item>

    <el-form-item :label="t('deployment.create.hardware.replicaCount')">
      <el-input-number
        v-model="props.form.replica_count"
        :min="1"
        :step="1"
      />
    </el-form-item>

    <el-form-item
      v-if="props.form.hardware_id"
      :label="t('deployment.create.hardware.availableReplicas')"
    >
      <el-tag :type="props.totalAvailable > 0 ? 'success' : 'danger'">
        {{ t('deployment.create.hardware.totalAvailable', { count: props.totalAvailable }) }}
      </el-tag>
    </el-form-item>
  </el-form>
</template>

<style scoped lang="scss">
.step-hardware {
  .form-hint {
    margin-top: 4px;
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-secondary);
  }
}
</style>
