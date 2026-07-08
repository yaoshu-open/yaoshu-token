<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSystemConfigStore } from '@/store/modules/system-config'
import { PriceTag } from '@element-plus/icons-vue'
import {
  BILLING_PRICING_VARS,
  parseTiersFromExpr,
  splitBillingExprAndRequestRules,
  tryParseRequestRuleExpr,
  normalizeTierLabel,
  type ParsedTier,
  type RequestRuleGroup,
  type TierCondition
} from '@/views/pricing/lib/billing-expr'

const props = withDefaults(defineProps<{
  billingExpr?: string | null
  matchedTierLabel?: string | null
  hideCacheColumns?: boolean
}>(), {
  billingExpr: '',
  matchedTierLabel: null,
  hideCacheColumns: false
})

const { t } = useI18n()
const store = useSystemConfigStore()

const currencyMeta = computed(() => {
  const c = store.currency
  const dt = c.quotaDisplayType as string
  if (dt === 'CNY') return { symbol: '¥', rate: c.usdExchangeRate || 7 }
  if (dt === 'CUSTOM') return { symbol: c.customCurrencySymbol || '¤', rate: c.customCurrencyExchangeRate || 1 }
  return { symbol: '$', rate: 1 }
})
const symbol = computed(() => currencyMeta.value.symbol)
const rate = computed(() => currencyMeta.value.rate)

const parsedExpr = computed(() => {
  const split = splitBillingExprAndRequestRules(props.billingExpr || '')
  return {
    tiers: parseTiersFromExpr(split.billingExpr),
    ruleGroups: tryParseRequestRuleExpr(split.requestRuleExpr || '') || [] as RequestRuleGroup[]
  }
})
const tiers = computed(() => parsedExpr.value.tiers)
const ruleGroups = computed(() => parsedExpr.value.ruleGroups)

const hasTiers = computed(() => tiers.value.length > 0)
const hasRules = computed(() => ruleGroups.value.length > 0)

const visiblePriceFields = computed(() =>
  BILLING_PRICING_VARS.filter((v) => {
    if (!v.field) return false
    if (props.hideCacheColumns && v.group === 'cache') return false
    return tiers.value.some((tier) => Number(tier[v.field!] || 0) > 0)
  })
)

const VAR_LABELS: Record<string, string> = { p: 'Input', c: 'Output', len: 'Length' }
const OP_LABELS: Record<string, string> = { '<': '<', '<=': '≤', '>': '>', '>=': '≥' }

function formatTokenHint(value: string | number): string {
  const n = Number(value)
  if (!Number.isFinite(n) || n === 0) return ''
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(n % 1_000_000 === 0 ? 0 : 1)}M`
  if (n >= 1000) return `${(n / 1000).toFixed(n % 1000 === 0 ? 0 : 1)}K`
  return String(n)
}

function formatConditionSummary(conditions: TierCondition[]): string {
  return conditions
    .map((c) => `${t(VAR_LABELS[c.var] || c.var)} ${OP_LABELS[c.op] || c.op} ${formatTokenHint(c.value) || c.value}`)
    .join(' && ')
}

function tierPrice(tier: ParsedTier, field: string): string {
  const value = Number(tier[field] || 0)
  return value > 0 ? `${symbol.value}${(value * rate.value).toFixed(4)}` : '-'
}

const normalizedMatchedLabel = computed(() => normalizeTierLabel(props.matchedTierLabel ?? undefined))
</script>

<template>
  <section
    v-if="billingExpr"
    class="dyn-pricing"
  >
    <template v-if="!hasTiers">
      <div class="dyn-pricing__header">
        <el-icon class="dyn-pricing__icon">
          <PriceTag />
        </el-icon>
        <div>
          <div class="dyn-pricing__title">
            {{ t('pricing.specialBillingExpression') }}
          </div>
          <div class="dyn-pricing__subtitle">
            {{ t('pricing.unableToParse') }}
          </div>
        </div>
      </div>
      <code class="dyn-pricing__expr">{{ billingExpr }}</code>
    </template>

    <template v-else>
      <div class="dyn-pricing__header">
        <el-icon class="dyn-pricing__icon">
          <PriceTag />
        </el-icon>
        <div>
          <div class="dyn-pricing__title">
            {{ t('pricing.dynamicPricing') }}
          </div>
          <div class="dyn-pricing__subtitle">
            {{ t('pricing.dynamicPricingDesc') }}
          </div>
        </div>
      </div>

      <div
        v-if="hasTiers"
        class="dyn-pricing__tiers"
      >
        <div class="dyn-pricing__tier-label">
          {{ t('pricing.tieredPriceTable') }}
        </div>
        <el-table
          :data="tiers"
          size="small"
          class="dyn-pricing__table"
        >
          <el-table-column :label="t('pricing.tier')">
            <template #default="{ row }">
              <div class="tier-cell">
                <el-tag
                  size="small"
                  type="primary"
                >
                  {{ row.label || t('pricing.default') }}
                </el-tag>
                <el-tag
                  v-if="normalizedMatchedLabel && normalizeTierLabel(row.label) === normalizedMatchedLabel"
                  size="small"
                  type="success"
                >
                  {{ t('pricing.matched') }}
                </el-tag>
                <div
                  v-if="row.conditions?.length"
                  class="tier-cell__cond"
                >
                  {{ formatConditionSummary(row.conditions) }}
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            v-for="v in visiblePriceFields"
            :key="v.field ?? v.key"
            :label="t(v.shortLabel)"
            align="right"
          >
            <template #default="{ row }">
              {{ tierPrice(row as ParsedTier, v.field!) }}
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div
        v-if="hasRules"
        class="dyn-pricing__rules"
      >
        <div class="dyn-pricing__tier-label">
          {{ t('pricing.conditionalMultipliers') }}
        </div>
        <ul class="dyn-pricing__rule-list">
          <li
            v-for="(group, gi) in ruleGroups"
            :key="`group-${gi}`"
            class="dyn-pricing__rule-item"
          >
            <span class="dyn-pricing__rule-cond">{{ group.conditions.map((c) => c.source === 'time' ? `${c.timeFunc}(${c.timezone})` : `${c.source}(${c.path})`).join(' && ') }}</span>
            <el-tag
              size="small"
              type="warning"
            >
              {{ group.multiplier }}x
            </el-tag>
          </li>
        </ul>
      </div>
    </template>
  </section>
</template>

<style scoped lang="scss">
.dyn-pricing {
  padding: var(--ys-spacing-3) 0;

  &__header {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: flex-start;
    margin-bottom: 12px;
  }

  &__icon {
    display: inline-flex;
    flex-shrink: 0;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    color: var(--el-color-warning);
    background: var(--el-color-warning-light-9);
    border-radius: var(--ys-radius-base);
  }

  &__title { font-size: 15px; font-weight: 500; }
  &__subtitle { font-size: var(--ys-font-size-xs); color: var(--el-text-color-secondary); }

  &__expr {
    display: block;
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
    word-break: break-all;
  }

  &__tiers, &__rules { margin-bottom: 12px; }
  &__tier-label { margin-bottom: 8px; font-size: var(--ys-font-size-base); font-weight: 600; }

  &__rule-list { padding: 0; margin: 0; list-style: none; }

  &__rule-item {
    display: flex;
    gap: var(--ys-spacing-3);
    align-items: center;
    justify-content: space-between;
    padding: var(--ys-spacing-2) var(--ys-spacing-3);
    margin-bottom: 6px;
    background: var(--el-fill-color-light);
    border-radius: var(--ys-radius-base);
  }
  &__rule-cond { font-size: var(--ys-font-size-base); word-break: break-all; }
}

.tier-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;

  &__cond {
    width: 100%;
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
  }
}
</style>
