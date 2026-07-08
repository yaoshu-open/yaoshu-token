<script setup lang="ts" generic="TData extends Record<string, unknown>">
import { computed, ref, watch } from 'vue'
import { ElButton, ElInput } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useDebounce } from '@/composables/useDebounce'
import DataTableFacetedFilter from './DataTableFacetedFilter.vue'
import DataTableViewOptions from './DataTableViewOptions.vue'
import type {
  DataTableFilterDef,
  DataTableInstance,
} from './types'

const props = withDefaults(
  defineProps<{
    dataTable: DataTableInstance<TData>
    searchPlaceholder?: string
    searchDebounceMs?: number
    /** 指定列筛选的 columnId；省略则用全局筛选 */
    searchKey?: string
    filters?: DataTableFilterDef[]
    hideViewOptions?: boolean
    hasAdditionalFilters?: boolean
    hasExpandedActiveFilters?: boolean
  }>(),
  {
    searchPlaceholder: undefined,
    searchDebounceMs: 0,
    searchKey: undefined,
    filters: () => [],
    hideViewOptions: false,
    hasAdditionalFilters: false,
    hasExpandedActiveFilters: false,
  },
)

const emit = defineEmits<{
  (e: 'reset'): void
  (e: 'search'): void
}>()

const { t } = useI18n()

// ===== 搜索输入（含 IME 组合处理）=====
const currentSearchValue = computed(() => {
  if (props.searchKey) {
    const v = props.dataTable.getColumnFilterValue(props.searchKey)
    return typeof v === 'string' ? v : ''
  }
  return props.dataTable.globalFilter.value
})

const inputValue = ref(currentSearchValue.value)
const pendingValue = ref(currentSearchValue.value)
const isComposing = ref(false)

const searchDebounceMs = Math.max(0, props.searchDebounceMs)
const debouncedValue = useDebounce(pendingValue, searchDebounceMs)

// 外部状态变化时同步本地
watch(currentSearchValue, (value) => {
  if (!isComposing.value) inputValue.value = value
  pendingValue.value = value
})

function commitSearch(value: string) {
  if (value === currentSearchValue.value) return
  if (props.searchKey) {
    props.dataTable.setColumnFilter(props.searchKey, value || undefined)
  } else {
    props.dataTable.setGlobalFilter(value)
  }
}

// 防抖值变化时提交
watch(debouncedValue, (debounced) => {
  if (searchDebounceMs <= 0 || isComposing.value || debounced !== pendingValue.value) return
  commitSearch(debounced)
})

function queueSearch(value: string) {
  pendingValue.value = value
  if (searchDebounceMs <= 0) commitSearch(value)
}

function handleInput(value: string) {
  inputValue.value = value
  if (!isComposing.value) queueSearch(value)
}

function handleCompositionStart() {
  isComposing.value = true
}

function handleCompositionEnd(event: CompositionEvent) {
  isComposing.value = false
  const value = (event.target as HTMLInputElement).value
  inputValue.value = value
  queueSearch(value)
}

// ===== 重置 =====
const isFiltered = computed(
  () =>
    props.dataTable.columnFilters.value.length > 0 ||
    !!props.dataTable.globalFilter.value ||
    props.hasAdditionalFilters,
)

function handleReset() {
  isComposing.value = false
  inputValue.value = ''
  pendingValue.value = ''
  props.dataTable.resetColumnFilters()
  props.dataTable.resetGlobalFilter()
  emit('reset')
}

// ===== 折叠面板 =====
const expanded = ref(false)

const placeholder = computed(() => props.searchPlaceholder ?? t('common.filter'))
</script>

<template>
  <div class="data-table-toolbar">
    <slot name="leftActions" />

    <div class="data-table-toolbar__row">
      <!-- 自定义搜索或默认搜索 -->
      <slot
        v-if="$slots.customSearch"
        name="customSearch"
      />
      <ElInput
        v-else
        v-model="inputValue"
        :placeholder="placeholder"
        clearable
        class="data-table-toolbar__search"
        @input="handleInput"
        @compositionstart="handleCompositionStart"
        @compositionend="handleCompositionEnd"
        @clear="handleReset"
      >
        <template #prefix>
          <i class="i-ep-search" />
        </template>
      </ElInput>

      <!-- 额外搜索输入 -->
      <slot name="additionalSearch" />

      <!-- 筛选 chips -->
      <DataTableFacetedFilter
        v-for="filter in filters"
        :key="filter.columnId"
        :data-table="dataTable"
        :column-id="filter.columnId"
        :title="filter.title"
        :options="filter.options"
        :single-select="filter.singleSelect"
      />

      <!-- 折叠展开的筛选 -->
      <template v-if="expanded">
        <slot name="expandable" />
      </template>

      <!-- 右侧操作集群 -->
      <div class="data-table-toolbar__actions">
        <slot name="preActions" />

        <!-- 重置 -->
        <ElButton
          v-if="isFiltered"
          variant="default"
          size="default"
          @click="handleReset"
        >
          {{ t('common.reset') }}
          <i class="i-ep-close" />
        </ElButton>

        <!-- 搜索（form 模式） -->
        <ElButton
          v-if="$slots.search"
          type="primary"
          @click="emit('search')"
        >
          {{ t('common.search') }}
        </ElButton>

        <!-- 视图选项 -->
        <DataTableViewOptions
          v-if="!hideViewOptions"
          :data-table="dataTable"
        />

        <!-- 折叠切换 -->
        <ElButton
          v-if="$slots.expandable"
          variant="default"
          size="default"
          :class="{ 'has-active': hasExpandedActiveFilters && !expanded }"
          @click="expanded = !expanded"
        >
          {{ expanded ? t('common.collapse') : t('common.expand') }}
          <i
            class="i-ep-arrow-down"
            :class="{ 'is-rotated': expanded }"
          />
        </ElButton>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.data-table-toolbar {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-2);

  &__row {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-2) var(--ys-spacing-3);
    align-items: center;
  }

  &__search {
    width: 100%;
    max-width: 240px;
  }

  &__actions {
    display: flex;
    flex-shrink: 0;
    gap: 6px var(--ys-spacing-2);
    align-items: center;
    margin-left: auto;
  }
}

.is-rotated {
  transform: rotate(180deg);
  transition: transform 0.2s;
}

.has-active {
  color: var(--el-color-primary);
}
</style>
