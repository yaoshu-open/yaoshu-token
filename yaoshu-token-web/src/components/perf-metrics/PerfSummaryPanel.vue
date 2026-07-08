<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Monitor, Odometer, Timer } from '@element-plus/icons-vue'
import { ElSkeleton } from 'element-plus'
import ErrorState from '@/components/ErrorState.vue'
import EmptyState from '@/components/EmptyState.vue'
import PerfMetricCard from './PerfMetricCard.vue'
import PerfModelBadge from './PerfModelBadge.vue'
import { usePerfMetricsSummary } from '@/composables/perf-metrics/usePerfMetricsSummary'
import { DEFAULT_HOURS, TOP_MODEL_LIMIT } from '@/api/perf-metrics/constants'
import type { PerfModelSummary } from '@/api/perf-metrics/types'
import {
  formatLatency,
  formatThroughput,
  formatUptimePct,
  getSuccessRateLevel,
  type SuccessRateLevel
} from '@/utils/perfFormat'

interface Props {
  hours?: number
}

const props = withDefaults(defineProps<Props>(), {
  hours: DEFAULT_HOURS
})

const { t } = useI18n()

const { data, loading, error, reload } = usePerfMetricsSummary(props.hours)

const models = computed<PerfModelSummary[]>(() => data.value?.models ?? [])
function simpleAverage(
  metric: 'avgLatencyMs' | 'avgTps' | 'successRate',
  isValid: (value: number) => boolean
): number {
  let total = 0
  let count = 0
  for (const row of models.value) {
    const value = Number(row[metric])
    if (!isValid(value)) continue
    total += value
    count++
  }
  return count > 0 ? total / count : NaN
}

const avgLatency = computed(() =>
  Math.round(
    simpleAverage('avgLatencyMs', (v) => Number.isFinite(v) && v > 0)
  )
)
const avgTps = computed(() =>
  simpleAverage('avgTps', (v) => Number.isFinite(v) && v > 0)
)
const successRate = computed(() =>
  simpleAverage('successRate', Number.isFinite)
)
const successIntent = computed<SuccessRateLevel>(() =>
  getSuccessRateLevel(successRate.value)
)

// Top 模型（后端已按 requestCount 降序，截取前 N）
const topModels = computed(() => models.value.slice(0, TOP_MODEL_LIMIT))
</script>

<template>
  <section class="perf-summary-panel">
    <header class="perf-summary-panel__head">
      <div class="perf-summary-panel__title-wrap">
        <Monitor class="perf-summary-panel__icon" />
        <h3 class="perf-summary-panel__title">
          {{ t('performance.health') }}
        </h3>
      </div>
      <span class="perf-summary-panel__subtitle">
        {{ t('performance.windowHint', { hours: props.hours }) }}
      </span>
    </header>

    <div class="perf-summary-panel__body">
      <!-- 加载态 -->
      <div
        v-if="loading"
        class="perf-summary-panel__loading"
      >
        <div class="perf-summary-panel__cards">
          <ElSkeleton
            v-for="i in 3"
            :key="i"
            :rows="2"
            animated
            class="perf-summary-panel__skel"
          />
        </div>
      </div>

      <!-- 错误态 -->
      <ErrorState
        v-else-if="error"
        :title="t('performance.loadFailed')"
        :description="error.message"
        @retry="reload"
      />

      <!-- 空态 -->
      <EmptyState
        v-else-if="models.length === 0"
        :description="t('performance.noSummaryData')"
      />

      <!-- 数据态 -->
      <template v-else>
        <div class="perf-summary-panel__cards">
          <PerfMetricCard
            :icon="Monitor"
            :label="t('performance.successRate')"
            :value="formatUptimePct(successRate)"
            :intent="successIntent"
          />
          <PerfMetricCard
            :icon="Timer"
            :label="t('performance.avgLatency')"
            :value="formatLatency(avgLatency)"
          />
          <PerfMetricCard
            :icon="Odometer"
            :label="t('performance.throughput')"
            :value="formatThroughput(avgTps)"
          />
        </div>

        <div
          v-if="topModels.length"
          class="perf-summary-panel__models"
        >
          <span class="perf-summary-panel__models-label">
            {{ t('performance.topModelsByTraffic') }}
          </span>
          <div class="perf-summary-panel__badges">
            <PerfModelBadge
              v-for="model in topModels"
              :key="model.modelName"
              :model="model"
            />
          </div>
        </div>
      </template>
    </div>
  </section>
</template>

<style scoped lang="scss">
.perf-summary-panel {
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: var(--ys-radius-xl);

  &__head {
    display: flex;
    gap: 0.5rem;
    align-items: center;
    padding: 0.75rem 1.25rem;
    border-bottom: 1px solid var(--el-border-color);
  }

  &__title-wrap {
    display: flex;
    gap: 0.5rem;
    align-items: center;
  }

  &__icon {
    flex-shrink: 0;
    width: 1rem;
    height: 1rem;
    color: var(--el-text-color-secondary);
  }

  &__title {
    margin: 0;
    font-size: 0.875rem;
    font-weight: 600;
  }

  &__subtitle {
    margin-left: auto;
    font-size: 0.75rem;
    color: var(--el-text-color-secondary);
  }

  &__body {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
    padding: 1rem 1.25rem;
  }

  &__cards {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 0.5rem;

    @media (width <= 640px) {
      grid-template-columns: 1fr;
    }
  }

  &__loading {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
  }

  &__skel {
    padding: 0.625rem 0.75rem;
  }

  &__models {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
  }

  &__models-label {
    font-size: 11px;
    font-weight: 500;
    color: var(--el-text-color-secondary);
  }

  &__badges {
    display: flex;
    flex-wrap: wrap;
    gap: 0.375rem;
  }
}
</style>
