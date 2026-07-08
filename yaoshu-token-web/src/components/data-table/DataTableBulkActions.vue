<script setup lang="ts" generic="TData extends Record<string, unknown>">
import { computed } from 'vue'
import { ElBadge, ElButton, ElDivider } from 'element-plus'
import { useI18n } from 'vue-i18n'
import type { DataTableInstance } from './types'

const props = defineProps<{
  dataTable: DataTableInstance<TData>
  /** 选中实体名的 i18n key */
  entityName: string
}>()

const { t } = useI18n()

const selectedCount = computed(() => props.dataTable.selectedCount.value)

function clearSelection() {
  props.dataTable.resetRowSelection()
}
</script>

<template>
  <Transition name="bulk-actions">
    <div
      v-if="selectedCount > 0"
      class="data-table-bulk-actions"
      role="toolbar"
    >
      <ElButton
        size="small"
        variant="default"
        :aria-label="t('common.clearSelection')"
        @click="clearSelection"
      >
        ✕
      </ElButton>
      <ElDivider direction="vertical" />
      <div class="data-table-bulk-actions__info">
        <ElBadge
          :value="selectedCount"
          type="primary"
        />
        <span class="data-table-bulk-actions__text">
          {{ t(entityName) }} {{ t('common.selected') }}
        </span>
      </div>
      <ElDivider direction="vertical" />
      <div class="data-table-bulk-actions__actions">
        <slot />
      </div>
    </div>
  </Transition>
</template>

<style scoped lang="scss">
.data-table-bulk-actions {
  position: fixed;
  bottom: 24px;
  left: 50%;
  z-index: 50;
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
  padding: var(--ys-spacing-2) var(--ys-spacing-3);
  background: var(--el-bg-color);
  border: var(--el-border);
  border-radius: var(--ys-radius-lg);
  box-shadow: var(--el-box-shadow);
  backdrop-filter: blur(8px);
  transform: translateX(-50%);

  &__info {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
  }

  &__text {
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-regular);
  }

  &__actions {
    display: flex;
    gap: var(--ys-spacing-1);
    align-items: center;
  }
}

.bulk-actions-enter-active,
.bulk-actions-leave-active {
  transition: all 0.3s ease;
}

.bulk-actions-enter-from,
.bulk-actions-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(20px);
}
</style>
