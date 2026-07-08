<script setup lang="ts">
/**
 * 日志搜索栏（搜索 + Stream Radio + Auto/Follow Switch + 刷新/复制/下载按钮）。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DeploymentStream } from '@/api/deployment/types'

const props = defineProps<{
  searchTerm: string
  streamFilter: DeploymentStream
  autoRefresh: boolean
  following: boolean
  loading: boolean
  hasLogs: boolean
  status: string
  lastUpdated: Date | null
  filteredCount: number
  totalCount: number
}>()

const emit = defineEmits<{
  (e: 'update:searchTerm', value: string): void
  (e: 'update:streamFilter', value: DeploymentStream): void
  (e: 'update:autoRefresh', value: boolean): void
  (e: 'update:following', value: boolean): void
  (e: 'refresh'): void
  (e: 'copy'): void
  (e: 'download'): void
}>()

const { t } = useI18n()

const updatedAtText = computed(() => {
  if (!props.lastUpdated) return ''
  return props.lastUpdated.toLocaleTimeString()
})
</script>

<template>
  <div class="deployment-logs-search">
    <div class="deployment-logs-search__row">
      <el-input
        :model-value="searchTerm"
        :placeholder="t('deployment.logs.searchPlaceholder')"
        clearable
        class="deployment-logs-search__input"
        @update:model-value="(v) => emit('update:searchTerm', String(v ?? ''))"
      >
        <template #prefix>
          <i class="i-ep-search" />
        </template>
      </el-input>

      <div class="deployment-logs-search__stream">
        <span class="deployment-logs-search__label">{{ t('deployment.logs.stream') }}</span>
        <el-radio-group
          :model-value="streamFilter"
          size="small"
          @update:model-value="(v) => emit('update:streamFilter', (v as DeploymentStream) ?? 'stdout')"
        >
          <el-radio-button value="stdout">
            STDOUT
          </el-radio-button>
          <el-radio-button value="stderr">
            STDERR
          </el-radio-button>
        </el-radio-group>
      </div>

      <div class="deployment-logs-search__switch">
        <el-switch
          :model-value="autoRefresh"
          @update:model-value="(v: boolean | string | number) => emit('update:autoRefresh', Boolean(v))"
        />
        <span class="deployment-logs-search__label">{{ t('deployment.logs.autoRefresh') }}</span>
      </div>

      <div class="deployment-logs-search__switch">
        <el-switch
          :model-value="following"
          @update:model-value="(v: boolean | string | number) => emit('update:following', Boolean(v))"
        />
        <span class="deployment-logs-search__label">{{ t('deployment.logs.follow') }}</span>
      </div>

      <div class="deployment-logs-search__actions">
        <el-tooltip
          :content="t('deployment.logs.refresh')"
          placement="top"
        >
          <el-button
            :loading="loading"
            size="small"
            link
            @click="emit('refresh')"
          >
            <i class="i-ep-refresh" />
          </el-button>
        </el-tooltip>
        <el-tooltip
          :content="t('deployment.logs.copyAll')"
          placement="top"
        >
          <el-button
            :disabled="!hasLogs"
            size="small"
            link
            @click="emit('copy')"
          >
            <i class="i-ep-copy-document" />
          </el-button>
        </el-tooltip>
        <el-tooltip
          :content="t('deployment.logs.download')"
          placement="top"
        >
          <el-button
            :disabled="!hasLogs"
            size="small"
            link
            @click="emit('download')"
          >
            <i class="i-ep-download" />
          </el-button>
        </el-tooltip>
      </div>
    </div>

    <div class="deployment-logs-search__status">
      <span class="deployment-logs-search__count">
        {{ t('deployment.logs.totalCount', { count: totalCount }) }}
      </span>
      <span
        v-if="searchTerm.trim()"
        class="deployment-logs-search__count"
      >
        {{ t('deployment.logs.filteredCount', { count: filteredCount }) }}
      </span>
      <el-tag
        v-if="autoRefresh"
        type="success"
        size="small"
      >
        <i class="i-ep-time" />
        {{ t('deployment.logs.autoRefreshing') }}
      </el-tag>
      <span
        v-if="updatedAtText"
        class="deployment-logs-search__updated"
      >
        {{ t('deployment.logs.lastUpdated') }}: {{ updatedAtText }}
      </span>
      <span class="deployment-logs-search__deployment-status">
        {{ t('deployment.logs.deploymentStatus') }}: {{ status }}
      </span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.deployment-logs-search {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-2);
  padding: var(--ys-spacing-3);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);

  &__row {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-3);
    align-items: center;
  }

  &__input {
    flex: 1;
    min-width: 180px;
  }

  &__label {
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-secondary);
  }

  &__stream {
    display: flex;
    gap: 6px;
    align-items: center;
  }

  &__switch {
    display: flex;
    gap: var(--ys-spacing-1);
    align-items: center;
  }

  &__actions {
    display: flex;
    gap: var(--ys-spacing-1);
    align-items: center;
  }

  &__status {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-3);
    align-items: center;
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-secondary);
  }

  &__count {
    font-weight: 500;
  }

  &__updated {
    font-family: var(--el-font-family-monospace, monospace);
  }

  &__deployment-status {
    margin-left: auto;
  }
}
</style>
