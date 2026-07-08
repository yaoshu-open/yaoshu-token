<script setup lang="ts">
/**
 * 渠道工具栏容器。
 *
 * 职责：搜索 / 筛选 / 紧凑模式切换 / 主操作按钮 / 批量操作栏（条件显示）。
 */
import { computed } from 'vue'
import { Search, RefreshLeft } from '@element-plus/icons-vue'
import { CHANNEL_STATUS_OPTIONS } from '@/api/channel/constants'
import CompactModeToggle from '@/components/CompactModeToggle.vue'
import PrimaryButtons from './PrimaryButtons.vue'
import BulkActions from './BulkActions.vue'
import type { ChannelFilters } from '@/composables/channel/useChannelsData'

const props = defineProps<{
  filters: ChannelFilters
  isCompact: boolean
  selectedCount: number
  hasSelection: boolean
}>()

const emit = defineEmits<{
  (e: 'update:filters', value: ChannelFilters): void
  (e: 'search'): void
  (e: 'reset-filters'): void
  (e: 'toggle-compact'): void
  (e: 'add'): void
  (e: 'test-all'): void
  (e: 'update-all-balance'): void
  (e: 'fix-abilities'): void
  (e: 'delete-disabled'): void
  (e: 'batch-delete'): void
  (e: 'batch-set-tag'): void
  (e: 'batch-enable'): void
  (e: 'batch-disable'): void
  (e: 'clear-selection'): void
}>()

const statusOptions = CHANNEL_STATUS_OPTIONS

// v-model 代理
const keyword = computed({
  get: () => props.filters.keyword,
  set: (val: string) => emit('update:filters', { ...props.filters, keyword: val })
})
const status = computed({
  get: () => props.filters.status,
  set: (val: string) => emit('update:filters', { ...props.filters, status: val })
})
const group = computed({
  get: () => props.filters.group,
  set: (val: string) => emit('update:filters', { ...props.filters, group: val })
})
</script>

<template>
  <div class="channels-toolbar">
    <!-- 第一行：搜索 + 筛选 + 紧凑模式 + 主操作 -->
    <div class="toolbar-row">
      <div class="toolbar-left">
        <el-input
          v-model="keyword"
          :placeholder="$t('channel.search.placeholder')"
          :prefix-icon="Search"
          clearable
          size="default"
          style="width: 240px"
          @keyup.enter="$emit('search')"
          @clear="$emit('search')"
        />

        <el-select
          v-model="status"
          :placeholder="$t('channel.filters.status')"
          size="default"
          style="width: 140px"
          clearable
        >
          <el-option
            v-for="opt in statusOptions"
            :key="opt.value"
            :label="$t(opt.label)"
            :value="opt.value"
          />
        </el-select>

        <el-input
          v-model="group"
          :placeholder="$t('channel.filters.group')"
          size="default"
          style="width: 140px"
          clearable
          @keyup.enter="$emit('search')"
        />

        <el-button
          :icon="RefreshLeft"
          size="default"
          @click="$emit('reset-filters')"
        >
          {{ $t('channel.actions.reset') }}
        </el-button>
      </div>

      <div class="toolbar-right">
        <CompactModeToggle
          table-key="channels"
          variant="switch"
          @change="$emit('toggle-compact')"
        />
        <PrimaryButtons
          @add="$emit('add')"
          @test-all="$emit('test-all')"
          @update-all-balance="$emit('update-all-balance')"
          @fix-abilities="$emit('fix-abilities')"
          @delete-disabled="$emit('delete-disabled')"
        />
      </div>
    </div>

    <!-- 第二行：批量操作（选中时显示） -->
    <BulkActions
      v-if="hasSelection"
      :selected-count="selectedCount"
      @batch-delete="$emit('batch-delete')"
      @batch-set-tag="$emit('batch-set-tag')"
      @batch-enable="$emit('batch-enable')"
      @batch-disable="$emit('batch-disable')"
      @clear-selection="$emit('clear-selection')"
    />
  </div>
</template>

<style scoped>
.channels-toolbar {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-2);
  padding: var(--ys-spacing-3) 0;
}

.toolbar-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ys-spacing-3);
  align-items: center;
  justify-content: space-between;
}

.toolbar-left {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ys-spacing-2);
  align-items: center;
}

.toolbar-right {
  display: flex;
  gap: var(--ys-spacing-3);
  align-items: center;
}
</style>
