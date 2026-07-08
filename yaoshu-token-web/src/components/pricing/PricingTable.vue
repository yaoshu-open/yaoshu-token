<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import GroupBadge from '@/components/common/GroupBadge.vue'
import LobeIcon from '@/components/common/VendorIcon.vue'
import ModelPerfBadge from './ModelPerfBadge.vue'
import { formatPrice, formatRequestPrice } from '@/views/pricing/lib/price'
import { parseTags } from '@/views/pricing/lib/filters'
import { isTokenBasedModel } from '@/views/pricing/lib/model-helpers'
import { MAX_TAGS_DISPLAY } from '@/api/pricing/constants'
import type { PricingModel, TokenUnit } from '@/api/pricing/types'
import { isFeatureHidden } from '@/plugins/spi/registry'

const props = defineProps<{
  models: PricingModel[]
  priceRate: number
  usdExchangeRate: number
  tokenUnit: TokenUnit
  showRechargePrice: boolean
}>()

const emit = defineEmits<{
  (e: 'model-click', modelName: string): void
}>()

const { t } = useI18n()

// PD-03：商业版无倍率概念，隐藏 Groups 列
const groupHidden = isFeatureHidden('group-ratio')

function getInputPrice(model: PricingModel) {
  if (isTokenBasedModel(model)) {
    return formatPrice(model, 'input', props.tokenUnit, props.showRechargePrice, props.priceRate, props.usdExchangeRate)
  }
  return formatRequestPrice(model, props.showRechargePrice, props.priceRate, props.usdExchangeRate)
}

function getOutputPrice(model: PricingModel) {
  if (isTokenBasedModel(model)) {
    return formatPrice(model, 'output', props.tokenUnit, props.showRechargePrice, props.priceRate, props.usdExchangeRate)
  }
  return '-'
}

// 工单3：cacheRatio 非空时展示缓存输入价格，对齐卡片与详情页
function getCachePrice(model: PricingModel) {
  if (isTokenBasedModel(model) && model.cacheRatio != null) {
    return formatPrice(model, 'cache', props.tokenUnit, props.showRechargePrice, props.priceRate, props.usdExchangeRate)
  }
  return '-'
}
</script>

<template>
  <el-table
    :data="models"
    size="default"
    class="pricing-table"
    @row-click="(row: PricingModel) => emit('model-click', row.modelName)"
  >
    <el-table-column
      :label="t('pricing.model')"
      min-width="200"
    >
      <template #default="{ row }">
        <div class="pricing-table__model">
          <LobeIcon :vendor="row.vendorName || ''" :vendor-icon="row.vendorIcon" :size="20" />
          <span class="pricing-table__name">{{ row.modelName }}</span>
          <ModelPerfBadge :model-name="row.modelName" />
        </div>
        <div
          v-if="row.vendorName"
          class="pricing-table__vendor"
        >
          {{ row.vendorName }}
        </div>
      </template>
    </el-table-column>
    <el-table-column
      :label="t('pricing.tags')"
      min-width="150"
    >
      <template #default="{ row }">
        <div class="pricing-table__tags">
          <el-tag
            v-for="tag in parseTags(row.tags).slice(0, MAX_TAGS_DISPLAY)"
            :key="tag"
            size="small"
            type="info"
          >
            {{ tag }}
          </el-tag>
        </div>
      </template>
    </el-table-column>
    <el-table-column
      v-if="!groupHidden"
      :label="t('pricing.groups')"
      min-width="120"
    >
      <template #default="{ row }">
        <div class="pricing-table__groups">
          <GroupBadge
            v-for="g in (row.enableGroup || []).slice(0, 3)"
            :key="g"
            :group="g"
            size="sm"
          />
        </div>
      </template>
    </el-table-column>
    <el-table-column
      :label="`${t('pricing.input')} ${t('pricing.perMTokens')}`"
      align="right"
      min-width="100"
    >
      <template #default="{ row }">
        <span class="pricing-table__price">{{ getInputPrice(row as PricingModel) }}</span>
      </template>
    </el-table-column>
    <el-table-column
      :label="`${t('pricing.output')} ${t('pricing.perMTokens')}`"
      align="right"
      min-width="100"
    >
      <template #default="{ row }">
        <span class="pricing-table__price">{{ getOutputPrice(row as PricingModel) }}</span>
      </template>
    </el-table-column>
    <el-table-column
      :label="`${t('pricing.cachedInput')} ${t('pricing.perMTokens')}`"
      align="right"
      min-width="100"
    >
      <template #default="{ row }">
        <span class="pricing-table__price">{{ getCachePrice(row as PricingModel) }}</span>
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped lang="scss">
.pricing-table {
  cursor: pointer;

  &__model {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
  }

  &__name {
    font-family: monospace;
    font-weight: 600;
  }

  &__vendor {
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-1);
  }

  &__groups {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-1);
  }

  &__price {
    font-family: monospace;
    font-weight: 500;
    font-variant-numeric: tabular-nums;
  }
}
</style>
