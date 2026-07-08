<script setup lang="ts">
/**
 * 展示：待入账 / 累计收益 / 邀请人数 + 邀请链接复制 + 转账按钮。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatQuotaWithCurrency } from '@/utils/currency'
import CopyButton from '@/components/common/CopyButton.vue'
import type { UserWalletData } from '@/api/wallet/types'

interface Props {
  user: UserWalletData | null
  affiliateLink: string
  loading?: boolean
  complianceConfirmed?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  complianceConfirmed: true,
})

defineEmits<{
  (e: 'transfer'): void
}>()

const { t } = useI18n()

const hasRewards = computed(() => (props.user?.affQuota ?? 0) > 0)

const stats = computed(() => [
  { label: t('wallet.affiliate.pending'), value: formatQuotaWithCurrency(props.user?.affQuota ?? 0) },
  { label: t('wallet.affiliate.totalEarned'), value: formatQuotaWithCurrency(props.user?.affHistoryQuota ?? 0) },
  { label: t('wallet.affiliate.invites'), value: String(props.user?.affCount ?? 0) },
])
</script>

<template>
  <div class="affiliate-card">
    <div
      v-if="loading"
      class="affiliate-card__body"
    >
      <ElSkeleton
        :rows="3"
        animated
      />
    </div>
    <div
      v-else
      class="affiliate-card__body"
    >
      <div class="affiliate-card__intro">
        <div class="affiliate-card__icon-wrapper">
          <i class="i-ep-share affiliate-card__icon" />
        </div>
        <div class="affiliate-card__text">
          <h3 class="affiliate-card__title">
            {{ t('wallet.affiliate.title') }}
          </h3>
          <p class="affiliate-card__desc">
            {{ t('wallet.affiliate.description') }}
          </p>
        </div>
      </div>

      <div class="affiliate-card__stats">
        <div
          v-for="item in stats"
          :key="item.label"
          class="affiliate-card__stat"
        >
          <div class="affiliate-card__stat-label">
            {{ item.label }}
          </div>
          <div class="affiliate-card__stat-value">
            {{ item.value }}
          </div>
        </div>
      </div>

      <div class="affiliate-card__actions">
        <ElInput
          :model-value="affiliateLink"
          readonly
          class="affiliate-card__link-input"
        />
        <div class="affiliate-card__action-buttons">
          <CopyButton
            :value="affiliateLink"
            :tooltip="t('wallet.affiliate.copyLink')"
            :success-tooltip="t('common.copied')"
          />
          <ElButton
            v-if="hasRewards"
            type="primary"
            size="small"
            :disabled="!complianceConfirmed"
            @click="$emit('transfer')"
          >
            {{ t('wallet.affiliate.transferToBalance') }}
          </ElButton>
        </div>
      </div>

      <p
        v-if="!complianceConfirmed"
        class="affiliate-card__notice"
      >
        {{ t('wallet.affiliate.complianceNotice') }}
      </p>
    </div>
  </div>
</template>

<style scoped lang="scss">
.affiliate-card {
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--ys-radius-md);

  &__body {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-4);
    padding: var(--ys-spacing-4) var(--ys-spacing-5);
  }

  &__intro {
    display: flex;
    gap: 10px;
    align-items: center;
    min-width: 0;
  }

  &__icon-wrapper {
    display: flex;
    flex-shrink: 0;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    background: var(--el-fill-color-blank);
    border: 1px solid var(--el-border-color);
    border-radius: var(--ys-radius-md);
  }

  &__icon {
    font-size: var(--ys-font-size-lg);
    color: var(--el-text-color-secondary);
  }

  &__text {
    min-width: 0;
  }

  &__title {
    margin: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: var(--ys-font-size-base);
    font-weight: 600;
    white-space: nowrap;
  }

  &__desc {
    display: -webkit-box;
    margin: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    -webkit-line-clamp: 1;
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
    -webkit-box-orient: vertical;
  }

  &__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 6px;
    text-align: center;
  }

  &__stat-label {
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 10px;
    font-weight: 500;
    color: var(--el-text-color-secondary);
    text-transform: uppercase;
    letter-spacing: 0.05em;
    white-space: nowrap;
  }

  &__stat-value {
    margin-top: 2px;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: var(--ys-font-size-base);
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }

  &__actions {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
    align-items: stretch;
  }

  &__action-buttons {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
  }

  &__link-input {
    :deep(.el-input__inner) {
      font-family: 'JetBrains Mono', monospace;
      font-size: var(--ys-font-size-xs);
    }
  }

  &__notice {
    margin: 0;
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);

    @media (width >= 1024px) {
      grid-column: 1 / -1;
    }
  }
}
</style>
