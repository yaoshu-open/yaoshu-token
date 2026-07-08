<script setup lang="ts">
// 使用 mock-stats.ts 的 buildAppRankings 生成 mock 数据。
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRight, Trophy } from '@element-plus/icons-vue'
import { buildAppRankings, formatTokenVolume } from '@/views/pricing/lib/mock-stats'
import type { PricingModel } from '@/api/pricing/types'

const props = defineProps<{
  model: PricingModel
}>()

const { t } = useI18n()

const apps = computed(() => buildAppRankings(props.model, 12))

const totalMonthlyTokens = computed(() =>
  apps.value.reduce((s, a) => s + a.monthly_tokens, 0)
)

const COMPACT_NUMBER = new Intl.NumberFormat(undefined, {
  notation: 'compact',
  maximumFractionDigits: 1
})

function isPodium(rank: number) {
  return rank <= 3
}

function rankClass(rank: number) {
  if (rank === 1) return 'rank-badge--gold'
  if (rank === 2) return 'rank-badge--silver'
  if (rank === 3) return 'rank-badge--bronze'
  return ''
}

function growthClass(value: number) {
  if (value > 0) return 'growth-chip--up'
  if (value < 0) return 'growth-chip--down'
  return 'growth-chip--neutral'
}

function formatGrowth(value: number) {
  const n = Number(value)
  return `${n > 0 ? '+' : ''}${n.toFixed(1)}%`
}
</script>

<template>
  <div
    v-if="apps.length === 0"
    class="apps-empty"
  >
    {{ t('pricing.noAppData') }}
  </div>
  <div
    v-else
    class="apps"
  >
    <div class="apps__stats">
      <div class="apps__stat-card">
        <div class="apps__stat-label">
          {{ t('pricing.trackedApps') }}
        </div>
        <div class="apps__stat-value">
          {{ apps.length }}
        </div>
        <p class="apps__stat-hint">
          {{ t('pricing.topIntegrations') }}
        </p>
      </div>
      <div class="apps__stat-card">
        <div class="apps__stat-label">
          {{ t('pricing.monthlyTokens') }}
        </div>
        <div class="apps__stat-value">
          {{ COMPACT_NUMBER.format(totalMonthlyTokens) }}
        </div>
        <p class="apps__stat-hint">
          {{ t('pricing.aggregatedApps') }}
        </p>
      </div>
      <div class="apps__stat-card">
        <div class="apps__stat-label">
          {{ t('pricing.topByUsage') }}
        </div>
        <div class="apps__stat-value apps__stat-value--name">
          {{ apps[0].name }}
        </div>
        <p class="apps__stat-hint">
          {{ apps[0].category }} · {{ formatTokenVolume(apps[0].monthly_tokens) }} {{ t('pricing.tokensPerMo') }}
        </p>
      </div>
    </div>

    <el-table
      :data="apps"
      size="small"
      class="apps__table"
    >
      <el-table-column
        width="56"
        align="center"
      >
        <template #header>
          #
        </template>
        <template #default="{ row }">
          <span
            class="rank-badge"
            :class="rankClass(row.rank)"
          >
            <el-icon
              v-if="isPodium(row.rank)"
              :size="12"
            ><Trophy /></el-icon>
            <template v-else>{{ row.rank }}</template>
          </span>
        </template>
      </el-table-column>
      <el-table-column :label="t('pricing.app')">
        <template #default="{ row }">
          <div class="app-cell">
            <span class="app-cell__initial">{{ row.initial }}</span>
            <div class="app-cell__info">
              <a
                v-if="row.url"
                :href="row.url"
                target="_blank"
                rel="noreferrer"
                class="app-cell__name"
              >
                {{ row.name }}
              </a>
              <span
                v-else
                class="app-cell__name"
              >{{ row.name }}</span>
              <p class="app-cell__desc">
                {{ row.description }}
              </p>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('pricing.category')"
        class-name="hidden-md-only"
      >
        <template #default="{ row }">
          {{ row.category }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('pricing.monthlyTokens')"
        align="right"
      >
        <template #default="{ row }">
          {{ formatTokenVolume(row.monthly_tokens) }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('pricing.change30d')"
        align="right"
        width="100"
      >
        <template #default="{ row }">
          <span
            class="growth-chip"
            :class="growthClass(row.growthPct)"
          >
            <el-icon v-if="row.growthPct > 0"><ArrowRight /></el-icon>
            <el-icon v-else-if="row.growthPct < 0"><ArrowRight /></el-icon>
            {{ formatGrowth(row.growthPct) }}
          </span>
        </template>
      </el-table-column>
    </el-table>

    <p class="apps__disclaimer">
      {{ t('pricing.appRankingsSimulated') }}
    </p>
  </div>
</template>

<style scoped lang="scss">
.apps-empty {
  padding: var(--ys-spacing-6);
  font-size: var(--ys-font-size-base);
  color: var(--el-text-color-secondary);
  text-align: center;
  border: 1px solid var(--el-border-color);
  border-radius: var(--ys-radius-md);
}

.apps {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);

  &__stats {
    display: grid;
    grid-template-columns: repeat(1, 1fr);
    gap: var(--ys-spacing-2);

    @media (width >= 640px) {
      grid-template-columns: repeat(3, 1fr);
    }
  }

  &__stat-card {
    padding: var(--ys-spacing-3);
    background: var(--el-fill-color-lighter);
    border: 1px solid var(--el-border-color);
    border-radius: var(--ys-radius-md);
  }

  &__stat-label {
    font-size: 10px;
    font-weight: 500;
    color: var(--el-text-color-secondary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  &__stat-value {
    margin-top: 4px;
    font-size: 18px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;

    &--name {
      overflow: hidden;
      text-overflow: ellipsis;
      font-size: 15px;
      white-space: nowrap;
    }
  }

  &__stat-hint {
    margin: 2px 0 0;
    font-size: 11px;
    color: var(--el-text-color-placeholder);
  }

  &__disclaimer {
    font-size: 11px;
    line-height: 1.5;
    color: var(--el-text-color-placeholder);
  }
}

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  font-family: monospace;
  font-size: var(--ys-font-size-xs);
  font-weight: 700;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color);
  border-radius: var(--ys-radius-base);

  &--gold { color: #b45309; background: #fef3c7; }
  &--silver { color: #475569; background: #f1f5f9; }
  &--bronze { color: #c2410c; background: #fed7aa; }
}

.app-cell {
  display: flex;
  gap: var(--ys-spacing-3);
  align-items: center;

  &__initial {
    display: inline-flex;
    flex-shrink: 0;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    font-weight: 700;
    background: var(--el-fill-color);
    border-radius: var(--ys-radius-base);
  }

  &__name {
    font-size: var(--ys-font-size-base);
    font-weight: 500;
    color: var(--el-text-color-primary);
    text-decoration: none;

    &:hover {
      color: var(--el-color-primary);
    }
  }

  &__desc {
    margin: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-secondary);
    white-space: nowrap;
  }
}

.growth-chip {
  display: inline-flex;
  gap: 2px;
  align-items: center;
  padding: 2px 6px;
  font-family: monospace;
  font-size: 11px;
  font-weight: 600;
  border-radius: var(--ys-radius-sm);

  &--up { color: var(--el-color-success); background: var(--el-color-success-light-9); }
  &--down { color: var(--el-color-danger); background: var(--el-color-danger-light-9); }
  &--neutral { color: var(--el-text-color-secondary); background: var(--el-fill-color); }
}
</style>
