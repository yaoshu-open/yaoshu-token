<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { PerformanceGroup } from '@/api/perf-metrics/types'
import { formatLatency, formatThroughput } from '@/utils/perfFormat'
import UptimeSparkline, { type UptimePoint } from './UptimeSparkline.vue'

interface Props {
  groups: PerformanceGroup[]
}

defineProps<Props>()

const { t } = useI18n()

// 单分组的时序 → 可用性样本（成功率<100 计为事故桶）
function toUptimeSeries(group: PerformanceGroup): UptimePoint[] {
  return group.series.map((point) => ({
    date: new Date(point.ts * 1000).toISOString(),
    uptime_pct: Math.round(point.successRate * 100) / 100,
    incidents: point.successRate < 100 ? 1 : 0,
    outage_minutes: 0
  }))
}
</script>

<template>
  <el-table
    :data="groups"
    size="small"
    class="perf-group-table"
  >
    <el-table-column
      :label="t('performance.group')"
      min-width="100"
    >
      <template #default="{ row }">
        <el-tag
          size="small"
          effect="plain"
        >
          {{ row.group }}
        </el-tag>
      </template>
    </el-table-column>

    <el-table-column
      label="TPS"
      align="right"
      min-width="90"
    >
      <template #default="{ row }">
        <span class="perf-group-table__num">{{ formatThroughput(row.avgTps) }}</span>
      </template>
    </el-table-column>

    <el-table-column
      :label="t('performance.avgTtft')"
      align="right"
      min-width="100"
    >
      <template #default="{ row }">
        <span class="perf-group-table__num">{{ formatLatency(row.avgTtftMs) }}</span>
      </template>
    </el-table-column>

    <el-table-column
      :label="t('performance.avgLatency')"
      align="right"
      min-width="110"
    >
      <template #default="{ row }">
        <span class="perf-group-table__num perf-group-table__num--muted">
          {{ formatLatency(row.avgLatencyMs) }}
        </span>
      </template>
    </el-table-column>

    <el-table-column
      :label="t('performance.successRate')"
      min-width="180"
    >
      <template #default="{ row }">
        <UptimeSparkline
          :series="toUptimeSeries(row as PerformanceGroup)"
          size="sm"
        />
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped lang="scss">
.perf-group-table {
  :deep(.el-table__cell) {
    padding: 6px 0;
  }

  &__num {
    font-family: var(--el-font-family-mono, monospace);
    font-size: var(--ys-font-size-sm);
    font-variant-numeric: tabular-nums;

    &--muted {
      color: var(--el-text-color-secondary);
    }
  }
}
</style>
