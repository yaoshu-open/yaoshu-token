<script setup lang="ts">
// 复用 @/utils/perfFormat 的格式化函数。
import { ref, watch, onMounted } from 'vue'
import { getPerfMetrics } from '@/api/perf-metrics'
import { formatUptimePct, getSuccessRateLevel } from '@/utils/perfFormat'
import type { PerformanceGroup } from '@/api/perf-metrics/types'

const props = defineProps<{
  modelName: string
}>()

const avgSuccessRate = ref<number>(NaN)
const loading = ref(true)

async function load() {
  if (!props.modelName) return
  loading.value = true
  try {
    const data = await getPerfMetrics(props.modelName, 24)
    const groups = data?.groups ?? []
    const rates = groups
      .map((g: PerformanceGroup) => g.successRate)
      .filter((r) => Number.isFinite(r))
    avgSuccessRate.value =
      rates.length > 0 ? rates.reduce((s, r) => s + r, 0) / rates.length : NaN
  } catch {
    avgSuccessRate.value = NaN
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.modelName, load)
</script>

<template>
  <span
    v-if="loading"
    class="model-perf-badge model-perf-badge--loading"
  >···</span>
  <span
    v-else-if="!Number.isNaN(avgSuccessRate)"
    class="model-perf-badge"
    :class="`model-perf-badge--${getSuccessRateLevel(avgSuccessRate)}`"
  >
    {{ formatUptimePct(avgSuccessRate) }}
  </span>
</template>

<style scoped lang="scss">
.model-perf-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  font-size: 11px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  border-radius: var(--ys-radius-sm);

  &--loading {
    color: var(--el-text-color-secondary);
  }

  &--success {
    color: var(--el-color-success);
    background: var(--el-color-success-light-9);
  }

  &--warning {
    color: var(--el-color-warning);
    background: var(--el-color-warning-light-9);
  }

  &--danger {
    color: var(--el-color-danger);
    background: var(--el-color-danger-light-9);
  }

  &--unknown {
    color: var(--el-text-color-secondary);
  }
}
</style>
