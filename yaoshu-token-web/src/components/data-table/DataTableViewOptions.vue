<script setup lang="ts" generic="TData extends Record<string, unknown>">
import { computed } from 'vue'
import { ElButton, ElCheckbox, ElDropdown, ElDropdownItem, ElDropdownMenu } from 'element-plus'
import { useI18n } from 'vue-i18n'
import type { DataTableInstance } from './types'

const props = defineProps<{
  dataTable: DataTableInstance<TData>
}>()

const { t } = useI18n()

// 可切换可见性的列（enableHiding !== false 且有 field 或 cellRenderer）
const toggleableColumns = computed(() =>
  props.dataTable.columns.value.filter(
    (col) => col.enableHiding !== false && (col.field || col.cellRenderer || col.key),
  ),
)

function getColumnLabel(col: (typeof toggleableColumns.value)[number]): string {
  return col.meta?.label ?? col.title ?? col.key
}
</script>

<template>
  <ElDropdown
    trigger="click"
    placement="bottom-end"
  >
    <ElButton
      size="default"
      variant="default"
    >
      {{ t('common.view') }}
    </ElButton>
    <template #dropdown>
      <ElDropdownMenu>
        <div class="data-table-view-options__label">
          {{ t('common.toggleColumns') }}
        </div>
        <ElDropdownItem
          v-for="col in toggleableColumns"
          :key="col.key"
          class="data-table-view-options__item"
        >
          <ElCheckbox
            :model-value="dataTable.getColumnVisibility(col.key)"
            @change="(val: string | number | boolean) => dataTable.toggleColumnVisibility(col.key, Boolean(val))"
          >
            {{ getColumnLabel(col) }}
          </ElCheckbox>
        </ElDropdownItem>
      </ElDropdownMenu>
    </template>
  </ElDropdown>
</template>

<style scoped lang="scss">
.data-table-view-options {
  &__label {
    padding: var(--ys-spacing-2) var(--ys-spacing-4) var(--ys-spacing-1);
    font-size: var(--el-font-size-extra-small);
    color: var(--el-text-color-secondary);
  }

  &__item {
    :deep(.el-checkbox) {
      width: 100%;
    }
  }
}
</style>
