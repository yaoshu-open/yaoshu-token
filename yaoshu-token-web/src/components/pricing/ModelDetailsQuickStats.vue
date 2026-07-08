<script setup lang="ts">
// 5 项统计：Context / Modalities / Max output / Knowledge cutoff / Released
import { computed } from 'vue'
import {
  Clock,
  Connection,
  Document,
  FullScreen,
  MagicStick
} from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { formatTokenCount, formatYearMonth, type ModelMetadata } from '@/views/pricing/lib/model-metadata'
import ModelDetailsModalities from './ModelDetailsModalities.vue'

const props = defineProps<{
  metadata: ModelMetadata
}>()

const { t } = useI18n()

interface StatItem {
  key: string
  icon: any
  label: string
  value: string | number
  hint?: string
}

const stats = computed<StatItem[]>(() => {
  const result: StatItem[] = [
    {
      key: 'context',
      icon: Document,
      label: t('pricing.context'),
      value: formatTokenCount(props.metadata.contextLength),
      hint: t('pricing.maxInputWindow')
    },
    {
      key: 'modalities',
      icon: Connection,
      label: t('pricing.modalities'),
      value: ''
    }
  ]
  if (props.metadata.maxOutputTokens > 0) {
    result.push({
      key: 'max-output',
      icon: FullScreen,
      label: t('pricing.maxOutput'),
      value: formatTokenCount(props.metadata.maxOutputTokens),
      hint: t('pricing.maxTokensPerResponse')
    })
  }
  if (props.metadata.knowledgeCutoff) {
    result.push({
      key: 'knowledge',
      icon: MagicStick,
      label: t('pricing.knowledgeCutoff'),
      value: formatYearMonth(props.metadata.knowledgeCutoff)
    })
  }
  if (props.metadata.releaseDate) {
    result.push({
      key: 'release',
      icon: Clock,
      label: t('pricing.released'),
      value: formatYearMonth(props.metadata.releaseDate)
    })
  }
  return result
})
</script>

<template>
  <div class="quick-stats">
    <div
      v-for="stat in stats"
      :key="stat.key"
      class="quick-stats__item"
    >
      <span class="quick-stats__label">
        <el-icon :size="12"><component :is="stat.icon" /></el-icon>
        {{ stat.label }}
      </span>
      <span
        v-if="stat.key === 'modalities'"
        class="quick-stats__value quick-stats__modalities"
      >
        <ModelDetailsModalities :modalities="metadata.inputModalities" />
        <span class="quick-stats__arrow">→</span>
        <ModelDetailsModalities :modalities="metadata.outputModalities" />
      </span>
      <span
        v-else
        class="quick-stats__value"
      >{{ stat.value }}</span>
      <span
        v-if="stat.hint"
        class="quick-stats__hint"
      >{{ stat.hint }}</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.quick-stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1px;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: var(--ys-radius-md);

  @media (width >= 768px) {
    grid-template-columns: repeat(3, 1fr);
  }

  @media (width >= 1280px) {
    grid-template-columns: repeat(5, 1fr);
  }

  &__item {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 10px var(--ys-spacing-3);
    background: var(--el-bg-color);
  }

  &__label {
    display: inline-flex;
    gap: var(--ys-spacing-1);
    align-items: center;
    font-size: 10px;
    font-weight: 500;
    color: var(--el-text-color-secondary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  &__value {
    font-size: var(--ys-font-size-base);
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    color: var(--el-text-color-primary);
  }

  &__modalities {
    display: inline-flex;
    gap: var(--ys-spacing-1);
    align-items: center;
  }

  &__arrow {
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-placeholder);
  }

  &__hint {
    font-size: 10px;
    color: var(--el-text-color-placeholder);
  }
}
</style>
