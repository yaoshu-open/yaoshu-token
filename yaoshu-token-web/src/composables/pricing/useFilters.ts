/**
 * Pricing 过滤逻辑 composable。
 */
import { ref, computed, type Ref } from 'vue'
import {
  FILTER_ALL,
  SORT_OPTIONS,
  QUOTA_TYPES,
  ENDPOINT_TYPES,
  DEFAULT_TOKEN_UNIT,
  VIEW_MODES,
  type ViewMode
} from '@/api/pricing/constants'
import { filterAndSortModels, extractAllTags } from '@/views/pricing/lib/filters'
import type { PricingModel, TokenUnit } from '@/api/pricing/types'

interface UseFiltersReturn {
  searchInput: Ref<string>
  sortBy: Ref<string>
  vendorFilter: Ref<string>
  groupFilter: Ref<string>
  quotaTypeFilter: Ref<string>
  endpointTypeFilter: Ref<string>
  tagFilter: Ref<string>
  tokenUnit: Ref<TokenUnit>
  viewMode: Ref<ViewMode>
  showRechargePrice: Ref<boolean>
  filteredModels: Ref<PricingModel[]>
  hasActiveFilters: Ref<boolean>
  activeFilterCount: Ref<number>
  availableTags: Ref<string[]>
  clearFilters: () => void
  clearSearch: () => void
}

export function useFilters(models: Ref<PricingModel[]>): UseFiltersReturn {
  const searchInput = ref('')
  const sortBy = ref(SORT_OPTIONS.NAME)
  const vendorFilter = ref(FILTER_ALL)
  const groupFilter = ref(FILTER_ALL)
  const quotaTypeFilter = ref(QUOTA_TYPES.ALL)
  const endpointTypeFilter = ref(ENDPOINT_TYPES.ALL)
  const tagFilter = ref(FILTER_ALL)
  const tokenUnit = ref<TokenUnit>(DEFAULT_TOKEN_UNIT)
  const viewMode = ref<ViewMode>(VIEW_MODES.CARD)
  const showRechargePrice = ref(false)

  const availableTags = computed(() => {
    if (!models.value || models.value.length === 0) return []
    return extractAllTags(models.value)
  })

  const filteredModels = computed(() => {
    if (!models.value || models.value.length === 0) return []
    return filterAndSortModels(models.value, {
      search: searchInput.value,
      vendor: vendorFilter.value,
      group: groupFilter.value,
      quotaType: quotaTypeFilter.value,
      endpointType: endpointTypeFilter.value,
      tag: tagFilter.value,
      sortBy: sortBy.value
    })
  })

  const hasActiveFilters = computed(
    () =>
      vendorFilter.value !== FILTER_ALL ||
      groupFilter.value !== FILTER_ALL ||
      quotaTypeFilter.value !== QUOTA_TYPES.ALL ||
      endpointTypeFilter.value !== ENDPOINT_TYPES.ALL ||
      tagFilter.value !== FILTER_ALL
  )

  const activeFilterCount = computed(
    () =>
      (vendorFilter.value !== FILTER_ALL ? 1 : 0) +
      (groupFilter.value !== FILTER_ALL ? 1 : 0) +
      (quotaTypeFilter.value !== QUOTA_TYPES.ALL ? 1 : 0) +
      (endpointTypeFilter.value !== ENDPOINT_TYPES.ALL ? 1 : 0) +
      (tagFilter.value !== FILTER_ALL ? 1 : 0)
  )

  function clearFilters() {
    vendorFilter.value = FILTER_ALL
    groupFilter.value = FILTER_ALL
    quotaTypeFilter.value = QUOTA_TYPES.ALL
    endpointTypeFilter.value = ENDPOINT_TYPES.ALL
    tagFilter.value = FILTER_ALL
  }

  function clearSearch() {
    searchInput.value = ''
  }

  return {
    searchInput,
    sortBy,
    vendorFilter,
    groupFilter,
    quotaTypeFilter,
    endpointTypeFilter,
    tagFilter,
    tokenUnit,
    viewMode,
    showRechargePrice,
    filteredModels,
    hasActiveFilters,
    activeFilterCount,
    availableTags,
    clearFilters,
    clearSearch
  }
}
