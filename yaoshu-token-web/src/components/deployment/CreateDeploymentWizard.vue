<script setup lang="ts">
/**
 * 部署创建 Wizard 容器（MD-C4-create）。
 *
 * 职责：
 * - 聚合表单状态（useDeploymentForm）
 * - 管理步骤导航（ElSteps + 上一步/下一步）
 * - 协调 composable（useNameCheck + useAvailableReplicas + usePriceEstimate）
 * - 加载硬件类型 + 地区列表
 * - 提交创建部署
 *
 * Props/Emits 契约见 `ai-docs/前端设计/模块设计/设计_CreateDeploymentWizard.md` §4。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { createDeployment, getHardwareTypes, getLocations } from '@/api/deployment'
import type {
  DeploymentDetails,
  DeploymentLocation,
  HardwareType
} from '@/api/deployment/types'
import { useDeploymentForm } from '@/composables/deployment/useDeploymentForm'
import { useNameCheck } from '@/composables/deployment/useNameCheck'
import { useAvailableReplicas } from '@/composables/deployment/useAvailableReplicas'
import { usePriceEstimate } from '@/composables/deployment/usePriceEstimate'
import StepBasicInfo from './wizard/StepBasicInfo.vue'
import StepHardware from './wizard/StepHardware.vue'
import StepLocation from './wizard/StepLocation.vue'
import StepAdvanced from './wizard/StepAdvanced.vue'
import StepReview from './wizard/StepReview.vue'

interface Props {
  modelValue: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'created', deployment: DeploymentDetails): void
}>()

const { t } = useI18n()

const { form, resetForm, buildPayload } = useDeploymentForm()

// 名称查重：watch form.resource_private_name
const resourceNameRef = computed(() => form.resource_private_name)
const { status: nameCheckStatus } = useNameCheck(resourceNameRef)

// 硬件类型 + 地区列表
const hardwareTypes = ref<HardwareType[]>([])
const locations = ref<DeploymentLocation[]>([])
const loadingOptions = ref(false)

// 可用副本：依赖 hardware_id + gpus_per_container
const replicasDeps = computed(() => ({
  hardware_id: form.hardware_id,
  gpus_per_container: form.gpus_per_container
}))
const {
  totalAvailable,
  getAvailableByLocation
} = useAvailableReplicas(replicasDeps)

// 价格估算：依赖 hardware_id + gpus_per_container + replica_count + location_ids + duration_hours
const priceDeps = computed(() => ({
  hardware_id: form.hardware_id,
  gpus_per_container: form.gpus_per_container,
  replica_count: form.replica_count,
  location_ids: form.location_ids,
  duration_hours: form.duration_hours
}))
const stepActive = ref(0)
const priceEnabled = computed(() => stepActive.value === 4)
const { estimate: priceEstimate, loading: priceLoading } = usePriceEstimate(priceDeps, priceEnabled)

const submitting = ref(false)

const steps = computed(() => [
  { title: t('deployment.create.steps.basicInfo') },
  { title: t('deployment.create.steps.hardware') },
  { title: t('deployment.create.steps.location') },
  { title: t('deployment.create.steps.advanced') },
  { title: t('deployment.create.steps.review') }
])

// 步骤校验
const canNext = computed<boolean[]>(() => {
  return [
    // Step 1: 名称可用 + 镜像非空 + 时长有效
    nameCheckStatus.value === 'available' && !!form.image_url.trim() && form.duration_hours >= 1,
    // Step 2: 硬件类型 + GPU 数 + 副本数
    form.hardware_id != null && form.gpus_per_container >= 1 && form.replica_count >= 1,
    // Step 3: 至少选择一个地区
    form.location_ids.length > 0,
    // Step 4: 高级选项无强制校验
    true,
    // Step 5: 提交按钮可用性（提交中禁用）
    !submitting.value
  ]
})

function closeDialog(): void {
  emit('update:modelValue', false)
}

function handleOpen(isOpen: boolean): void {
  if (isOpen) {
    stepActive.value = 0
    resetForm()
    void loadOptions()
  }
}

async function loadOptions(): Promise<void> {
  if (hardwareTypes.value.length > 0) return
  loadingOptions.value = true
  try {
    const [hw, loc] = await Promise.all([getHardwareTypes(), getLocations()])
    hardwareTypes.value = hw
    locations.value = loc
  } catch (e) {
    ElMessage.error(t('common.error.title'))
  } finally {
    loadingOptions.value = false
  }
}

function handlePrev(): void {
  if (stepActive.value > 0) stepActive.value--
}

function handleNext(): void {
  if (!canNext.value[stepActive.value]) return
  if (stepActive.value < steps.value.length - 1) stepActive.value++
}

async function handleSubmit(): Promise<void> {
  if (submitting.value) return
  submitting.value = true
  try {
    const payload = buildPayload()
    const deployment = await createDeployment(payload)
    ElMessage.success(t('deployment.create.success'))
    emit('created', deployment)
    closeDialog()
  } catch (e) {
    ElMessage.error(t('deployment.create.failed'))
  } finally {
    submitting.value = false
  }
}

watch(() => props.modelValue, handleOpen)

onMounted(() => {
  // 初始打开时由 watch 触发；此处仅保证组件就绪
})
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="t('deployment.create.title')"
    width="720px"
    top="6vh"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-steps
      :active="stepActive"
      finish-status="success"
      class="wizard-steps"
    >
      <el-step
        v-for="(s, idx) in steps"
        :key="idx"
        :title="s.title"
      />
    </el-steps>

    <div class="wizard-body">
      <StepBasicInfo
        v-show="stepActive === 0"
        :form="form"
        :name-check-status="nameCheckStatus"
      />
      <StepHardware
        v-show="stepActive === 1"
        :form="form"
        :hardware-types="hardwareTypes"
        :total-available="totalAvailable"
        :loading="loadingOptions"
      />
      <StepLocation
        v-show="stepActive === 2"
        :form="form"
        :locations="locations"
        :get-available-by-location="getAvailableByLocation"
        :loading="loadingOptions"
      />
      <StepAdvanced
        v-show="stepActive === 3"
        :form="form"
      />
      <StepReview
        v-show="stepActive === 4"
        :form="form"
        :estimate="priceEstimate"
        :loading="priceLoading"
        :submitting="submitting"
      />
    </div>

    <template #footer>
      <div class="wizard-footer">
        <el-button @click="closeDialog">
          {{ t('deployment.create.cancel') }}
        </el-button>
        <el-button
          v-if="stepActive > 0"
          @click="handlePrev"
        >
          {{ t('deployment.create.prev') }}
        </el-button>
        <el-button
          v-if="stepActive < steps.length - 1"
          type="primary"
          :disabled="!canNext[stepActive]"
          @click="handleNext"
        >
          {{ t('deployment.create.next') }}
        </el-button>
        <el-button
          v-else
          type="primary"
          :loading="submitting"
          :disabled="!canNext[stepActive]"
          @click="handleSubmit"
        >
          {{ submitting ? t('deployment.create.review.submitting') : t('deployment.create.review.submit') }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.wizard-steps {
  margin-bottom: 24px;
}

.wizard-body {
  min-height: 320px;
  max-height: 60vh;
  padding: 0 var(--ys-spacing-1);
  overflow-y: auto;
}

.wizard-footer {
  display: flex;
  gap: var(--ys-spacing-2);
  justify-content: flex-end;
}
</style>
