<script setup lang="ts">
/**
 * ContextUsageIndicator — Playground 顶部工具栏的上下文用量显示。
 *
 * 本次实现：仅显示已用 tokens（数据源 = 最后一次请求的 total_tokens）。
 * 向前规范：后端 /api/user/models 补 max_context 字段后，增强为占比进度条。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

interface Props {
  /** 当前对话占用 tokens（无 usage 时 undefined） */
  tokens: number | undefined
  /** 模型 max_context（后端补字段后启用，本次 undefined） */
  maxContext?: number
}

const props = defineProps<Props>()
const { t } = useI18n()

const hasUsage = computed(() => props.tokens !== undefined && props.tokens > 0)
const hasMaxContext = computed(() => props.maxContext !== undefined && props.maxContext > 0)

const percentage = computed(() => {
  if (!hasMaxContext.value || !hasUsage.value) return 0
  return Math.min(100, Math.round(((props.tokens as number) / (props.maxContext as number)) * 100))
})

// 占比越高颜色越警示
const percentageColor = computed(() => {
  if (!hasMaxContext.value) return ''
  const pct = percentage.value
  if (pct >= 90) return 'var(--el-color-danger)'
  if (pct >= 70) return 'var(--el-color-warning)'
  return 'var(--el-color-primary)'
})

function formatTokens(n: number): string {
  return n.toLocaleString()
}
</script>

<template>
  <div
    v-if="hasUsage"
    class="context-usage"
  >
    <i class="i-ep-data-analysis context-usage__icon" />
    <span class="context-usage__text">
      {{ t('playground.context.usage') }}：{{ formatTokens(tokens!) }}
      <template v-if="hasMaxContext">
        <span class="context-usage__sep"> / </span>
        <span class="context-usage__max">{{ formatTokens(maxContext!) }}</span>
        <span
          class="context-usage__pct"
          :style="{ color: percentageColor }"
        >
          ({{ percentage }}%)
        </span>
      </template>
      <span class="context-usage__unit"> tokens</span>
    </span>
  </div>
</template>

<style scoped lang="scss">
.context-usage {
  display: inline-flex;
  gap: var(--ys-spacing-1);
  align-items: center;
  padding: 2px var(--ys-spacing-2);
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-radius: var(--el-border-radius-small);

  &__icon {
    font-size: var(--ys-font-size-base);
  }

  &__text {
    font-variant-numeric: tabular-nums;
  }

  &__max {
    color: var(--el-text-color-placeholder);
  }

  &__pct {
    margin-left: 2px;
    font-weight: 600;
  }

  &__unit {
    color: var(--el-text-color-placeholder);
  }
}
</style>
