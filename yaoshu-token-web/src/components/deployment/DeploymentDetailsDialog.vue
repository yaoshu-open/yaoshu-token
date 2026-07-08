<script setup lang="ts">
/**
 * 部署详情增强 Dialog (T-MD-01)。
 * 集成 6 个子 Card：BasicInfo / Hardware / ContainerConfig / ContainerList / Location / Cost / Timeline。
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDeploymentDetails } from '@/composables/deployment/useDeploymentDetails'
import type { DeploymentListItem } from '@/api/deployment/types'
import LoadingState from '@/components/LoadingState.vue'
import ErrorState from '@/components/ErrorState.vue'
import DeploymentBasicInfoCard from './DeploymentBasicInfoCard.vue'
import DeploymentHardwareCard from './DeploymentHardwareCard.vue'
import DeploymentContainerConfigCard from './DeploymentContainerConfigCard.vue'
import DeploymentContainerListCard from './DeploymentContainerListCard.vue'
import DeploymentLocationCard from './DeploymentLocationCard.vue'
import DeploymentCostCard from './DeploymentCostCard.vue'
import DeploymentTimelineCard from './DeploymentTimelineCard.vue'

const props = defineProps<{
  modelValue: boolean
  deployment: DeploymentListItem | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'refresh'): void
}>()

const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const idRef = computed(() => (props.deployment?.id ?? null) as string | number | null)
const {
  details,
  containers,
  loading,
  containersLoading,
  error,
  refresh
} = useDeploymentDetails(() => idRef.value)

const selectContainer = ref<string | null>(null)

watch(visible, (v) => {
  if (v) void refresh()
}, { immediate: true })

function handleOpenUrl(url: string): void {
  window.open(url, '_blank', 'noopener,noreferrer')
}
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="t('deployment.details.title')"
    width="800"
    :close-on-click-modal="false"
    align-center
    destroy-on-close
  >
    <template #header>
      <div class="deployment-dialog-header">
        <i class="i-ep-info-filled" />
        <span class="deployment-dialog-header__title">{{ t('deployment.details.title') }}</span>
      </div>
    </template>

    <div
      v-if="loading && !details"
      class="deployment-dialog__loading"
    >
      <LoadingState :message="t('deployment.details.loading')" />
    </div>

    <ErrorState
      v-else-if="error"
      :title="t('common.error.title')"
      :message="error.message"
      @retry="refresh"
    />

    <div
      v-else-if="details"
      class="deployment-dialog__body"
    >
      <DeploymentBasicInfoCard :details="details" />
      <DeploymentHardwareCard :details="details" />
      <DeploymentContainerConfigCard
        v-if="details.container_config"
        :config="details.container_config"
      />
      <DeploymentContainerListCard
        :containers="containers"
        :loading="containersLoading"
        :selected="selectContainer"
        @select="(cId) => (selectContainer = cId)"
        @open-url="handleOpenUrl"
      />
      <DeploymentLocationCard
        v-if="details.locations && details.locations.length > 0"
        :locations="details.locations"
      />
      <DeploymentCostCard :details="details" />
      <DeploymentTimelineCard :details="details" />
    </div>
  </ElDialog>
</template>

<style scoped lang="scss">
.deployment-dialog {
  &-header {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;

    &__title {
      font-size: var(--el-font-size-large);
      font-weight: 500;
    }
  }

  &__loading {
    min-height: 200px;
  }

  &__body {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-4);
    max-height: 60vh;
    padding-right: 4px;
    overflow-y: auto;
  }
}
</style>
