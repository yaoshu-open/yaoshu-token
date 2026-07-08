/**
 * 消息渲染窗口化分批加载 composable。
 *
 * 底层消息数据（localStorage / chat.messages）保持全量完整，仅在渲染层做窗口切片：
 * 默认只暴露最近 N 条，滚动到顶部时调用 loadMore 追加更早 M 条，避免长对话全量挂载 DOM 卡顿。
 */
import { computed, ref } from 'vue'
import type { Ref } from 'vue'

interface UseMessageWindowOptions {
  /** 初始可见条数 */
  initialCount?: number
  /** 每次加载追加条数 */
  pageSize?: number
}

export function useMessageWindow<T extends { key: string }>(
  items: Ref<T[]>,
  options: UseMessageWindowOptions = {}
) {
  const initialCount = options.initialCount ?? 30
  const pageSize = options.pageSize ?? 20

  // 当前已加载的条数（从末尾向头部计数）
  const visibleCount = ref(initialCount)

  const totalCount = computed(() => items.value.length)
  const hasMore = computed(() => visibleCount.value < totalCount.value)

  // 窗口切片：保留末尾 visibleCount 条（最新消息始终可见）
  const visibleItems = computed<T[]>(() => {
    const start = Math.max(0, totalCount.value - visibleCount.value)
    return items.value.slice(start)
  })

  /** 已加载条数（供 UI 展示进度） */
  const loadedCount = computed(() => visibleItems.value.length)

  /** 追加更早的条目（滚动到顶部触发） */
  function loadMore(): void {
    if (!hasMore.value) return
    visibleCount.value += pageSize
  }

  /** 重置窗口（清空对话时调用） */
  function reset(): void {
    visibleCount.value = initialCount
  }

  return { visibleItems, visibleCount, totalCount, loadedCount, hasMore, loadMore, reset }
}
