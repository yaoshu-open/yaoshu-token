import { computed, ref, watch } from 'vue'
import type { ComputedRef, Ref } from 'vue'
import { useDebounce } from '@/composables/useDebounce'
import type {
  ColumnFiltersState,
  OnChangeFn,
} from '../types'

export interface UseDebouncedColumnFilterOptions {
  columnFilters: Ref<ColumnFiltersState> | ComputedRef<ColumnFiltersState>
  columnId: string
  onColumnFiltersChange: OnChangeFn<ColumnFiltersState>
  delay?: number
}

/**
 * 列筛选输入防抖：本地输入即时响应，提交到 columnFilters 延迟 delay ms。
 * 处理中文输入法 composition 事件，避免输入中途触发筛选。
 */
export function useDebouncedColumnFilter(
  options: UseDebouncedColumnFilterOptions,
) {
  const {
    columnFilters,
    columnId,
    onColumnFiltersChange,
    delay = 500,
  } = options

  const currentValue = computed(
    () =>
      (columnFilters.value.find((f) => f.id === columnId)?.value as
        | string
        | undefined) ?? '',
  )

  const inputValue = ref(currentValue.value)
  const pendingValue = ref(currentValue.value)
  const isComposing = ref(false)

  const debouncedValue = useDebounce(pendingValue, delay)

  // 外部状态变化时同步本地输入（URL 直接变更等场景）
  watch(currentValue, (value) => {
    if (!isComposing.value) {
      inputValue.value = value
    }
    pendingValue.value = value
  })

  // 防抖值变化时提交到 columnFilters
  watch(debouncedValue, (debounced) => {
    if (debounced === currentValue.value) return
    onColumnFiltersChange((previous) => {
      const filters = previous.filter((f) => f.id !== columnId)
      return debounced
        ? [...filters, { id: columnId, value: debounced }]
        : filters
    })
  })

  function updateInputValue(next: string) {
    inputValue.value = next
    if (!isComposing.value) {
      pendingValue.value = next
    }
  }

  function handleCompositionStart() {
    isComposing.value = true
  }

  function handleCompositionEnd(event: CompositionEvent) {
    isComposing.value = false
    const target = event.target as HTMLInputElement
    inputValue.value = target.value
    pendingValue.value = target.value
  }

  function resetInput() {
    isComposing.value = false
    inputValue.value = ''
    pendingValue.value = ''
  }

  return {
    value: currentValue,
    inputValue,
    setInputValue: updateInputValue,
    onCompositionStart: handleCompositionStart,
    onCompositionEnd: handleCompositionEnd,
    resetInput,
  }
}
