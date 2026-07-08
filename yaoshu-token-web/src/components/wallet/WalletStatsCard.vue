<script setup lang="ts">
/**
 * 展示：当前余额 / 总用量 / API 请求数。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatQuotaWithCurrency } from '@/utils/currency'
import { formatNumber } from '@/utils/wallet/format'
import type { UserWalletData } from '@/api/wallet/types'

interface Props {
  user: UserWalletData | null
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
})

const { t } = useI18n()

const stats = computed(() => [
  {
    label: t('wallet.stats.currentBalance'),
    value: formatQuotaWithCurrency(props.user?.quota ?? 0),
    description: t('wallet.stats.remainingQuota'),
    icon: 'i-ep-wallet',
  },
  {
    label: t('wallet.stats.totalUsage'),
    value: formatQuotaWithCurrency(props.user?.usedQuota ?? 0),
    description: t('wallet.stats.totalConsumed'),
    icon: 'i-ep-data-line',
  },
  {
    label: t('wallet.stats.apiRequests'),
    value: formatNumber((props.user?.requestCount ?? 0).toLocaleString()),
    description: t('wallet.stats.totalRequests'),
    icon: 'i-ep-connection',
  },
])
</script>

<template>
  <div class="wallet-stats">
    <div
      v-if="loading"
      class="wallet-stats__grid"
    >
      <div
        v-for="i in 3"
        :key="i"
        class="wallet-stats__item"
      >
        <ElSkeleton
          :rows="3"
          animated
        />
      </div>
    </div>
    <div
      v-else
      class="wallet-stats__grid"
    >
      <div
        v-for="item in stats"
        :key="item.label"
        class="wallet-stats__item"
      >
        <div class="wallet-stats__header">
          <i :class="[item.icon, 'wallet-stats__icon']" />
          <span class="wallet-stats__label">{{ item.label }}</span>
        </div>
        <div class="wallet-stats__value">
          {{ item.value }}
        </div>
        <div class="wallet-stats__desc">
          {{ item.description }}
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.wallet-stats {
  overflow: hidden;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--ys-radius-md);

  &__grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 0;

    @media (width <= 640px) {
      grid-template-columns: 1fr;
    }
  }

  &__item {
    padding: var(--ys-spacing-4) var(--ys-spacing-5);
    border-right: 1px solid var(--el-border-color-lighter);

    &:last-child {
      border-right: none;
    }

    @media (width <= 640px) {
      border-right: none;
      border-bottom: 1px solid var(--el-border-color-lighter);

      &:last-child {
        border-bottom: none;
      }
    }
  }

  &__header {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
  }

  &__icon {
    font-size: var(--ys-font-size-base);
    color: var(--el-text-color-secondary);
  }

  &__label {
    font-size: var(--ys-font-size-xs);
    font-weight: 500;
    color: var(--el-text-color-secondary);
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }

  &__value {
    margin-top: 6px;
    font-family: 'JetBrains Mono', monospace;
    font-size: var(--ys-font-size-xl);
    font-weight: 700;
    font-variant-numeric: tabular-nums;
    color: var(--el-text-color-primary);
    word-break: break-all;

    @media (width >= 640px) {
      font-size: var(--ys-font-size-2xl);
    }
  }

  &__desc {
    margin-top: 4px;
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);

    @media (width <= 768px) {
      display: none;
    }
  }
}
</style>
