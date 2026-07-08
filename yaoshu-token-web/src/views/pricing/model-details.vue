<script setup lang="ts">
// 模型详情独立页（T-PRICING-02）。
// 从 URL :modelId 获取模型名，复用 ModelDetailsContent 展示完整详情。
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft } from '@element-plus/icons-vue'
import { usePricingData } from '@/composables/pricing/usePricingData'
import { DEFAULT_TOKEN_UNIT } from '@/api/pricing/constants'
import type { TokenUnit } from '@/api/pricing/types'
import ModelDetailsContent from '@/components/pricing/ModelDetailsContent.vue'
import LoadingSkeleton from '@/components/pricing/LoadingSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const modelId = computed(() => decodeURIComponent(String(route.params.modelId || '')))

const {
  models,
  groupRatio,
  usableGroup,
  endpointMap,
  autoGroups,
  isLoading,
  priceRate,
  usdExchangeRate
} = usePricingData()

// S5: 移除 K 单位，固定为 M
const tokenUnit = computed<TokenUnit>(() => DEFAULT_TOKEN_UNIT)

const model = computed(() => {
  if (!models.value || !modelId.value) return null
  return models.value.find((m) => m.modelName === modelId.value) ?? null
})

function handleBack(): void {
  router.push({ path: '/pricing', query: { ...route.query, modelId: undefined } })
}
</script>

<template>
  <div class="model-details-page">
    <div class="model-details-page__container">
      <el-button
        link
        type="primary"
        :icon="ArrowLeft"
        class="model-details-page__back"
        @click="handleBack"
      >
        {{ t('pricing.backToList') }}
      </el-button>

      <LoadingSkeleton
        v-if="isLoading"
        view-mode="card"
      />

      <EmptyState
        v-else-if="!model"
        :title="t('pricing.modelNotFound')"
        :description="t('pricing.modelNotFoundDesc', { name: modelId })"
      >
        <el-button
          type="primary"
          @click="handleBack"
        >
          {{ t('pricing.backToList') }}
        </el-button>
      </EmptyState>

      <ModelDetailsContent
        v-else
        :model="model"
        :group-ratio="groupRatio"
        :usable-group="usableGroup"
        :endpoint-map="endpointMap"
        :auto-groups="autoGroups"
        :price-rate="priceRate"
        :usd-exchange-rate="usdExchangeRate"
        :token-unit="tokenUnit"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.model-details-page {
  width: 100%;
  min-height: 100vh;

  &__container {
    max-width: 1100px;
    padding: var(--ys-spacing-8) var(--ys-spacing-4);
    margin: 0 auto;
  }

  &__back {
    margin-bottom: var(--ys-spacing-4);
  }
}
</style>
