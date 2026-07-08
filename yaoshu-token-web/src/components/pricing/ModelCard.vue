<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import GroupBadge from '@/components/common/GroupBadge.vue'
import LobeIcon from '@/components/common/VendorIcon.vue'
import ModelPerfBadge from './ModelPerfBadge.vue'
import CopyButton from '@/components/common/CopyButton.vue'
import { formatPrice, formatRequestPrice } from '@/views/pricing/lib/price'
import { parseTags } from '@/views/pricing/lib/filters'
import { isTokenBasedModel } from '@/views/pricing/lib/model-helpers'
import { MAX_TAGS_DISPLAY } from '@/api/pricing/constants'
import type { PricingModel, TokenUnit } from '@/api/pricing/types'
import { isFeatureHidden } from '@/plugins/spi/registry'

const props = defineProps<{
  model: PricingModel
  priceRate: number
  usdExchangeRate: number
  tokenUnit: TokenUnit
  showRechargePrice: boolean
}>()

const emit = defineEmits<{
  (e: 'click', modelName: string): void
}>()

const { t } = useI18n()

// PD-03：商业版无倍率概念，隐藏 Groups 徽章
const groupHidden = isFeatureHidden('group-ratio')

const tags = parseTags(props.model.tags).slice(0, MAX_TAGS_DISPLAY)
const isToken = isTokenBasedModel(props.model)
// 工单3：cacheRatio 非空时展示缓存输入价格（对齐详情页 secondaryPriceTypes 逻辑）
const hasCache = props.model.cacheRatio != null
</script>

<template>
  <div
    class="model-card"
    @click="emit('click', model.modelName)"
  >
    <div class="model-card__header">
      <LobeIcon
        :vendor="model.vendorName || ''"
        :vendor-icon="model.vendorIcon"
        :size="24"
      />
      <span class="model-card__name">{{ model.modelName }}</span>
      <CopyButton
        :value="model.modelName"
        :tooltip="t('pricing.copyModelName')"
      />
    </div>
    <div class="model-card__meta">
      <span
        v-if="model.vendorName"
        class="model-card__vendor"
      >{{ model.vendorName }}</span>
      <ModelPerfBadge :model-name="model.modelName" />
    </div>
    <p
      v-if="model.description"
      class="model-card__desc"
    >
      {{ model.description }}
    </p>
    <div class="model-card__price">
      <template v-if="isToken">
        <div class="model-card__price-item">
          <span class="model-card__price-label">{{ t('pricing.input') }} {{ t('pricing.perMTokens') }}</span>
          <span class="model-card__price-value">
            {{ formatPrice(model, 'input', tokenUnit, showRechargePrice, priceRate, usdExchangeRate) }}
          </span>
        </div>
        <div class="model-card__price-item">
          <span class="model-card__price-label">{{ t('pricing.output') }} {{ t('pricing.perMTokens') }}</span>
          <span class="model-card__price-value">
            {{ formatPrice(model, 'output', tokenUnit, showRechargePrice, priceRate, usdExchangeRate) }}
          </span>
        </div>
        <div
          v-if="hasCache"
          class="model-card__price-item"
        >
          <span class="model-card__price-label">{{ t('pricing.cachedInput') }} {{ t('pricing.perMTokens') }}</span>
          <span class="model-card__price-value">
            {{ formatPrice(model, 'cache', tokenUnit, showRechargePrice, priceRate, usdExchangeRate) }}
          </span>
        </div>
      </template>
      <template v-else>
        <div class="model-card__price-item">
          <span class="model-card__price-label">{{ t('pricing.perRequest') }}</span>
          <span class="model-card__price-value">
            {{ formatRequestPrice(model, showRechargePrice, priceRate, usdExchangeRate) }}
          </span>
        </div>
      </template>
    </div>
    <div
      v-if="tags.length > 0"
      class="model-card__tags"
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
    <div
      v-if="!groupHidden && model.enableGroup?.length"
      class="model-card__groups"
    >
      <GroupBadge
        v-for="g in (model.enableGroup || []).slice(0, 3)"
        :key="g"
        :group="g"
        size="sm"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.model-card {
  padding: var(--ys-spacing-4);
  cursor: pointer;
  border: 1px solid var(--el-border-color);
  border-radius: var(--ys-radius-lg);
  transition: all 0.15s;

  &:hover {
    border-color: var(--el-color-primary-light-5);
    box-shadow: var(--ys-shadow-sm);
  }

  &__header {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
  }

  &__name {
    overflow: hidden;
    text-overflow: ellipsis;
    font-family: monospace;
    font-size: 15px;
    font-weight: 700;
    white-space: nowrap;
  }

  &__meta {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
    margin-top: 4px;
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
  }

  &__desc {
    display: -webkit-box;
    margin: var(--ys-spacing-2) 0 0;
    overflow: hidden;
    -webkit-line-clamp: 2;
    font-size: var(--ys-font-size-sm);
    line-height: 1.4;
    color: var(--el-text-color-secondary);
    -webkit-box-orient: vertical;
  }

  &__price {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-2) var(--ys-spacing-4);
    align-items: baseline;
    padding: var(--ys-spacing-2) var(--ys-spacing-3);
    margin-top: 12px;
    background: var(--el-fill-color-lighter);
    border-radius: var(--ys-radius-md);
  }

  &__price-item {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  &__price-label {
    font-size: 11px;
    color: var(--el-text-color-secondary);
  }

  &__price-value {
    font-family: monospace;
    font-size: var(--ys-font-size-base);
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }

  &__price-unit {
    font-size: 11px;
    font-weight: 400;
    color: var(--el-text-color-secondary);
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-1);
    margin-top: 8px;
  }

  &__groups {
    display: flex;
    gap: var(--ys-spacing-1);
    margin-top: 8px;
  }
}
</style>
