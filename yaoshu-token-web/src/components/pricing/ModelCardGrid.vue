<script setup lang="ts">
import type { PricingModel, TokenUnit } from '@/api/pricing/types'
import ModelCard from './ModelCard.vue'

defineProps<{
  models: PricingModel[]
  priceRate: number
  usdExchangeRate: number
  tokenUnit: TokenUnit
  showRechargePrice: boolean
}>()

const emit = defineEmits<{
  (e: 'model-click', modelName: string): void
}>()
</script>

<template>
  <div class="model-card-grid">
    <ModelCard
      v-for="model in models"
      :key="model.modelName"
      :model="model"
      :price-rate="priceRate"
      :usd-exchange-rate="usdExchangeRate"
      :token-unit="tokenUnit"
      :show-recharge-price="showRechargePrice"
      @click="emit('model-click', model.modelName)"
    />
  </div>
</template>

<style scoped lang="scss">
.model-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--ys-spacing-3);
}
</style>
