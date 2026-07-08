<script setup lang="ts">
import ModelDetailsContent from './ModelDetailsContent.vue'
import type { PricingModel, TokenUnit, EndpointInfo } from '@/api/pricing/types'

const props = defineProps<{
  open: boolean
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

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
}>()
</script>

<template>
  <el-drawer
    :model-value="open"
    direction="rtl"
    size="50%"
    :show-close="true"
    @update:model-value="(v: boolean) => emit('update:open', v)"
  >
    <template #header>
      <span style="position: absolute; left: -9999px;">{{ model.modelName }}</span>
    </template>
    <ModelDetailsContent
      :model="model"
      :group-ratio="groupRatio"
      :usable-group="usableGroup"
      :endpoint-map="endpointMap"
      :auto-groups="autoGroups"
      :price-rate="priceRate"
      :usd-exchange-rate="usdExchangeRate"
      :token-unit="tokenUnit"
      :show-recharge-price="showRechargePrice"
    />
  </el-drawer>
</template>
