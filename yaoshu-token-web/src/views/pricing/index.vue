<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { usePricingData } from '@/composables/pricing/usePricingData'
import { useFilters } from '@/composables/pricing/useFilters'
import { EXCLUDED_GROUPS, VIEW_MODES, FILTER_ALL, SORT_OPTIONS, QUOTA_TYPES, ENDPOINT_TYPES } from '@/api/pricing/constants'
import SearchBar from '@/components/pricing/SearchBar.vue'
import PricingSidebar from '@/components/pricing/PricingSidebar.vue'
import PricingToolbar from '@/components/pricing/PricingToolbar.vue'
import PricingTable from '@/components/pricing/PricingTable.vue'
import ModelCardGrid from '@/components/pricing/ModelCardGrid.vue'
import LoadingSkeleton from '@/components/pricing/LoadingSkeleton.vue'
import ModelDetailsDrawer from '@/components/pricing/ModelDetailsDrawer.vue'
import EmptyState from '@/components/EmptyState.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const {
  models,
  vendors,
  groupRatio,
  usableGroup,
  endpointMap,
  autoGroups,
  isLoading,
  priceRate,
  usdExchangeRate
} = usePricingData()

const {
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
} = useFilters(models)

const selectedModelName = ref<string | null>(null)

const selectedModel = computed(() =>
  selectedModelName.value
    ? models.value.find((m) => m.modelName === selectedModelName.value) ?? null
    : null
)

const availableGroups = computed(() =>
  Object.keys(usableGroup.value).filter((g) => !EXCLUDED_GROUPS.includes(g))
)

function handleModelClick(modelName: string) {
  selectedModelName.value = modelName
}

function handleClearAll() {
  clearFilters()
  clearSearch()
}

// T-PRICING-01 URL query 双向同步：读取初始化 + 变更写回
let syncingFromQuery = false

onMounted(() => {
  syncingFromQuery = true
  const q = route.query
  if (typeof q.search === 'string' && q.search) searchInput.value = q.search
  if (typeof q.sort === 'string' && q.sort) sortBy.value = q.sort
  if (typeof q.vendor === 'string' && q.vendor) vendorFilter.value = q.vendor
  if (typeof q.group === 'string' && q.group) groupFilter.value = q.group
  if (typeof q.quotaType === 'string' && q.quotaType) quotaTypeFilter.value = q.quotaType
  if (typeof q.endpointType === 'string' && q.endpointType) endpointTypeFilter.value = q.endpointType
  if (typeof q.tag === 'string' && q.tag) tagFilter.value = q.tag
  // S5: tokenUnit 固定为 M，不再从 query 读取
  if (q.view === VIEW_MODES.TABLE || q.view === VIEW_MODES.CARD) viewMode.value = q.view
  if (q.rechargePrice === 'true') showRechargePrice.value = true
  // model deep-link：从 rankings 等外部跳转打开模型详情抽屉
  if (typeof q.model === 'string' && q.model) selectedModelName.value = q.model
  syncingFromQuery = false
})

// 过滤状态 → URL query 写回（仅非默认值入 query，保持 URL 简洁）
watch(
  [searchInput, sortBy, vendorFilter, groupFilter, quotaTypeFilter, endpointTypeFilter, tagFilter, tokenUnit, viewMode, showRechargePrice],
  () => {
    if (syncingFromQuery) return
    const query: Record<string, string> = {}
    if (searchInput.value.trim()) query.search = searchInput.value.trim()
    if (sortBy.value !== SORT_OPTIONS.NAME) query.sort = sortBy.value
    if (vendorFilter.value !== FILTER_ALL) query.vendor = vendorFilter.value
    if (groupFilter.value !== FILTER_ALL) query.group = groupFilter.value
    if (quotaTypeFilter.value !== QUOTA_TYPES.ALL) query.quotaType = quotaTypeFilter.value
    if (endpointTypeFilter.value !== ENDPOINT_TYPES.ALL) query.endpointType = endpointTypeFilter.value
    if (tagFilter.value !== FILTER_ALL) query.tag = tagFilter.value
    // S5: tokenUnit 固定 M，不再写回 query
    if (viewMode.value !== VIEW_MODES.CARD) query.view = viewMode.value
    if (showRechargePrice.value) query.rechargePrice = 'true'
    router.replace({ path: '/pricing', query })
  }
)
</script>

<template>
  <div class="pricing-page">
    <!-- 加载态 -->
    <LoadingSkeleton
      v-if="isLoading"
      :view-mode="viewMode"
    />

    <!-- 内容 -->
    <template v-else>
      <!-- 背景装饰 -->
      <div
        class="pricing-page__bg"
        aria-hidden="true"
      />

      <div class="pricing-page__container">
        <!-- 标题区 -->
        <header class="pricing-page__header">
          <h1 class="pricing-page__title">
            {{ t('pricing.title') }}
          </h1>
          <p class="pricing-page__count">
            {{ t('pricing.subtitle', { count: models.length }) }}
          </p>
          <p class="pricing-page__desc">
            {{ t('pricing.discover') }}
          </p>
          <SearchBar
            :value="searchInput"
            :placeholder="t('pricing.searchPlaceholder')"
            @update:value="(v) => searchInput = v"
            @clear="clearSearch"
          />
        </header>

        <!-- 主体布局：侧边栏 + 内容区 -->
        <div class="pricing-page__layout">
          <!-- 侧边栏（xl+ 显示） -->
          <aside class="pricing-page__sidebar">
            <PricingSidebar
              :quota-type-filter="quotaTypeFilter"
              :endpoint-type-filter="endpointTypeFilter"
              :vendor-filter="vendorFilter"
              :group-filter="groupFilter"
              :tag-filter="tagFilter"
              :vendors="vendors"
              :groups="availableGroups"
              :group-ratios="groupRatio"
              :tags="availableTags"
              :models="models"
              :has-active-filters="hasActiveFilters"
              @update:quota-type-filter="(v) => quotaTypeFilter = v"
              @update:endpoint-type-filter="(v) => endpointTypeFilter = v"
              @update:vendor-filter="(v) => vendorFilter = v"
              @update:group-filter="(v) => groupFilter = v"
              @update:tag-filter="(v) => tagFilter = v"
              @clear-filters="clearFilters"
            />
          </aside>

          <!-- 内容区 -->
          <main class="pricing-page__main">
            <PricingToolbar
              :filtered-count="filteredModels.length"
              :total-count="models.length"
              :sort-by="sortBy"
              :token-unit="tokenUnit"
              :show-recharge-price="showRechargePrice"
              :view-mode="viewMode"
              :quota-type-filter="quotaTypeFilter"
              :endpoint-type-filter="endpointTypeFilter"
              :vendor-filter="vendorFilter"
              :group-filter="groupFilter"
              :tag-filter="tagFilter"
              :vendors="vendors"
              :groups="availableGroups"
              :group-ratios="groupRatio"
              :tags="availableTags"
              :models="models"
              :has-active-filters="hasActiveFilters"
              :active-filter-count="activeFilterCount"
              @update:sort-by="(v) => sortBy = v"
              @update:token-unit="(v) => tokenUnit = v"
              @update:show-recharge-price="(v) => showRechargePrice = v"
              @update:view-mode="(v: string) => viewMode = v as typeof viewMode"
              @update:quota-type-filter="(v: string) => quotaTypeFilter = v"
              @update:endpoint-type-filter="(v) => endpointTypeFilter = v"
              @update:vendor-filter="(v) => vendorFilter = v"
              @update:group-filter="(v) => groupFilter = v"
              @update:tag-filter="(v) => tagFilter = v"
              @clear-filters="clearFilters"
            />

            <!-- 空状态 -->
            <EmptyState
              v-if="filteredModels.length === 0"
              :description="t('pricing.noResults')"
            >
              <el-button
                v-if="hasActiveFilters || searchInput"
                @click="handleClearAll"
              >
                {{ t('pricing.clearAll') }}
              </el-button>
            </EmptyState>

            <!-- 卡片视图 -->
            <ModelCardGrid
              v-else-if="viewMode === VIEW_MODES.CARD"
              :models="filteredModels"
              :price-rate="priceRate"
              :usd-exchange-rate="usdExchangeRate"
              :token-unit="tokenUnit"
              :show-recharge-price="showRechargePrice"
              @model-click="handleModelClick"
            />

            <!-- 表格视图 -->
            <PricingTable
              v-else
              :models="filteredModels"
              :price-rate="priceRate"
              :usd-exchange-rate="usdExchangeRate"
              :token-unit="tokenUnit"
              :show-recharge-price="showRechargePrice"
              @model-click="handleModelClick"
            />
          </main>
        </div>

        <!-- 模型详情抽屉 -->
        <ModelDetailsDrawer
          v-if="selectedModel"
          :open="Boolean(selectedModel)"
          :model="selectedModel"
          :group-ratio="groupRatio"
          :usable-group="usableGroup"
          :endpoint-map="endpointMap"
          :auto-groups="autoGroups"
          :price-rate="priceRate"
          :usd-exchange-rate="usdExchangeRate"
          :token-unit="tokenUnit"
          :show-recharge-price="showRechargePrice"
          @update:open="(v) => { if (!v) selectedModelName = null }"
        />
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens' as *;

.pricing-page {
  position: relative;
  width: 100%;
  min-height: 100vh;

  &__bg {
    position: absolute;
    inset: 0;
    height: 600px;
    pointer-events: none;
    background:
      radial-gradient(ellipse 60% 50% at 20% 20%, color-mix(in srgb, var(--ys-color-primary) 80%, transparent) 0%, transparent 70%),
      radial-gradient(ellipse 50% 40% at 80% 15%, color-mix(in srgb, var(--ys-color-primary) 60%, transparent) 0%, transparent 70%),
      radial-gradient(ellipse 40% 35% at 50% 70%, color-mix(in srgb, var(--ys-color-secondary) 40%, transparent) 0%, transparent 70%);
    opacity: 0.1;
    mask-image: linear-gradient(to bottom, black 40%, transparent 100%);
  }

  &__container {
    position: relative;
    max-width: 1800px;
    padding: 64px $spacing-3 $spacing-8;
    margin: 0 auto;

    @media (width >= 640px) {
      padding: 80px $spacing-6 $spacing-10;
    }

    @media (width >= 1280px) {
      padding: 80px $spacing-8 $spacing-10;
    }
  }

  &__header {
    max-width: 768px;
    margin: 0 auto $spacing-5;
    text-align: center;

    @media (width >= 640px) {
      margin-bottom: $spacing-10;
    }
  }

  &__title {
    margin: 0;
    font-size: clamp(2rem, 5.5vw, 3.5rem);
    font-weight: 700;
    line-height: 1.15;
    letter-spacing: -0.02em;
  }

  &__count {
    margin: $spacing-3 0 0;
    font-size: $font-size-base;
    color: var(--el-text-color-secondary);

    @media (width >= 640px) {
      margin-top: $spacing-4;
      font-size: $font-size-lg;
    }
  }

  &__desc {
    max-width: 512px;
    margin: $spacing-2 auto 0;
    font-size: $font-size-xs;
    line-height: 1.5;
    color: var(--el-text-color-placeholder);

    @media (width >= 640px) {
      font-size: $font-size-base;
    }
  }

  &__layout {
    display: grid;
    gap: $spacing-4;

    @media (width >= 1280px) {
      grid-template-columns: 330px minmax(0, 1fr);
    }
  }

  &__sidebar {
    display: none;

    @media (width >= 1280px) {
      position: sticky;
      top: $spacing-4;
      display: block;
      align-self: start;
      max-height: calc(100vh - $spacing-8);
      overflow-y: auto;
    }
  }

  &__main {
    display: flex;
    flex-direction: column;
    gap: $spacing-4;
    min-width: 0;
  }
}
</style>
