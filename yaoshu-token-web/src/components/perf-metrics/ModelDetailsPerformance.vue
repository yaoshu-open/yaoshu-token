<script setup lang="ts">
import { computed, toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { Monitor, Odometer, Timer, Warning } from '@element-plus/icons-vue'
import { ElSkeleton } from 'element-plus'
import ErrorState from '@/components/ErrorState.vue'
import EmptyState from '@/components/EmptyState.vue'
import PerfMetricCard from './PerfMetricCard.vue'
import PerfGroupTable from './PerfGroupTable.vue'
import LatencyTrendChart, { type LatencyTrendPoint } from './LatencyTrendChart.vue'
import UptimeTrendChart from './UptimeTrendChart.vue'
import type { UptimePoint } from './UptimeSparkline.vue'
import { usePerfMetrics } from '@/composables/perf-metrics/usePerfMetrics'
import { DEFAULT_HOURS } from '@/api/perf-metrics/constants'
import type { PerformanceGroup } from '@/api/perf-metrics/types'
import {
  formatLatency,
  formatThroughput,
  formatUptimePct,
  getSuccessRateLevel,
  type SuccessRateLevel
} from '@/utils/perfFormat'

interface Props {
  model: string
  hours?: number
}

const props = withDefaults(defineProps<Props>(), {
  hours: DEFAULT_HOURS
})

const { t } = useI18n()

const { data, loading, error, reload } = usePerfMetrics(
  toRef(props, 'model'),
  props.hours
)
const groups = computed(() => data.value?.groups ?? [])
function averageAcrossGroups(field: 'avgTps' | 'avgLatencyMs'): number {
  const values = groups.value
    .map((g) => g[field])
    .filter((v) => Number.isFinite(v) && v > 0)
  if (values.length === 0) return 0
  return values.reduce((s, v) => s + v, 0) / values.length
}

function successRateAvg(): number {
  const values = groups.value
    .map((g) => g.successRate)
    .filter((v) => Number.isFinite(v))
  if (values.length === 0) return NaN
  return values.reduce((s, v) => s + v, 0) / values.length
}

const avgTps = computed(() => averageAcrossGroups('avgTps'))
const avgLatency = computed(() =>
  Math.round(averageAcrossGroups('avgLatencyMs'))
)
const successRate = computed(() => successRateAvg())
const successIntent = computed<SuccessRateLevel>(() =>
  getSuccessRateLevel(successRate.value)
)
const latencySeries = computed<LatencyTrendPoint[]>(() => {
  const byTs = new Map<number, number[]>()
  for (const group of groups.value) {
    for (const point of group.series) {
      if (point.avgTtftMs <= 0) continue
      const current = byTs.get(point.ts) ?? []
      current.push(point.avgTtftMs)
      byTs.set(point.ts, current)
    }
  }
  return Array.from(byTs.entries())
    .sort(([a], [b]) => a - b)
    .map(([ts, values]) => ({
      timestamp: new Date(ts * 1000).toISOString(),
      group: 'latency',
      ttft_ms: Math.round(
        values.reduce((sum, v) => sum + v, 0) / values.length
      )
    }))
})
const uptimeSeries = computed<UptimePoint[]>(() => {
  const byTs = new Map<number, { rates: number[]; incidents: number }>()
  for (const group of groups.value) {
    for (const point of group.series) {
      if (!Number.isFinite(point.successRate)) continue
      const current = byTs.get(point.ts) ?? { rates: [], incidents: 0 }
      current.rates.push(point.successRate)
      if (point.successRate < 100) current.incidents += 1
      byTs.set(point.ts, current)
    }
  }
  return Array.from(byTs.entries())
    .sort(([a], [b]) => a - b)
    .map(([ts, value]) => ({
      date: new Date(ts * 1000).toISOString(),
      uptime_pct:
        value.rates.length > 0
          ? Math.round(
              (value.rates.reduce((s, r) => s + r, 0) / value.rates.length) *
                100
            ) / 100
          : 0,
      incidents: value.incidents,
      outage_minutes: 0
    }))
})

// 事故桶计数（successRate<100 的时序桶数）
const incidentCount = computed(() =>
  uptimeSeries.value.reduce((s, p) => s + p.incidents, 0)
)

const successHint = computed(() =>
  incidentCount.value > 0
    ? t('performance.incidentsInWindow', { count: incidentCount.value })
    : t('performance.noIncidents')
)
</script>

<template>
  <div class="model-details-perf">
    <!-- 加载态 -->
    <div
      v-if="loading"
      class="model-details-perf__loading"
    >
      <ElSkeleton
        :rows="10"
        animated
      />
    </div>

    <!-- 错误态 -->
    <ErrorState
      v-else-if="error"
      :title="t('performance.loadFailed')"
      :description="error.message"
      @retry="reload"
    />

    <!-- 空态（无性能数据） -->
    <EmptyState
      v-else-if="groups.length === 0"
      :description="t('performance.noDataForModel')"
    />

    <!-- 数据态 -->
    <template v-else>
      <!-- KPI 指标卡 -->
      <div class="model-details-perf__cards">
        <PerfMetricCard
          :icon="Odometer"
          label="TPS"
          :value="formatThroughput(avgTps)"
          :hint="t('performance.sustainedTps')"
        />
        <PerfMetricCard
          :icon="Timer"
          :label="t('performance.avgLatency')"
          :value="formatLatency(avgLatency)"
        />
        <PerfMetricCard
          :icon="Monitor"
          :label="t('performance.successRate')"
          :value="formatUptimePct(successRate)"
          :hint="successHint"
          :intent="successIntent"
        />
      </div>

      <!-- 分组性能表 -->
      <section class="model-details-perf__section">
        <header class="model-details-perf__head">
          <div class="model-details-perf__head-title">
            <Monitor class="model-details-perf__head-icon" />
            <div>
              <div class="model-details-perf__title">
                {{ t('performance.perGroupPerf') }}
              </div>
              <p class="model-details-perf__desc">
                {{ t('performance.perGroupDesc') }}
              </p>
            </div>
          </div>
        </header>
        <PerfGroupTable :groups="groups as PerformanceGroup[]" />
      </section>

      <!-- 延迟趋势 -->
      <section class="model-details-perf__section">
        <header class="model-details-perf__head">
          <div class="model-details-perf__head-title">
            <Timer class="model-details-perf__head-icon" />
            <div>
              <div class="model-details-perf__title">
                {{ t('performance.latencyTrend') }}
              </div>
              <p class="model-details-perf__desc">
                {{ t('performance.avgTtft') }}
              </p>
            </div>
          </div>
        </header>
        <LatencyTrendChart :series="latencySeries" />
      </section>

      <!-- 可用性趋势 -->
      <section class="model-details-perf__section">
        <header class="model-details-perf__head">
          <div class="model-details-perf__head-title">
            <Monitor class="model-details-perf__head-icon" />
            <div>
              <div class="model-details-perf__title">
                {{ t('performance.availability') }}
              </div>
              <p class="model-details-perf__desc">
                {{
                  incidentCount > 0
                    ? t('performance.availabilityDescIncidents', {
                      incidents: incidentCount
                    })
                    : t('performance.availabilityDesc')
                }}
              </p>
            </div>
          </div>
          <span
            v-if="incidentCount > 0"
            class="model-details-perf__accent"
          >
            <Warning class="model-details-perf__accent-icon" />
            {{ t('performance.incidentsCount', { count: incidentCount }) }}
          </span>
        </header>
        <UptimeTrendChart :series="uptimeSeries" />
      </section>
    </template>
  </div>
</template>

<style scoped lang="scss">
.model-details-perf {
  display: flex;
  flex-direction: column;
  gap: 1rem;

  &__loading {
    padding: 1rem;
  }

  &__cards {
    display: grid;
    grid-template-columns: 1fr;
    gap: 0.5rem;

    @media (width >= 640px) {
      grid-template-columns: repeat(3, 1fr);
    }
  }

  &__section {
    padding: 1rem;
    border: 1px solid var(--el-border-color);
    border-radius: var(--ys-radius-md);
  }

  &__head {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
    align-items: flex-start;
    justify-content: space-between;
    margin-bottom: 0.5rem;
  }

  &__head-title {
    display: flex;
    gap: 0.5rem;
    align-items: flex-start;
    min-width: 0;
  }

  &__head-icon {
    flex-shrink: 0;
    width: 0.875rem;
    height: 0.875rem;
    margin-top: 0.125rem;
    color: var(--el-text-color-secondary);
  }

  &__title {
    font-size: 0.875rem;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__desc {
    margin: 0.125rem 0 0;
    font-size: 0.75rem;
    color: var(--el-text-color-secondary);
  }

  &__accent {
    display: inline-flex;
    flex-shrink: 0;
    gap: 0.25rem;
    align-items: center;
    font-size: 0.75rem;
    font-weight: 500;
    color: var(--el-color-warning);
  }

  &__accent-icon {
    width: 0.875rem;
    height: 0.875rem;
  }
}
</style>
