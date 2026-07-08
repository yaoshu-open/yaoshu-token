<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Wallet } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { formatQuotaWithCurrency } from '@/utils/currency'
import StatCard from './StatCard.vue'
import type { QuotaDate } from '@/api/dashboard/types'
import type { UserInfo } from '@/api/user/types'

interface SummaryCardsProps {
  userInfo: UserInfo | null
  quotaDates: QuotaDate[]
  sparklineData: { usage: number[]; requests: number[] }
  loading?: boolean
}

const props = defineProps<SummaryCardsProps>()
const { t } = useI18n()
const router = useRouter()

const remainQuota = computed<number>(() => {
  const q = props.userInfo?.quota ?? 0
  const used = props.userInfo?.usedQuota ?? 0
  return Math.max(0, q - used)
})

const usedQuota = computed<number>(() => props.userInfo?.usedQuota ?? 0)
const requestCount = computed<number>(() => props.userInfo?.requestCount ?? 0)

const recentUsage = computed<number>(() =>
  props.quotaDates.reduce((s, x) => s + (x.quota || 0), 0),
)

const todayUsageText = computed(() => formatQuotaWithCurrency(recentUsage.value))
const totalUsageText = computed(() => formatQuotaWithCurrency(usedQuota.value))
const requestCountText = computed(() => new Intl.NumberFormat().format(requestCount.value))

type HealthLevel = 'healthy' | 'caution' | 'critical'
const health = computed<{ level: HealthLevel; runway: number | null }>(() => {
  if (remainQuota.value <= 0) return { level: 'critical', runway: null }
  if (recentUsage.value <= 0) return { level: 'healthy', runway: null }
  const runway = remainQuota.value / recentUsage.value
  return {
    level: runway >= 3 ? 'healthy' : 'caution',
    runway,
  }
})

const healthDotColor = computed(() => {
  switch (health.value.level) {
    case 'healthy':
      return 'var(--el-color-success)'
    case 'caution':
      return 'var(--el-color-warning)'
    case 'critical':
      return 'var(--el-color-danger)'
  }
})

const remainQuotaText = computed(() => formatQuotaWithCurrency(remainQuota.value))
const runwayText = computed(() => {
  if (health.value.runway == null) return '-'
  return `${Number(health.value.runway).toFixed(1)} ${t('dashboard.days')}`
})

function goWallet() {
  router.push('/wallet')
}
</script>

<template>
  <div class="summary-cards">
    <div class="summary-cards__row">
      <StatCard
        :title="t('dashboard.todayUsage')"
        :value="todayUsageText"
        :description="t('dashboard.last24h')"
        tone="rose"
        :sparkline="sparklineData.usage"
        :loading="loading"
      />
      <StatCard
        :title="t('dashboard.totalUsage')"
        :value="totalUsageText"
        :description="t('dashboard.cumulative')"
        tone="teal"
        :sparkline="sparklineData.usage"
        :loading="loading"
      />
      <StatCard
        :title="t('dashboard.requestCount')"
        :value="requestCountText"
        :description="t('dashboard.totalRequests')"
        tone="gray"
        :sparkline="sparklineData.requests"
        :loading="loading"
      />
    </div>

    <div class="summary-cards__health">
      <div class="summary-cards__health-header">
        <span class="summary-cards__health-title">{{ t('dashboard.balanceHealth') }}</span>
        <span
          class="summary-cards__health-dot"
          :style="{ background: healthDotColor }"
        />
      </div>
      <div class="summary-cards__health-value">
        {{ remainQuotaText }}
      </div>
      <div class="summary-cards__health-runway">
        <span class="summary-cards__health-label">{{ t('dashboard.runway') }}:</span>
        <span class="summary-cards__health-num">{{ runwayText }}</span>
      </div>
      <ElButton
        type="primary"
        plain
        size="small"
        :icon="Wallet"
        @click="goWallet"
      >
        {{ t('dashboard.goWallet') }}
      </ElButton>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens' as *;

.summary-cards {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 280px;
  gap: $spacing-4;

  @media (width <= 1200px) {
    grid-template-columns: 1fr 1fr;

    .summary-cards__health {
      grid-column: span 2;
    }
  }

  @media (width <= 640px) {
    grid-template-columns: 1fr;

    .summary-cards__health {
      grid-column: auto;
    }
  }

  &__row {
    display: contents;
  }

  &__health {
    display: flex;
    flex-direction: column;
    gap: $spacing-2;
    padding: $spacing-5;
    background: var(--el-fill-color-blank);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: $radius-lg;
  }

  &__health-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__health-title {
    font-size: $font-size-sm;
    color: var(--el-text-color-secondary);
  }

  &__health-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
  }

  &__health-value {
    font-size: $font-size-2xl;
    font-weight: $font-weight-semibold;
    font-variant-numeric: tabular-nums;
    letter-spacing: -0.025em;
  }

  &__health-runway {
    display: flex;
    gap: $spacing-1;
    font-size: $font-size-xs;
    color: var(--el-text-color-placeholder);
  }

  &__health-num {
    font-variant-numeric: tabular-nums;
  }
}
</style>
