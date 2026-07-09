<script setup lang="ts">
/**
 * 调用统计看板容器（对齐 PD-08）。
 * 独立路由 /analytics，承载原 Dashboard 内违规嵌入的 models/users 板块。
 * Tab 切换：模型分析（所有用户）+ 用户分析（admin only）。
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElTabs, ElTabPane } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import DashboardHeader from '@/components/dashboard/DashboardHeader.vue'
import AnalyticsStatCards from '@/components/analytics/AnalyticsStatCards.vue'
import ConsumptionDistributionChart from '@/components/analytics/ConsumptionDistributionChart.vue'
import ModelCharts from '@/components/analytics/ModelCharts.vue'
import ModelsChartPreferences from '@/components/analytics/ModelsChartPreferences.vue'
import ModelsFilter from '@/components/analytics/ModelsFilter.vue'
import UserRankChart from '@/components/analytics/UserRankChart.vue'
import UserTrendChart from '@/components/analytics/UserTrendChart.vue'
import PerfSummaryPanel from '@/components/perf-metrics/PerfSummaryPanel.vue'
import { useUserPermissions } from '@/composables/useUserPermissions'
import { useDashboardData } from '@/composables/dashboard/useDashboardData'
import { useChartPreferences } from '@/composables/analytics/useChartPreferences'
import { useModelAnalytics } from '@/composables/analytics/useModelAnalytics'
import { useUserAnalytics } from '@/composables/analytics/useUserAnalytics'
import { buildDefaultDashboardFilters } from '@/api/dashboard/lib'
import type { DashboardChartPreferences, DashboardFilters } from '@/api/dashboard/types'

type AnalyticsTab = 'models' | 'users'

const { t } = useI18n()
const { isAdmin } = useUserPermissions()
const { userInfo, loading: headerLoading, refetch: refetchHeader } = useDashboardData()

const { preferences, setPreferences } = useChartPreferences()
const filters = ref<DashboardFilters>(buildDefaultDashboardFilters(preferences.value))

const {
  loading: modelLoading,
  rawData: modelRawData,
  fetch: fetchModelAnalytics,
  buildStatCards,
  buildCountProportion,
  buildQuotaTrend,
  buildCountTrend,
  buildCountRank,
} = useModelAnalytics()

const {
  loading: userLoading,
  userRank,
  buildUserTrend,
  fetch: fetchUserAnalytics,
} = useUserAnalytics()

// Tab 切换：admin 默认 models，可切 users；普通用户仅 models
const activeTab = ref<AnalyticsTab>('models')

// 懒加载标记
const userFetched = ref(false)

// 共享筛选：模型/用户分析共用 filters
async function applyFilters(next: DashboardFilters) {
  filters.value = next
  await fetchModelAnalytics(next)
  if (activeTab.value === 'users' && isAdmin.value) {
    await fetchUserAnalytics(next)
  }
}

function resetFilters() {
  const reset = buildDefaultDashboardFilters(preferences.value)
  filters.value = reset
  applyFilters(reset)
}

// 统一刷新：header 数据 + 模型/用户分析
function handleRefresh() {
  refetchHeader()
  applyFilters(filters.value)
}

function handlePreferencesChange(next: DashboardChartPreferences) {
  setPreferences(next)
  // 偏好变更后重置 filters 触发 refetch
  resetFilters()
}

// 切换 tab 时按需拉取用户分析数据
watch(activeTab, async (tab) => {
  if (tab === 'users' && isAdmin.value && !userFetched.value) {
    userFetched.value = true
    await fetchUserAnalytics(filters.value)
  }
})

// 首次进入：拉取模型分析数据
fetchModelAnalytics(filters.value)

const statCards = computed(() => buildStatCards(filters.value))
const quotaDistributionTrend = computed(() => buildQuotaTrend(filters.value, 10))
const countTrend = computed(() => buildCountTrend(filters.value, 10))
const countProportion = computed(() => buildCountProportion())
const countRank = computed(() => buildCountRank(20))
const userTrend = computed(() => buildUserTrend(filters.value, 5))

// 响应式联动：rawData 变化时 statCards 重新计算（computed 自动追踪 rawData）
void modelRawData
</script>

<template>
  <div class="analytics-page">
    <DashboardHeader
      :user-info="userInfo"
      :loading="headerLoading"
      @refresh="handleRefresh"
    >
      <template #actions>
        <div class="analytics-page__header-actions">
          <ModelsChartPreferences
            :preferences="preferences"
            @update:preferences="handlePreferencesChange"
          />
          <ModelsFilter
            :filters="filters"
            :is-admin="isAdmin"
            @update:filters="applyFilters"
            @reset="resetFilters"
          />
          <el-button
            size="small"
            :icon="Refresh"
            :loading="modelLoading || userLoading"
            @click="handleRefresh"
          >
            {{ t('common.refresh') }}
          </el-button>
        </div>
      </template>
    </DashboardHeader>

    <el-tabs v-model="activeTab" class="analytics-page__tabs">
      <!-- 模型分析板块（所有用户） -->
      <el-tab-pane
        name="models"
        :label="t('analytics.section.models')"
      >
        <div class="analytics-page__section">
          <AnalyticsStatCards
            :stats="statCards"
            :loading="modelLoading"
          />

          <PerfSummaryPanel
            v-if="isAdmin"
            :hours="24"
          />

          <div class="analytics-page__chart-card">
            <div class="analytics-page__chart-title">
              {{ t('analytics.chart.consumptionDistribution') }}
            </div>
            <ConsumptionDistributionChart
              :data="quotaDistributionTrend"
              :loading="modelLoading"
              :default-chart-type="preferences.consumptionDistributionChart"
            />
          </div>

          <div class="analytics-page__chart-card">
            <div class="analytics-page__chart-title">
              {{ t('analytics.chart.modelTrend') }}
            </div>
            <ModelCharts
              :trend="countTrend"
              :proportion="countProportion"
              :rank="countRank"
              :loading="modelLoading"
              :default-chart-tab="preferences.modelAnalyticsChart"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- 用户分析板块（admin only） -->
      <el-tab-pane
        v-if="isAdmin"
        name="users"
        :label="t('analytics.section.users')"
      >
        <div class="analytics-page__section">
          <div class="analytics-page__charts">
            <div class="analytics-page__chart-card">
              <div class="analytics-page__chart-title">
                {{ t('analytics.chart.userRank') }}
              </div>
              <UserRankChart
                :data="userRank"
                :loading="userLoading"
              />
            </div>
            <div class="analytics-page__chart-card">
              <div class="analytics-page__chart-title">
                {{ t('analytics.chart.userTrend') }}
              </div>
              <UserTrendChart
                :data="userTrend"
                :loading="userLoading"
              />
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens' as *;

.analytics-page {
  display: flex;
  flex-direction: column;
  gap: $spacing-6;
  padding: $spacing-8 $spacing-6;

  &__header-actions {
    display: flex;
    flex-shrink: 0;
    gap: $spacing-2;
    align-items: center;
  }

  &__tabs {
    :deep(.el-tabs__header) {
      margin-bottom: $spacing-4;
    }
  }

  &__section {
    display: flex;
    flex-direction: column;
    gap: $spacing-4;
  }

  &__charts {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: $spacing-4;

    @media (width <= 1024px) {
      grid-template-columns: 1fr;
    }
  }

  &__chart-card {
    padding: $spacing-5;
    background: var(--el-bg-color-overlay);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--ys-radius-lg);
    transition: border-color 0.2s;

    &:hover {
      border-color: var(--el-border-color);
    }
  }

  &__chart-title {
    display: flex;
    gap: $spacing-2;
    align-items: center;
    margin-bottom: $spacing-4;
    font-size: $font-size-base;
    font-weight: $font-weight-semibold;
    color: var(--el-text-color-primary);

    // 标题前品牌色竖条，强化视觉层级
    &::before {
      display: inline-block;
      width: 3px;
      height: 16px;
      content: '';
      background: var(--ys-color-primary);
      border-radius: var(--ys-radius-sm);
    }
  }
}
</style>
