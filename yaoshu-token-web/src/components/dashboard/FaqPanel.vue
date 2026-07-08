<script setup lang="ts">
/**
 * FAQ 面板（PD-08 后续）。
 * 数据源：/api/status → faq（已支持，管理员通过系统设置配置）。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElIcon, ElCollapse, ElCollapseItem } from 'element-plus'
import EmptyState from '@/components/EmptyState.vue'
import { useSystemConfigStore } from '@/store/modules/system-config'

interface FaqItem {
  id?: number
  question: string
  answer: string
}

const { t } = useI18n()
const systemConfig = useSystemConfigStore()

const items = computed<FaqItem[]>(() => {
  const raw = systemConfig.rawStatus?.faq
  return Array.isArray(raw) ? (raw as FaqItem[]) : []
})

const activeNames = ref<string[]>([])
</script>

<template>
  <div class="faq-panel">
    <div class="faq-panel__header">
      <ElIcon class="faq-panel__icon">
        <i class="i-ep-aim" />
      </ElIcon>
      <span class="faq-panel__title">{{ t('dashboard.faq.title') }}</span>
    </div>
    <div
      v-if="items.length === 0"
      class="faq-panel__empty"
    >
      <EmptyState :description="t('dashboard.faq.empty')" />
    </div>
    <ElCollapse
      v-else
      v-model="activeNames"
      class="faq-panel__collapse"
    >
      <ElCollapseItem
        v-for="(item, idx) in items"
        :key="item.id ?? idx"
        :name="String(item.id ?? idx)"
      >
        <template #title>
          <span class="faq-panel__question">{{ item.question }}</span>
        </template>
        <div class="faq-panel__answer">{{ item.answer }}</div>
      </ElCollapseItem>
    </ElCollapse>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens' as *;

.faq-panel {
  padding: $spacing-4;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: $radius-md;

  &__header {
    display: flex;
    gap: $spacing-2;
    align-items: center;
    margin-bottom: $spacing-3;
  }

  &__icon {
    font-size: var(--ys-font-size-lg);
    color: var(--el-text-color-secondary);
  }

  &__title {
    font-size: $font-size-base;
    font-weight: $font-weight-medium;
    color: var(--el-text-color-primary);
  }

  &__empty {
    padding: $spacing-6 0;
  }

  &__question {
    font-size: $font-size-sm;
    font-weight: $font-weight-medium;
    color: var(--el-text-color-primary);
  }

  &__answer {
    font-size: $font-size-sm;
    line-height: 1.6;
    color: var(--el-text-color-secondary);
    white-space: pre-wrap;
  }
}
</style>
