<script setup lang="ts">
/**
 * 渠道批量操作栏。
 *
 * 职责：选中行后显示，提供批量删除/设置标签/启用/禁用操作。
 */
import { Delete, PriceTag, Open, TurnOff } from '@element-plus/icons-vue'

defineProps<{
  selectedCount: number
}>()

const emit = defineEmits<{
  (e: 'batch-delete'): void
  (e: 'batch-set-tag'): void
  (e: 'batch-enable'): void
  (e: 'batch-disable'): void
  (e: 'clear-selection'): void
}>()
</script>

<template>
  <div class="channel-bulk-actions">
    <div class="bulk-info">
      <span class="selected-count">
        {{ $t('channel.bulk.selected', { count: selectedCount }) }}
      </span>
      <el-button
        link
        type="primary"
        size="small"
        @click="emit('clear-selection')"
      >
        {{ $t('channel.bulk.clear') }}
      </el-button>
    </div>

    <div class="bulk-buttons">
      <el-button
        size="small"
        :icon="Open"
        @click="emit('batch-enable')"
      >
        {{ $t('channel.bulk.enable') }}
      </el-button>
      <el-button
        size="small"
        :icon="TurnOff"
        @click="emit('batch-disable')"
      >
        {{ $t('channel.bulk.disable') }}
      </el-button>
      <el-button
        size="small"
        :icon="PriceTag"
        @click="emit('batch-set-tag')"
      >
        {{ $t('channel.bulk.setTag') }}
      </el-button>
      <el-button
        size="small"
        type="danger"
        :icon="Delete"
        @click="emit('batch-delete')"
      >
        {{ $t('channel.bulk.delete') }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.channel-bulk-actions {
  display: flex;
  gap: var(--ys-spacing-3);
  align-items: center;
  justify-content: space-between;
  padding: var(--ys-spacing-2) var(--ys-spacing-3);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: var(--ys-radius-sm);
}

.bulk-info {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
}

.selected-count {
  font-size: var(--ys-font-size-sm);
  font-weight: 500;
  color: var(--el-color-primary);
}

.bulk-buttons {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
}
</style>
