import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { LocationQueryRaw } from 'vue-router'
import type {
  ColumnFiltersState,
  OnChangeFn,
  PaginationState,
  SortingState,
  UseTableUrlStateParams,
  UseTableUrlStateReturn,
} from '../types'

type SearchRecord = Record<string, unknown>

/** 从 query 值解析数字 */
function parseNumber(value: unknown, fallback: number): number {
  if (typeof value === 'number') return value
  if (typeof value === 'string') {
    const n = Number(value)
    return Number.isNaN(n) ? fallback : n
  }
  return fallback
}

/** 从 query 值解析字符串数组（支持 string | string[]） */
function parseStringArray(value: unknown): string[] {
  if (Array.isArray(value)) return value.map(String)
  if (typeof value === 'string' && value !== '') return [value]
  return []
}

/** 从 query 值解析字符串 */
function parseString(value: unknown): string {
  if (typeof value === 'string') return value
  if (Array.isArray(value) && value.length > 0) return String(value[0])
  return ''
}

/** 解析排序 query：sort=field:order 或 sort[]=field1:asc */
function parseSortingQuery(value: unknown): SortingState {
  const parseEntry = (entry: unknown): SortingState[number] | null => {
    if (typeof entry !== 'string') return null
    const colonIdx = entry.lastIndexOf(':')
    if (colonIdx <= 0) return null
    const key = entry.slice(0, colonIdx)
    const order = entry.slice(colonIdx + 1)
    if (order !== 'asc' && order !== 'desc') return null
    return { key, order }
  }

  if (Array.isArray(value)) {
    return value.map(parseEntry).filter((v): v is SortingState[number] => v !== null)
  }
  if (typeof value === 'string') {
    const parsed = parseEntry(value)
    return parsed ? [parsed] : []
  }
  return []
}

/** 序列化排序状态为 query 值 */
function serializeSorting(sorting: SortingState): string[] | string | undefined {
  if (sorting.length === 0) return undefined
  if (sorting.length === 1) return `${sorting[0].key}:${sorting[0].order}`
  return sorting.map((s) => `${s.key}:${s.order}`)
}

export function useTableUrlState(
  params: UseTableUrlStateParams = {},
): UseTableUrlStateReturn {
  const route = useRoute()
  const router = useRouter()

  const {
    pagination: paginationCfg,
    globalFilter: globalFilterCfg,
    sorting: sortingCfg,
    columnFilters: columnFiltersCfg = [],
  } = params

  const pageKey = paginationCfg?.pageKey ?? 'page'
  const pageSizeKey = paginationCfg?.pageSizeKey ?? 'pageSize'
  const defaultPage = paginationCfg?.defaultPage ?? 1
  const defaultPageSize = paginationCfg?.defaultPageSize ?? 20

  const globalFilterKey = globalFilterCfg?.key ?? 'filter'
  const globalFilterEnabled = globalFilterCfg?.enabled ?? true
  const trimGlobal = globalFilterCfg?.trim ?? true

  const sortingKey = sortingCfg?.key ?? 'sort'
  const sortingEnabled = sortingCfg?.enabled ?? true

  // 更新 URL query（合并式，不覆盖其他参数）
  function navigateQuery(patch: SearchRecord, replace = false) {
    const newQuery: LocationQueryRaw = { ...route.query }
    for (const [key, value] of Object.entries(patch)) {
      if (value === undefined) {
        delete newQuery[key]
      } else {
        newQuery[key] = value as LocationQueryRaw[string]
      }
    }
    router.replace({ query: newQuery }).catch(() => {
      // 路由替换失败（如导航守卫拦截），静默处理
    })
    void replace
  }

  // ===== 排序状态 =====
  const sorting = computed<SortingState>(() => {
    if (!sortingEnabled) return []
    return parseSortingQuery(route.query[sortingKey])
  })

  const onSortingChange: OnChangeFn<SortingState> = (updater) => {
    const next = typeof updater === 'function' ? updater(sorting.value) : updater
    navigateQuery({
      [sortingKey]: serializeSorting(next),
      [pageKey]: undefined,
    })
  }

  // ===== 全局筛选 =====
  const globalFilter = computed<string>(() => {
    if (!globalFilterEnabled) return ''
    const raw = route.query[globalFilterKey]
    const value = typeof raw === 'string' ? raw : Array.isArray(raw) ? String(raw[0] ?? '') : ''
    return trimGlobal ? value.trim() : value
  })

  const onGlobalFilterChange: OnChangeFn<string> | undefined = globalFilterEnabled
    ? (updater) => {
        const next = typeof updater === 'function' ? updater(globalFilter.value) : updater
        const value = trimGlobal ? next.trim() : next
        navigateQuery({
          [pageKey]: undefined,
          [globalFilterKey]: value || undefined,
        })
      }
    : undefined

  // ===== 列筛选 =====
  const columnFilters = computed<ColumnFiltersState>(() => {
    const collected: ColumnFiltersState = []
    for (const cfg of columnFiltersCfg) {
      const raw = route.query[cfg.searchKey]
      const deserialize = cfg.deserialize ?? ((v: unknown) => v)
      if (cfg.type === 'string') {
        const value = parseString(deserialize(raw))
        if (value.trim() !== '') {
          collected.push({ id: cfg.columnId, value })
        }
      } else {
        const value = parseStringArray(deserialize(raw))
        if (value.length > 0) {
          collected.push({ id: cfg.columnId, value })
        }
      }
    }
    return collected
  })

  const onColumnFiltersChange: OnChangeFn<ColumnFiltersState> = (updater) => {
    const next = typeof updater === 'function' ? updater(columnFilters.value) : updater
    const patch: SearchRecord = {}

    for (const cfg of columnFiltersCfg) {
      const found = next.find((f) => f.id === cfg.columnId)
      const serialize = cfg.serialize ?? ((v: unknown) => v)
      if (cfg.type === 'string') {
        const value = typeof found?.value === 'string' ? found.value : ''
        patch[cfg.searchKey] = value.trim() !== '' ? serialize(value) : undefined
      } else {
        const value = Array.isArray(found?.value) ? (found!.value as unknown[]) : []
        patch[cfg.searchKey] = value.length > 0 ? serialize(value) : undefined
      }
    }

    navigateQuery({
      [pageKey]: undefined,
      ...patch,
    })
  }

  // ===== 分页 =====
  const pagination = computed<PaginationState>(() => {
    const pageNum = parseNumber(route.query[pageKey], defaultPage)
    const pageSizeNum = parseNumber(route.query[pageSizeKey], defaultPageSize)
    return {
      pageIndex: Math.max(0, pageNum - 1),
      pageSize: pageSizeNum,
    }
  })

  const onPaginationChange: OnChangeFn<PaginationState> = (updater) => {
    const next = typeof updater === 'function' ? updater(pagination.value) : updater
    const nextPage = next.pageIndex + 1
    navigateQuery({
      [pageKey]: nextPage <= defaultPage ? undefined : nextPage,
      [pageSizeKey]: next.pageSize === defaultPageSize ? undefined : next.pageSize,
    })
  }

  // ===== 页码越界保护 =====
  const ensurePageInRange = (
    pageCount: number,
    opts: { resetTo?: 'first' | 'last' } = { resetTo: 'first' },
  ) => {
    const currentPage = parseNumber(route.query[pageKey], defaultPage)
    if (pageCount > 0 && currentPage > pageCount) {
      navigateQuery(
        {
          [pageKey]: opts.resetTo === 'last' ? pageCount : undefined,
        },
        true,
      )
    }
  }

  return {
    sorting,
    onSortingChange,
    globalFilter: globalFilterEnabled ? globalFilter : computed(() => ''),
    onGlobalFilterChange,
    columnFilters,
    onColumnFiltersChange,
    pagination,
    onPaginationChange,
    ensurePageInRange,
  }
}
