<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search } from '@element-plus/icons-vue'
import PerfSummaryPanel from '@/components/perf-metrics/PerfSummaryPanel.vue'
import ModelDetailsPerformance from '@/components/perf-metrics/ModelDetailsPerformance.vue'

const { t } = useI18n()

// 预览页模型选择器：输入模型名触发单模型性能查询。
// pricing 详情 Performance tab 已复用 ModelDetailsPerformance（传入 model prop），此预览页保留手动查询能力。
const modelInput = ref('gpt-4o')
const activeModel = ref('gpt-4o')

function handleQuery() {
  const trimmed = modelInput.value.trim()
  if (trimmed) {
    activeModel.value = trimmed
  }
}
</script>

<template>
  <div class="perf-view">
    <header class="perf-view__header">
      <h1 class="perf-view__title">
        {{ t('performance.title') }}
      </h1>
      <p class="perf-view__subtitle">
        {{ t('performance.subtitle') }}
      </p>
    </header>

    <!-- 全局性能汇总面板（自取数） -->
    <PerfSummaryPanel />

    <!-- 模型选择器 -->
    <section class="perf-view__selector">
      <h2 class="perf-view__section-title">
        {{ t('performance.modelDetails') }}
      </h2>
      <div class="perf-view__input-row">
        <el-input
          v-model="modelInput"
          :placeholder="t('performance.modelPlaceholder')"
          clearable
          class="perf-view__input"
          @keyup.enter="handleQuery"
        />
        <el-button
          type="primary"
          :icon="Search"
          @click="handleQuery"
        >
          {{ t('performance.query') }}
        </el-button>
      </div>
    </section>

    <!-- 单模型性能详情（model prop 驱动自取数） -->
    <ModelDetailsPerformance
      v-if="activeModel"
      :model="activeModel"
    />
  </div>
</template>

<style scoped lang="scss">
.perf-view {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;

  &__header {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
  }

  &__title {
    margin: 0;
    font-size: 1.875rem;
    font-weight: 700;
    color: var(--el-text-color-primary);
  }

  &__subtitle {
    margin: 0;
    font-size: 0.875rem;
    color: var(--el-text-color-secondary);
  }

  &__selector {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
  }

  &__section-title {
    margin: 0;
    font-size: 1.125rem;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__input-row {
    display: flex;
    gap: 0.5rem;
    align-items: center;
  }

  &__input {
    max-width: 24rem;
  }
}
</style>
