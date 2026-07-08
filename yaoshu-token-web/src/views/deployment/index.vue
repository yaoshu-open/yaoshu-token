<script setup lang="ts">
/**
 * 部署列表页（M2 P1 ModelDeployment 部署增强集成点）。
 *
 * 范围说明：
 * - 列表 UI 用 ElTable 简化实现（避免与 M1-C data-table 重复造轮子）
 * - 3 个增强 Dialog（T-MD-01/02/03）是核心交付物
 * - 不实现 create / start / restart / batchDelete（后端契约未提供 / 跨 classic 行为差异）；rename 已实现（MD-C4）
 */
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { listDeployments, searchDeployments, syncDeploymentToChannel } from '@/api/deployment'
import type { DeploymentListItem } from '@/api/deployment/types'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import ErrorState from '@/components/ErrorState.vue'
import DeploymentDetailsDialog from '@/components/deployment/DeploymentDetailsDialog.vue'
import DeploymentLogsDialog from '@/components/deployment/DeploymentLogsDialog.vue'
import DeploymentExtendDialog from '@/components/deployment/DeploymentExtendDialog.vue'
import DeploymentRenameDialog from '@/components/deployment/DeploymentRenameDialog.vue'
import DeploymentTableRow from '@/components/deployment/DeploymentTableRow.vue'
import CreateDeploymentWizard from '@/components/deployment/CreateDeploymentWizard.vue'

const { t } = useI18n()

const items = ref<DeploymentListItem[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<Error | null>(null)
const keyword = ref('')
const page = ref(1)
const pageSize = ref(10)
const currentDeployment = ref<DeploymentListItem | null>(null)

const detailsOpen = ref(false)
const logsOpen = ref(false)
const extendOpen = ref(false)
const renameOpen = ref(false)
const createOpen = ref(false)

const hasFilter = computed(() => keyword.value.trim().length > 0)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const params = { p: page.value, page_size: pageSize.value, status: undefined as string | undefined, keyword: keyword.value.trim() || undefined }
    const res = hasFilter.value
      ? await searchDeployments(params)
      : await listDeployments(params)
    items.value = res.items ?? []
    total.value = res.total ?? 0
  } catch (e) {
    error.value = e as Error
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openDetails(d: DeploymentListItem): void {
  currentDeployment.value = d
  detailsOpen.value = true
}
function openLogs(d: DeploymentListItem): void {
  currentDeployment.value = d
  logsOpen.value = true
}
function openExtend(d: DeploymentListItem): void {
  currentDeployment.value = d
  extendOpen.value = true
}

function openRename(d: DeploymentListItem): void {
  currentDeployment.value = d
  renameOpen.value = true
}

function handleExtended(): void {
  void load()
}

function handleCreated(): void {
  page.value = 1
  void load()
}

const syncing = ref(false)

async function handleSyncToChannel(d: DeploymentListItem): Promise<void> {
  if (syncing.value) return
  syncing.value = true
  try {
    const name = d.container_name || d.deployment_name || String(d.id)
    await syncDeploymentToChannel(d.id, name)
    ElMessage.success(t('deployment.sync.success'))
  } catch (e) {
    const msg = e instanceof Error && e.message === 'NO_CONTAINER_URL'
      ? t('deployment.sync.noContainerUrl')
      : t('deployment.sync.failed')
    ElMessage.error(msg)
  } finally {
    syncing.value = false
  }
}
</script>

<template>
  <div class="deployment-view">
    <div class="deployment-view__header">
      <h2 class="deployment-view__title">
        <i class="i-lucide-server" />
        {{ t('deployment.list.title') }}
      </h2>
      <p class="deployment-view__subtitle">
        {{ t('deployment.list.subtitle') }}
      </p>
    </div>

    <div class="deployment-view__toolbar">
      <el-input
        v-model="keyword"
        :placeholder="t('deployment.list.searchPlaceholder')"
        clearable
        class="deployment-view__search"
        @keyup.enter="() => { page = 1; load() }"
        @clear="() => { page = 1; load() }"
      >
        <template #prefix>
          <i class="i-ep-search" />
        </template>
      </el-input>
      <el-button
        type="primary"
        :loading="loading"
        @click="() => { page = 1; load() }"
      >
        <i class="i-ep-search" />
        {{ t('deployment.list.search') }}
      </el-button>
      <el-button
        type="success"
        @click="createOpen = true"
      >
        <i class="i-ep-plus" />
        {{ t('deployment.create.button') }}
      </el-button>
    </div>

    <LoadingState
      v-if="loading && items.length === 0"
      :message="t('deployment.list.loading')"
    />
    <ErrorState
      v-else-if="error"
      :title="t('common.error.title')"
      :message="error.message"
      @retry="load"
    />
    <EmptyState
      v-else-if="items.length === 0"
      :title="t('deployment.list.emptyTitle')"
      :description="t('deployment.list.emptyDesc')"
    />
    <div
      v-else
      class="deployment-view__list"
    >
      <DeploymentTableRow
        v-for="d in items"
        :key="d.id"
        :deployment="d"
        @view-details="openDetails(d)"
        @view-logs="openLogs(d)"
        @extend="openExtend(d)"
        @sync-to-channel="handleSyncToChannel(d)"
        @rename="openRename(d)"
      />
    </div>

    <div
      v-if="items.length > 0"
      class="deployment-view__pagination"
    >
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="load"
        @size-change="() => { page = 1; load() }"
      />
    </div>

    <DeploymentDetailsDialog
      v-model="detailsOpen"
      :deployment="currentDeployment"
    />
    <DeploymentLogsDialog
      v-model="logsOpen"
      :deployment="currentDeployment"
    />
    <DeploymentExtendDialog
      v-model="extendOpen"
      :deployment="currentDeployment"
      @extended="handleExtended"
    />
    <DeploymentRenameDialog
      v-model="renameOpen"
      :deployment="currentDeployment"
      @renamed="load"
    />
    <CreateDeploymentWizard
      v-model="createOpen"
      @created="handleCreated"
    />
  </div>
</template>

<style scoped lang="scss">
.deployment-view {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);
  padding: var(--ys-spacing-4);

  &__header {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-1);
  }

  &__title {
    display: flex;
    gap: 6px;
    align-items: center;
    margin: 0;
    font-size: var(--el-font-size-extra-large);
    font-weight: 500;
  }

  &__subtitle {
    margin: 0;
    color: var(--el-text-color-secondary);
  }

  &__toolbar {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
  }

  &__search {
    max-width: 320px;
  }

  &__list {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
  }

  &__pagination {
    display: flex;
    justify-content: flex-end;
  }
}
</style>
