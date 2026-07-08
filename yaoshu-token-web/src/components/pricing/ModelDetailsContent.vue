<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { InfoFilled, Monitor, Document } from '@element-plus/icons-vue'
import CopyButton from '@/components/common/CopyButton.vue'
import GroupBadge from '@/components/common/GroupBadge.vue'
import LobeIcon from '@/components/common/VendorIcon.vue'
import { usePerfMetrics } from '@/composables/perf-metrics/usePerfMetrics'
import { formatThroughput, formatLatency, formatUptimePct } from '@/utils/perfFormat'
import { QUOTA_TYPE_VALUES } from '@/api/pricing/constants'
import { parseTags } from '@/views/pricing/lib/filters'
import { isTokenBasedModel, getAvailableGroups } from '@/views/pricing/lib/model-helpers'
import { inferModelMetadata, inferApiInfo } from '@/views/pricing/lib/model-metadata'
import { formatPrice, formatGroupPrice, formatRequestPrice, formatFixedPrice } from '@/views/pricing/lib/price'
import { isDynamicPricingModel } from '@/views/pricing/lib/dynamic-price'
import type { PricingModel, TokenUnit, EndpointInfo, PriceType } from '@/api/pricing/types'
import { isFeatureHidden } from '@/plugins/spi/registry'
import DynamicPricingBreakdown from './DynamicPricingBreakdown.vue'
import ModelDetailsQuickStats from './ModelDetailsQuickStats.vue'
import ModelDetailsCapabilities from './ModelDetailsCapabilities.vue'
import ModelDetailsModalities from './ModelDetailsModalities.vue'
import ModelDetailsApi from './ModelDetailsApi.vue'
import ModelDetailsPerformance from '@/components/perf-metrics/ModelDetailsPerformance.vue'

const props = defineProps<{
  model: PricingModel
  groupRatio: Record<string, number>
  usableGroup: Record<string, { desc: string; ratio: number }>
  endpointMap: Record<string, EndpointInfo>
  autoGroups: string[]
  priceRate: number
  usdExchangeRate: number
  tokenUnit: TokenUnit
  showRechargePrice?: boolean
}>()

const { t } = useI18n()

// PD-03：商业版无倍率概念，隐藏分组价格表与自动分组链
const groupHidden = isFeatureHidden('group-ratio')
const showRechargePrice = computed(() => props.showRechargePrice ?? false)
const metadata = computed(() => inferModelMetadata(props.model))
const apiInfo = computed(() => inferApiInfo(props.model))
const isDynamic = computed(() => isDynamicPricingModel(props.model))
// S5: 移除 K 单位，固定为 1M
const tokenUnitLabel = '1M'
const isTokenBased = computed(() => isTokenBasedModel(props.model))
const tags = computed(() => parseTags(props.model.tags))

// 性能数据（Overview 摘要）
const modelNameRef = computed(() => props.model.modelName)
const { data: perfData } = usePerfMetrics(modelNameRef, 24)

const avgTps = computed(() => {
  const groups = perfData.value?.groups ?? []
  const values = groups.map((g) => g.avgTps).filter((v) => v > 0)
  return values.length > 0 ? values.reduce((s, v) => s + v, 0) / values.length : 0
})
const avgLatency = computed(() => {
  const groups = perfData.value?.groups ?? []
  const values = groups.map((g) => g.avgLatencyMs).filter((v) => v > 0)
  return values.length > 0 ? Math.round(values.reduce((s, v) => s + v, 0) / values.length) : 0
})
const successRate = computed(() => {
  const groups = perfData.value?.groups ?? []
  const rates = groups.map((g) => g.successRate).filter((r) => Number.isFinite(r))
  return rates.length > 0 ? rates.reduce((s, r) => s + r, 0) / rates.length : NaN
})

// 分组价格
const availableGroups = computed(() => getAvailableGroups(props.model, props.usableGroup || {}))

// 次要价格类型（cache/create_cache/image/audio_input/audio_output）
const secondaryPriceTypes = computed<{ label: string; type: PriceType }[]>(() => {
  if (!isTokenBased.value) return []
  const items: { label: string; type: PriceType }[] = []
  if (props.model.cacheRatio != null) items.push({ label: t('pricing.cachedInput'), type: 'cache' })
  if (props.model.createCacheRatio != null) items.push({ label: t('pricing.cacheWrite'), type: 'create_cache' })
  if (props.model.imageRatio != null) items.push({ label: t('pricing.imageInput'), type: 'image' })
  if (props.model.audioRatio != null) items.push({ label: t('pricing.audioInput'), type: 'audio_input' })
  if (props.model.audioRatio != null && props.model.audioCompletionRatio != null) {
    items.push({ label: t('pricing.audioOutput'), type: 'audio_output' })
  }
  return items
})

// 自动分组链（autoGroups 与 model.enableGroup 的交集）
const autoGroupChain = computed(() => {
  const enableGroups = Array.isArray(props.model.enableGroup) ? props.model.enableGroup : []
  return (props.autoGroups || []).filter((g) => enableGroups.includes(g))
})

const activeTab = ref('overview')
</script>

<template>
  <div class="model-details">
    <!-- 模型头部 -->
    <header class="model-details__header">
      <div class="model-details__title-row">
        <LobeIcon
          :vendor="model.vendorName || ''"
          :vendor-icon="model.vendorIcon"
          :size="24"
        />
        <h1 class="model-details__name">
          {{ model.modelName }}
        </h1>
        <CopyButton
          :value="model.modelName"
          :tooltip="t('pricing.copyModelName')"
        />
      </div>
      <div class="model-details__meta">
        <span v-if="model.vendorName">{{ model.vendorName }}</span>
        <span>·</span>
        <span>{{ model.quotaType === QUOTA_TYPE_VALUES.TOKEN ? t('pricing.tokenBased') : t('pricing.perRequest') }}</span>
        <template v-if="isDynamic">
          <span>·</span>
          <el-tag
            size="small"
            type="warning"
          >
            {{ t('pricing.dynamicPricing') }}
          </el-tag>
        </template>
      </div>
      <p
        v-if="model.description"
        class="model-details__desc"
      >
        {{ model.description }}
      </p>
      <div
        v-if="tags.length > 0"
        class="model-details__tags"
      >
        <el-tag
          v-for="tag in tags"
          :key="tag"
          size="small"
          type="info"
        >
          {{ tag }}
        </el-tag>
      </div>
    </header>

    <el-tabs
      v-model="activeTab"
      class="model-details__tabs"
    >
      <!-- Overview 标签页 -->
      <el-tab-pane name="overview">
        <template #label>
          <el-icon><InfoFilled /></el-icon>
          {{ t('pricing.tabs.overview') }}
        </template>

        <div class="model-details__overview">
          <!-- 性能摘要 -->
          <div class="overview-summary">
            <div class="overview-summary__item">
              <span class="overview-summary__label">TPS</span>
              <span class="overview-summary__value">{{ formatThroughput(avgTps) }}</span>
            </div>
            <div class="overview-summary__item">
              <span class="overview-summary__label">{{ t('pricing.avgLatency') }}</span>
              <span class="overview-summary__value">{{ formatLatency(avgLatency) }}</span>
            </div>
            <div class="overview-summary__item">
              <span class="overview-summary__label">{{ t('pricing.successRate') }}</span>
              <span class="overview-summary__value">{{ formatUptimePct(successRate) }}</span>
            </div>
          </div>

          <!-- 定价 -->
          <section class="overview-section">
            <h3 class="overview-section__title">
              {{ t('pricing.pricing') }}
            </h3>
            <!-- 基础价格 -->
            <div
              v-if="isTokenBased"
              class="base-price"
            >
              <div class="base-price__item">
                <span class="base-price__label">{{ t('pricing.input') }}</span>
                <span class="base-price__value">
                  {{ formatPrice(model, 'input', tokenUnit, showRechargePrice, priceRate, usdExchangeRate) }}
                  <span class="base-price__unit">/ {{ tokenUnitLabel }}</span>
                </span>
              </div>
              <div class="base-price__item">
                <span class="base-price__label">{{ t('pricing.output') }}</span>
                <span class="base-price__value">
                  {{ formatPrice(model, 'output', tokenUnit, showRechargePrice, priceRate, usdExchangeRate) }}
                  <span class="base-price__unit">/ {{ tokenUnitLabel }}</span>
                </span>
              </div>
            </div>
            <div
              v-else
              class="base-price"
            >
              <div class="base-price__item">
                <span class="base-price__label">{{ t('pricing.perRequest') }}</span>
                <span class="base-price__value">{{ formatRequestPrice(model, showRechargePrice, priceRate, usdExchangeRate) }}</span>
              </div>
            </div>

            <!-- 次要价格类型（cache/create_cache/image/audio_input/audio_output） -->
            <div
              v-if="secondaryPriceTypes.length > 0"
              class="secondary-prices"
            >
              <div
                v-for="pt in secondaryPriceTypes"
                :key="pt.type"
                class="secondary-prices__item"
              >
                <span class="secondary-prices__label">{{ pt.label }}</span>
                <span class="secondary-prices__value">
                  {{ formatPrice(model, pt.type, tokenUnit, showRechargePrice, priceRate, usdExchangeRate) }}
                  <span class="secondary-prices__unit">/ {{ tokenUnitLabel }}</span>
                </span>
              </div>
            </div>

            <!-- 动态定价分解 -->
            <DynamicPricingBreakdown
              v-if="isDynamic"
              :billing-expr="model.billingExpr"
            />

            <!-- 分组价格表 -->
            <div v-if="!groupHidden && availableGroups.length > 0">
              <el-table
                :data="availableGroups.map((g) => ({ group: g }))"
                size="small"
                class="group-pricing"
              >
                <el-table-column :label="t('pricing.group')">
                  <template #default="{ row }">
                    <GroupBadge
                      :group="row.group"
                      size="sm"
                    />
                  </template>
                </el-table-column>
                <el-table-column
                  :label="t('pricing.ratio')"
                  align="right"
                >
                  <template #default="{ row }">
                    {{ groupRatio[row.group as string] || 1 }}x
                  </template>
                </el-table-column>
                <template v-if="isTokenBased">
                  <el-table-column
                    :label="t('pricing.input')"
                    align="right"
                  >
                    <template #default="{ row }">
                      {{ formatGroupPrice(model, row.group as string, 'input', tokenUnit, showRechargePrice, priceRate, usdExchangeRate, groupRatio) }}
                    </template>
                  </el-table-column>
                  <el-table-column
                    :label="t('pricing.output') + ' / 1M tokens'"
                    align="right"
                  >
                    <template #default="{ row }">
                      {{ formatGroupPrice(model, row.group as string, 'output', tokenUnit, showRechargePrice, priceRate, usdExchangeRate, groupRatio) }}
                    </template>
                  </el-table-column>
                </template>
                <template v-else>
                  <el-table-column
                    :label="t('pricing.price')"
                    align="right"
                  >
                    <template #default="{ row }">
                      {{ formatFixedPrice(model, row.group as string, showRechargePrice, priceRate, usdExchangeRate, groupRatio) }}
                    </template>
                  </el-table-column>
                </template>
              </el-table>
              <p
                v-if="isTokenBased"
                class="group-pricing__hint"
              >
                {{ t('pricing.pricesShownPer') }} {{ tokenUnitLabel }} {{ t('pricing.tokens') }}
              </p>
            </div>

            <!-- 自动分组链 -->
            <div
              v-if="!groupHidden && autoGroupChain.length > 0"
              class="auto-group-chain"
            >
              <span class="auto-group-chain__label">{{ t('pricing.autoGroupChain') }}</span>
              <span class="auto-group-chain__items">
                <template
                  v-for="(g, i) in autoGroupChain"
                  :key="g"
                >
                  <GroupBadge
                    :group="g"
                    size="sm"
                  />
                  <span
                    v-if="i < autoGroupChain.length - 1"
                    class="auto-group-chain__arrow"
                  >→</span>
                </template>
              </span>
            </div>
          </section>

          <!-- 快速统计 -->
          <ModelDetailsQuickStats :metadata="metadata" />

          <!-- 能力 + 模态 -->
          <section class="overview-section">
            <h3 class="overview-section__title">
              {{ t('pricing.capabilities') }} / {{ t('pricing.supportedModalities') }}
            </h3>
            <ModelDetailsCapabilities :capabilities="metadata.capabilities" />
            <div class="modality-flow">
              <div>
                <span class="modality-flow__label">{{ t('pricing.input') }}</span>
                <ModelDetailsModalities :modalities="metadata.inputModalities" />
              </div>
              <span class="modality-flow__arrow">→</span>
              <div>
                <span class="modality-flow__label">{{ t('pricing.output') }}</span>
                <ModelDetailsModalities :modalities="metadata.outputModalities" />
              </div>
            </div>
          </section>

          <!-- Provider 信息 -->
          <section class="overview-section">
            <h3 class="overview-section__title">
              {{ t('pricing.providerInfo') }}
            </h3>
            <dl class="provider-info">
              <div><dt>{{ t('pricing.vendor') }}</dt><dd>{{ apiInfo.vendor_label }}</dd></div>
              <div><dt>{{ t('pricing.tokenizer') }}</dt><dd>{{ apiInfo.tokenizer }}<span v-if="apiInfo.tokenizer_note"> ({{ apiInfo.tokenizer_note }})</span></dd></div>
              <div><dt>{{ t('pricing.license') }}</dt><dd>{{ apiInfo.license }}</dd></div>
              <div><dt>{{ t('pricing.dataRetention') }}</dt><dd>{{ apiInfo.data_retention_days }} {{ t('pricing.days') }}</dd></div>
              <div v-if="apiInfo.homepage">
                <dt>{{ t('pricing.homepage') }}</dt><dd>
                  <a
                    :href="apiInfo.homepage"
                    target="_blank"
                    rel="noreferrer"
                  >{{ apiInfo.homepage }}</a>
                </dd>
              </div>
            </dl>
          </section>
        </div>
      </el-tab-pane>

      <!-- Performance 标签页（复用 M3-2 组件） -->
      <el-tab-pane name="performance">
        <template #label>
          <el-icon><Monitor /></el-icon>
          {{ t('pricing.tabs.performance') }}
        </template>
        <ModelDetailsPerformance :model="model.modelName" />
      </el-tab-pane>

      <!-- API 标签页 -->
      <el-tab-pane name="api">
        <template #label>
          <el-icon><Document /></el-icon>
          {{ t('pricing.tabs.api') }}
        </template>
        <ModelDetailsApi
          :model="model"
          :endpoint-map="endpointMap"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped lang="scss">
.model-details {
  &__header {
    padding-bottom: 16px;
  }

  &__title-row {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
  }

  &__name {
    margin: 0;
    font-family: monospace;
    font-size: var(--ys-font-size-xl);
    font-weight: 700;
  }

  &__meta {
    display: flex;
    gap: 6px;
    align-items: center;
    margin-top: 4px;
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
  }

  &__desc {
    margin: var(--ys-spacing-2) 0 0;
    font-size: var(--ys-font-size-base);
    line-height: 1.5;
    color: var(--el-text-color-secondary);
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-1);
    margin-top: 8px;
  }

  &__tabs {
    margin-top: 16px;
  }

  &__overview {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-6);
  }
}

.overview-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: var(--ys-radius-md);

  &__item {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: var(--ys-spacing-2) var(--ys-spacing-3);
    background: var(--el-bg-color);
  }

  &__label {
    font-size: 10px;
    font-weight: 500;
    color: var(--el-text-color-secondary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  &__value {
    font-family: monospace;
    font-size: var(--ys-font-size-base);
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }
}

.overview-section {
  &__title {
    margin: 0 0 var(--ys-spacing-3);
    font-size: var(--ys-font-size-xs);
    font-weight: 600;
    color: var(--el-text-color-secondary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }
}

.base-price {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--ys-spacing-2);
  margin-bottom: 12px;

  &__item {
    padding: var(--ys-spacing-3);
    background: var(--el-fill-color-lighter);
    border: 1px solid var(--el-border-color);
    border-radius: var(--ys-radius-md);
  }

  &__label {
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
  }

  &__value {
    display: block;
    margin-top: 4px;
    font-family: monospace;
    font-size: var(--ys-font-size-lg);
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }

  &__unit {
    font-size: var(--ys-font-size-xs);
    font-weight: 400;
    color: var(--el-text-color-placeholder);
  }
}

.group-pricing {
  &__hint {
    margin: 6px 0 0;
    font-size: 10px;
    color: var(--el-text-color-placeholder);
  }
}

.secondary-prices {
  padding: var(--ys-spacing-2) var(--ys-spacing-3);
  margin-top: 8px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--ys-radius-md);

  &__item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--ys-spacing-1) 0;

    & + & {
      border-top: 1px solid var(--el-border-color-lighter);
    }
  }

  &__label {
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
  }

  &__value {
    font-family: monospace;
    font-size: var(--ys-font-size-sm);
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }

  &__unit {
    font-size: 11px;
    font-weight: 400;
    color: var(--el-text-color-placeholder);
  }
}

.auto-group-chain {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ys-spacing-2);
  align-items: center;
  margin-top: 12px;

  &__label {
    font-size: 11px;
    font-weight: 500;
    color: var(--el-text-color-secondary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  &__items {
    display: inline-flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-1);
    align-items: center;
  }

  &__arrow {
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-placeholder);
  }
}

.modality-flow {
  display: flex;
  gap: var(--ys-spacing-3);
  align-items: center;
  padding: var(--ys-spacing-2) var(--ys-spacing-3);
  margin-top: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--ys-radius-md);

  &__label {
    margin-right: 6px;
    font-size: 11px;
    font-weight: 500;
    color: var(--el-text-color-secondary);
  }

  &__arrow {
    color: var(--el-text-color-placeholder);
  }
}

.provider-info {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--ys-spacing-2);
  margin: 0;

  div {
    display: flex;
    gap: var(--ys-spacing-2);
    padding: var(--ys-spacing-2) var(--ys-spacing-3);
    border: 1px solid var(--el-border-color);
    border-radius: var(--ys-radius-base);
  }

  dt {
    min-width: 80px;
    font-size: 11px;
    font-weight: 500;
    color: var(--el-text-color-secondary);
  }

  dd {
    margin: 0;
    font-size: var(--ys-font-size-sm);
  }

  a {
    color: var(--el-color-primary);
    text-decoration: none;
  }
}
</style>
