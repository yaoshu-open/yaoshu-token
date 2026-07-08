<script setup lang="ts" generic="TData extends Record<string, unknown>">
import { computed } from 'vue'
import { ElEmpty, ElSkeleton } from 'element-plus'
import { useI18n } from 'vue-i18n'
import type { DataTableInstance } from './types'

const props = withDefaults(
  defineProps<{
    dataTable: DataTableInstance<TData>
    isLoading?: boolean
    emptyTitle?: string
    emptyDescription?: string
    rowClassName?: (rowData: TData) => string | undefined
  }>(),
  {
    isLoading: false,
    emptyTitle: undefined,
    emptyDescription: undefined,
    rowClassName: undefined,
  },
)

const { t } = useI18n()

const tableData = computed(() => props.dataTable.data.value)
const isEmpty = computed(() => !props.isLoading && tableData.value.length === 0)
</script>

<template>
  <div class="data-table-mobile-card-list">
    <!-- 加载骨架 -->
    <template v-if="isLoading">
      <div
        v-for="i in 6"
        :key="i"
        class="data-table-mobile-card-list__skeleton"
      >
        <ElSkeleton
          animated
          :lines="3"
        />
      </div>
    </template>

    <!-- 空数据 -->
    <ElEmpty
      v-else-if="isEmpty"
      :description="emptyTitle ?? t('common.empty.title')"
      :image-size="80"
    />

    <!-- 卡片列表 -->
    <template v-else>
      <div
        v-for="(row, index) in tableData"
        :key="dataTable.getRowId(row, index)"
        class="data-table-mobile-card-list__card"
        :class="rowClassName?.(row)"
      >
        <slot
          name="card"
          :row="row"
          :index="index"
        />
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.data-table-mobile-card-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--ys-spacing-2);
  min-height: 0;
  overflow-y: auto;

  &__skeleton {
    padding: var(--ys-spacing-3);
    background: var(--el-fill-color-light);
    border-radius: var(--ys-radius-md);
  }

  &__card {
    padding: var(--ys-spacing-3);
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--ys-radius-md);
  }
}
</style>
