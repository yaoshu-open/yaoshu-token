<script setup lang="ts" generic="TData extends Record<string, unknown>">
import { computed } from 'vue'
import { ElBadge, ElButton, ElCheckbox, ElCheckboxGroup, ElDivider, ElEmpty, ElInput, ElPopover } from 'element-plus'
import { useI18n } from 'vue-i18n'
import type { DataTableFilterOption, DataTableInstance } from './types'

const props = withDefaults(
  defineProps<{
    dataTable: DataTableInstance<TData>
    columnId: string
    title: string
    options: DataTableFilterOption[]
    singleSelect?: boolean
  }>(),
  { singleSelect: false },
)

const { t } = useI18n()

const selectedValues = computed<string[]>(() => {
  const filterValue = props.dataTable.getColumnFilterValue(props.columnId)
  if (Array.isArray(filterValue)) return filterValue
  if (typeof filterValue === 'string' && filterValue) return [filterValue]
  return []
})

const selectedCount = computed(() => selectedValues.value.length)

function handleChange(values: Array<string | number | boolean>) {
  const arr = values.map(String)
  if (props.singleSelect) {
    props.dataTable.setColumnFilter(
      props.columnId,
      arr.length > 0 ? arr[0] : undefined,
    )
  } else {
    props.dataTable.setColumnFilter(
      props.columnId,
      arr.length > 0 ? arr : undefined,
    )
  }
}

function clearFilter() {
  props.dataTable.setColumnFilter(props.columnId, undefined)
}
</script>

<template>
  <ElPopover
    trigger="click"
    placement="bottom-start"
    :width="240"
    :show-arrow="false"
  >
    <template #reference>
      <ElButton
        size="small"
        variant="default"
        class="data-table-faceted-filter__trigger"
      >
        <span>+</span>
        <span>{{ title }}</span>
        <template v-if="selectedCount > 0">
          <ElBadge
            :value="selectedCount"
            type="info"
          />
        </template>
      </ElButton>
    </template>

    <div class="data-table-faceted-filter">
      <ElInput
        :placeholder="title"
        size="small"
        disabled
      />
      <div class="data-table-faceted-filter__list">
        <ElEmpty
          v-if="options.length === 0"
          :description="t('common.noResults')"
          :image-size="40"
        />
        <ElCheckboxGroup
          v-else
          :model-value="selectedValues"
          @change="handleChange"
        >
          <div
            v-for="option in options"
            :key="option.value"
            class="data-table-faceted-filter__item"
          >
            <ElCheckbox :value="option.value">
              <span class="data-table-faceted-filter__label">{{ option.label }}</span>
              <span
                v-if="typeof option.count === 'number'"
                class="data-table-faceted-filter__count"
              >{{ option.count }}</span>
            </ElCheckbox>
          </div>
        </ElCheckboxGroup>
      </div>
      <template v-if="selectedCount > 0">
        <ElDivider />
        <ElButton
          text
          size="small"
          class="data-table-faceted-filter__clear"
          @click="clearFilter"
        >
          {{ t('common.clearFilters') }}
        </ElButton>
      </template>
    </div>
  </ElPopover>
</template>

<style scoped lang="scss">
.data-table-faceted-filter {
  &__trigger {
    gap: var(--ys-spacing-1);
    height: 32px;
    border-style: dashed;
  }

  &__list {
    max-height: 280px;
    margin-top: 8px;
    overflow-y: auto;
  }

  &__item {
    display: flex;
    align-items: center;
    padding: 2px 0;
  }

  &__label {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__count {
    margin-left: auto;
    font-family: var(--el-font-family-mono, monospace);
    font-size: var(--el-font-size-extra-small);
    color: var(--el-text-color-secondary);
  }

  &__clear {
    width: 100%;
  }
}
</style>
