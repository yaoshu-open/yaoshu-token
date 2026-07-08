<script setup lang="ts">
import { computed } from 'vue'
import { formatUptimePct } from '@/utils/perfFormat'

/** 可用性样本点（视图模型，由 ModelDetailsPerformance 从时序聚合产出） */
export interface UptimePoint {
  date: string
  uptime_pct: number
  incidents: number
  outage_minutes: number
}

interface Props {
  series: UptimePoint[]
  size?: 'sm' | 'md'
  showOverall?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  size: 'md',
  showOverall: true
})

const overall = computed(() => {
  if (props.series.length === 0) return NaN
  return props.series.reduce((s, p) => s + p.uptime_pct, 0) / props.series.length
})

const overallText = computed(() => formatUptimePct(overall.value))

// T-PERF-03 tooltip 精化：可读日期 + 可用率 + 事故数 + 停机时长
function tooltipText(day: UptimePoint): string {
  const date = new Date(day.date)
  const dateLabel = isNaN(date.getTime())
    ? day.date
    : date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
  const parts = [dateLabel, `${Number(day.uptime_pct).toFixed(2)}%`]
  if (day.incidents > 0) parts.push(`${day.incidents} incidents`)
  if (day.outage_minutes > 0) parts.push(`${day.outage_minutes}min outage`)
  return parts.join(' · ')
}
</script>

<template>
  <div
    v-if="series.length === 0"
    class="uptime-sparkline uptime-sparkline--empty"
  >
    —
  </div>
  <div
    v-else
    class="uptime-sparkline"
  >
    <div
      class="uptime-sparkline__bars"
      :class="`uptime-sparkline__bars--${size}`"
      role="img"
      :aria-label="`${series.length} point uptime ${Number(overall).toFixed(2)}%`"
    >
      <el-tooltip
        v-for="(day, idx) in series"
        :key="idx"
        :content="tooltipText(day)"
        placement="top"
      >
        <span
          class="uptime-sparkline__bar"
          :class="[
            `uptime-sparkline__bar--${size}`,
            `uptime-sparkline__bar--level-${day.uptime_pct >= 99.9 ? 'ok' : day.uptime_pct >= 99 ? 'warn' : day.uptime_pct >= 95 ? 'degraded' : 'down'}`,
            day.uptime_pct >= 99.9 ? 'h-full' : day.uptime_pct >= 99 ? 'h-88' : day.uptime_pct >= 95 ? 'h-72' : day.uptime_pct >= 90 ? 'h-55' : 'h-40'
          ]"
        />
      </el-tooltip>
    </div>
    <span
      v-if="showOverall"
      class="uptime-sparkline__overall"
      :class="{
        'uptime-sparkline__overall--ok': overall >= 99,
        'uptime-sparkline__overall--degraded': overall >= 95 && overall < 99,
        'uptime-sparkline__overall--down': overall < 95
      }"
    >
      {{ overallText }}
    </span>
  </div>
</template>

<style scoped lang="scss">
.uptime-sparkline {
  display: flex;
  gap: 0.5rem;
  align-items: center;

  &--empty {
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
  }

  &__bars {
    display: flex;
    gap: 2px;
    align-items: flex-end;

    &--sm {
      height: 14px;
    }

    &--md {
      height: 20px;
    }
  }

  &__bar {
    display: block;
    width: 3px;
    border-radius: 2px;
    transition: opacity 0.15s;

    &:hover {
      opacity: 0.8;
    }

    &--sm {
      width: 3px;
    }

    &--md {
      width: 4px;
    }

    // 高度修饰（严重度越低条越短）
    &.h-full {
      height: 100%;
    }

    &.h-88 {
      height: 88%;
    }

    &.h-72 {
      height: 72%;
    }

    &.h-55 {
      height: 55%;
    }

    &.h-40 {
      height: 40%;
    }
    &--level-ok {
      background: var(--el-color-success);
    }

    &--level-warn {
      background: var(--el-color-success-light-3);
    }

    &--level-degraded {
      background: var(--el-color-warning);
    }

    &--level-down {
      background: var(--el-color-danger);
    }
  }

  &__overall {
    font-family: var(--el-font-family-mono, monospace);
    font-size: var(--ys-font-size-base);
    font-weight: 600;
    font-variant-numeric: tabular-nums;

    &--ok {
      color: var(--el-color-success);
    }

    &--degraded {
      color: var(--el-color-warning);
    }

    &--down {
      color: var(--el-color-danger);
    }
  }
}
</style>
