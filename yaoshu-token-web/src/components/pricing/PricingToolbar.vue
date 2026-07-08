<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Grid, List } from '@element-plus/icons-vue'
import {
  SORT_OPTIONS,
  VIEW_MODES
} from '@/api/pricing/constants'
import type { PricingVendor, PricingModel, TokenUnit } from '@/api/pricing/types'
import { isFeatureHidden } from '@/plugins/spi/registry'

// 商业版 PD-03 延伸：无倍率/加价概念，隐藏充值价开关
const rechargeHidden = isFeatureHidden('pricing-recharge')

const props = defineProps<{
  filteredCount: number
  totalCount?: number
  sortBy: string
  tokenUnit: TokenUnit
  showRechargePrice: boolean
  viewMode: string
  quotaTypeFilter: string
  endpointTypeFilter: string
  vendorFilter: string
  groupFilter: string
  tagFilter: string
  vendors: PricingVendor[]
  groups: string[]
  groupRatios: Record<string, number>
  tags: string[]
  models: PricingModel[]
  hasActiveFilters: boolean
  activeFilterCount: number
}>()

const emit = defineEmits<{
  (e: 'update:sortBy', v: string): void
  (e: 'update:tokenUnit', v: TokenUnit): void
  (e: 'update:showRechargePrice', v: boolean): void
  (e: 'update:viewMode', v: string): void
  (e: 'update:quotaTypeFilter', v: string): void
  (e: 'update:endpointTypeFilter', v: string): void
  (e: 'update:vendorFilter', v: string): void
  (e: 'update:groupFilter', v: string): void
  (e: 'update:tagFilter', v: string): void
  (e: 'clear-filters'): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="pricing-toolbar">
    <div class="pricing-toolbar__left">
      <span class="pricing-toolbar__count">
        {{ filteredCount }}<span v-if="totalCount"> / {{ totalCount }}</span>
        {{ t('pricing.models') }}
      </span>
      <el-button
        v-if="hasActiveFilters"
        text
        size="small"
        @click="emit('clear-filters')"
      >
        {{ t('pricing.clearFilters') }} ({{ activeFilterCount }})
      </el-button>
    </div>

    <div class="pricing-toolbar__right">
      <el-select
        :model-value="sortBy"
        size="small"
        style="width: 160px"
        @update:model-value="(v: string) => emit('update:sortBy', v)"
      >
        <el-option
          :value="SORT_OPTIONS.NAME"
          :label="t('pricing.sortByName')"
        />
        <el-option
          :value="SORT_OPTIONS.PRICE_LOW"
          :label="t('pricing.sortPriceLow')"
        />
        <el-option
          :value="SORT_OPTIONS.PRICE_HIGH"
          :label="t('pricing.sortPriceHigh')"
        />
      </el-select>

      <!-- S5: 移除 K/M 单位切换，固定为 M（百万 tokens） -->

      <el-switch
        v-if="!rechargeHidden"
        :model-value="showRechargePrice"
        :active-text="t('pricing.rechargePrice')"
        size="small"
        @update:model-value="(v: any) => emit('update:showRechargePrice', !!v)"
      />

      <el-radio-group
        :model-value="viewMode"
        size="small"
        @update:model-value="(v: any) => emit('update:viewMode', v as string)"
      >
        <el-radio-button :value="VIEW_MODES.CARD">
          <el-icon><Grid /></el-icon>
        </el-radio-button>
        <el-radio-button :value="VIEW_MODES.TABLE">
          <el-icon><List /></el-icon>
        </el-radio-button>
      </el-radio-group>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pricing-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ys-spacing-3);
  align-items: center;
  justify-content: space-between;

  &__left {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
  }

  &__count {
    font-size: var(--ys-font-size-base);
    color: var(--el-text-color-secondary);
  }

  &__right {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-2);
    align-items: center;
  }
}
</style>
