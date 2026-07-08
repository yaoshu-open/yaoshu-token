<script setup lang="ts">
/**
 * Dashboard 个人概览单页（PD-01）。
 * 数据看板（模型/用户分析）已迁移至独立路由 /analytics（PD-08）。
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDashboardData } from '@/composables/dashboard/useDashboardData'
import { useSystemConfigStore } from '@/store/modules/system-config'
import { useUserPermissions } from '@/composables/useUserPermissions'
import DashboardHeader from '@/components/dashboard/DashboardHeader.vue'
import SummaryCards from '@/components/dashboard/SummaryCards.vue'
import SetupGuide from '@/components/dashboard/SetupGuide.vue'
import AnnouncementsPanel from '@/components/dashboard/AnnouncementsPanel.vue'
import ApiInfoPanel from '@/components/dashboard/ApiInfoPanel.vue'
import FaqPanel from '@/components/dashboard/FaqPanel.vue'
import UptimePanel from '@/components/dashboard/UptimePanel.vue'
import PerfSummaryPanel from '@/components/perf-metrics/PerfSummaryPanel.vue'

const { t } = useI18n()
const {
  userInfo,
  quotaDates,
  tokens,
  availableModels,
  preferredKey,
  recentUsage: _recentUsage,
  sparklineData,
  loading,
  refetch,
} = useDashboardData()

const systemConfig = useSystemConfigStore()
const { isAdmin } = useUserPermissions()

const announcements = computed(() => {
  const raw = systemConfig.rawStatus?.announcements
  return Array.isArray(raw) ? raw : []
})

const remainQuota = computed(() => {
  const q = userInfo.value?.quota ?? 0
  const used = userInfo.value?.usedQuota ?? 0
  return Math.max(0, q - used)
})

const usedQuota = computed(() => userInfo.value?.usedQuota ?? 0)
const requestCount = computed(() => userInfo.value?.requestCount ?? 0)

const stepsDone = computed(() => ({
  hasKey: !!preferredKey.value,
  hasTopup: remainQuota.value > 0 || usedQuota.value > 0,
  hasRequest: requestCount.value > 0,
}))

const allStepsDone = computed(() =>
  stepsDone.value.hasKey && stepsDone.value.hasTopup && stepsDone.value.hasRequest,
)

const STORAGE_KEY = 'dashboard_overview_setup_guide_expanded'
const setupGuideExpanded = ref(!allStepsDone.value)

function readStored(): boolean | null {
  try {
    const v = localStorage.getItem(STORAGE_KEY)
    return v === null ? null : v === 'true'
  } catch {
    return null
  }
}

function writeStored(v: boolean) {
  try {
    localStorage.setItem(STORAGE_KEY, String(v))
  } catch {
    /* privacy mode */
  }
}

watch(
  allStepsDone,
  (done) => {
    const stored = readStored()
    if (stored === null) {
      setupGuideExpanded.value = !done
    } else {
      setupGuideExpanded.value = stored
    }
  },
  { immediate: true },
)

function handleToggle(expanded: boolean) {
  setupGuideExpanded.value = expanded
  writeStored(expanded)
}

void tokens
void _recentUsage
void t
</script>

<template>
  <div class="dashboard-page">
    <DashboardHeader
      :user-info="userInfo"
      :loading="loading"
      @refresh="refetch"
    />

    <SummaryCards
      :user-info="userInfo"
      :quota-dates="quotaDates"
      :sparkline-data="sparklineData"
      :loading="loading"
    />

    <PerfSummaryPanel
      v-if="isAdmin"
      :hours="24"
    />

    <!-- 新手引导：独立区块，折叠时不影响下方布局 -->
    <SetupGuide
      :user-info="userInfo"
      :preferred-key="preferredKey"
      :available-models="availableModels"
      :request-count="requestCount"
      :remain-quota="remainQuota"
      :used-quota="usedQuota"
      :expanded="setupGuideExpanded"
      @toggle="handleToggle"
    />

    <!-- 信息面板区：所有用户均渲染（空态引导管理员配置，普通用户感知功能存在） -->
    <div class="dashboard-page__panels">
      <AnnouncementsPanel
        :items="announcements"
        :loading="false"
      />
      <ApiInfoPanel />
      <FaqPanel />
      <UptimePanel />
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens' as *;

.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: $spacing-6;
  padding: $spacing-8 $spacing-6;

  // 信息面板区：响应式 grid，面板按内容自然流动
  // - ≥640px：两列（面板肩并肩）
  // - <640px：单列堆叠
  &__panels {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
    gap: $spacing-4;
  }
}
</style>
