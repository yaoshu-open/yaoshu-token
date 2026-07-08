<script setup lang="ts">
/**
 * 部署日志增强 Dialog (T-MD-02)。
 * 集成：ContainerSelector + LogSearchBar + ContainerDetails + LogList。
 */
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useDeploymentContainers } from '@/composables/deployment/useDeploymentContainers'
import { useDeploymentLogs } from '@/composables/deployment/useDeploymentLogs'
import { useCopyToClipboard } from '@/composables/useCopyToClipboard'
import { ALL_CONTAINERS } from '@/api/deployment/constants'
import type { DeploymentListItem } from '@/api/deployment/types'
import LoadingState from '@/components/LoadingState.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import DeploymentContainerSelector from './DeploymentContainerSelector.vue'
import DeploymentLogSearchBar from './DeploymentLogSearchBar.vue'
import DeploymentContainerDetails from './DeploymentContainerDetails.vue'
import DeploymentLogList from './DeploymentLogList.vue'

const props = defineProps<{
  modelValue: boolean
  deployment: DeploymentListItem | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const idRef = computed(() => (props.deployment?.id ?? null) as string | number | null)

const {
  containers,
  selectedContainerId,
  selectedContainer,
  containerDetails,
  containersLoading,
  containerDetailsLoading,
  fetchContainers,
  fetchContainerDetails,
  selectContainer,
  getStatusConfig
} = useDeploymentContainers(() => idRef.value)

const logListRef = ref<InstanceType<typeof DeploymentLogList> | null>(null)

const {
  logLines,
  filteredLogs,
  loading,
  error,
  searchTerm,
  autoRefresh,
  following,
  streamFilter,
  lastUpdatedAt,
  isAtBottom,
  fetchLogs,
  downloadLogs,
  copyLogsText,
  setIsAtBottom
} = useDeploymentLogs(
  () => idRef.value,
  () => selectedContainerId.value,
  {
    scrollToBottom: () => logListRef.value?.scrollToBottom()
  }
)

const { copy: copyText } = useCopyToClipboard({
  successMessage: t('deployment.logs.copySuccess'),
  errorMessage: t('deployment.logs.copyFailed')
})

watch(visible, async (v) => {
  if (v) {
    await fetchContainers()
    if (selectedContainerId.value !== ALL_CONTAINERS) {
      void fetchContainerDetails(selectedContainerId.value)
    }
    // 选第一个容器后 fetchLogs 自动触发（watch(selectedContainerIdRef)）
    if (containers.value.length > 0) {
      await nextTick()
    }
  } else {
    // 关闭时清理日志（容器数据由 useDeploymentContainers.reset 自行处理）
    logLines.value = []
  }
}, { immediate: true })

watch(selectedContainerId, (cid) => {
  if (cid && cid !== ALL_CONTAINERS) {
    void fetchContainerDetails(cid)
  } else {
    containerDetails.value = null
  }
})

function handleContainerChange(value: string): void {
  selectContainer(value)
}

async function handleCopyAll(): Promise<void> {
  const text = copyLogsText()
  if (!text) {
    ElMessage.warning(t('deployment.logs.copyEmpty'))
    return
  }
  if (filteredLogs.value.length > 0 && searchTerm.value.trim()) {
    try {
      await ElMessageBox.confirm(
        t('deployment.logs.copyConfirmFiltered', { count: filteredLogs.value.length }),
        t('deployment.logs.copyAll'),
        { type: 'info', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
      )
    } catch {
      return
    }
  }
  await copyText(text)
}

function handleDownload(): void {
  if (filteredLogs.value.length === 0 && logLines.value.length === 0) {
    ElMessage.warning(t('deployment.logs.downloadEmpty'))
    return
  }
  downloadLogs()
  ElMessage.success(t('deployment.logs.downloadSuccess'))
}

function handleRefresh(): void {
  void fetchLogs()
  if (selectedContainerId.value !== ALL_CONTAINERS) {
    void fetchContainerDetails(selectedContainerId.value)
  }
}

function windowOpen(url: string): void {
  window.open(url, '_blank', 'noopener,noreferrer')
}
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="t('deployment.logs.title')"
    width="1000"
    top="5vh"
    :close-on-click-modal="false"
    align-center
    destroy-on-close
    :footer="null"
    class="deployment-logs-dialog"
  >
    <template #header>
      <div class="deployment-logs-header">
        <i class="i-ep-monitor" />
        <span class="deployment-logs-header__title">{{ t('deployment.logs.title') }}</span>
        <span
          v-if="deployment"
          class="deployment-logs-header__subtitle"
        >
          - {{ deployment.container_name || deployment.deployment_name || deployment.id }}
        </span>
      </div>
    </template>

    <div class="deployment-logs">
      <DeploymentLogSearchBar
        v-model:search-term="searchTerm"
        v-model:stream-filter="streamFilter"
        v-model:auto-refresh="autoRefresh"
        v-model:following="following"
        :loading="loading"
        :has-logs="logLines.length > 0"
        :status="deployment?.status ?? 'unknown'"
        :last-updated="lastUpdatedAt"
        :filtered-count="filteredLogs.length"
        :total-count="logLines.length"
        @refresh="handleRefresh"
        @copy="handleCopyAll"
        @download="handleDownload"
      />

      <div class="deployment-logs__toolbar">
        <DeploymentContainerSelector
          :containers="containers"
          :model-value="selectedContainerId"
          :loading="containersLoading"
          @update:model-value="handleContainerChange"
        />
        <DeploymentContainerDetails
          v-if="selectedContainerId !== ALL_CONTAINERS"
          :container="containerDetails ?? selectedContainer"
          :loading="containerDetailsLoading"
          :status-config="getStatusConfig"
          @refresh="fetchContainerDetails(selectedContainerId)"
          @open-url="(url) => windowOpen(url)"
        />
      </div>

      <div class="deployment-logs__main">
        <LoadingState
          v-if="loading && logLines.length === 0"
          :message="t('deployment.logs.loading')"
        />
        <ErrorState
          v-else-if="error"
          :title="t('common.error.title')"
          :message="error.message"
          @retry="handleRefresh"
        />
        <EmptyState
          v-else-if="containers.length === 0 && !containersLoading"
          :title="t('deployment.logs.containerEmpty')"
          :description="t('deployment.logs.containerEmptyDesc')"
        />
        <EmptyState
          v-else-if="logLines.length > 0 && filteredLogs.length === 0"
          :title="t('deployment.logs.noMatchTitle')"
          :description="t('deployment.logs.noMatchDesc')"
        />
        <EmptyState
          v-else-if="logLines.length === 0"
          :title="t('deployment.logs.emptyTitle')"
          :description="t('deployment.logs.emptyDesc')"
        />
        <DeploymentLogList
          v-else
          ref="logListRef"
          :lines="filteredLogs"
          :is-streaming="loading && following"
          :following="following"
          :is-at-bottom="isAtBottom"
          @scroll-state="setIsAtBottom"
        />
      </div>
    </div>
  </ElDialog>
</template>

<style scoped lang="scss">
.deployment-logs {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-3);

  &-header {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;

    &__title {
      font-size: var(--el-font-size-large);
      font-weight: 500;
    }

    &__subtitle {
      font-size: var(--el-font-size-small);
      color: var(--el-text-color-secondary);
    }
  }

  &__toolbar {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-3);
    padding: var(--ys-spacing-3);
    background: var(--el-fill-color-light);
    border-radius: var(--el-border-radius-base);
  }

  &__main {
    min-height: 360px;
    max-height: 50vh;
    overflow: hidden;
  }
}
</style>
