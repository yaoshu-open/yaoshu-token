<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import CommonLogsView from '@/components/usage-logs/common/CommonLogsView.vue'
import TaskLogsView from '@/components/usage-logs/task/TaskLogsView.vue'

type LogSection = 'common' | 'task'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const currentSection = computed<LogSection>(() => {
  const tab = route.params.tab as string
  return tab === 'task' ? 'task' : 'common'
})

const pageDesc = computed(() =>
  currentSection.value === 'task'
    ? t('usageLogs.taskDesc')
    : t('usageLogs.commonDesc')
)

function switchSection(section: LogSection) {
  router.push(`/usage-logs/${section}`)
}
</script>

<template>
  <div class="usage-logs-page">
    <div class="usage-logs-page__hero">
      <div class="usage-logs-page__title-row">
        <h1 class="usage-logs-page__title">
          {{ t('nav.usageLogs') }}
        </h1>
        <ElRadioGroup
          :model-value="currentSection"
          size="small"
          @update:model-value="(v) => switchSection(v as LogSection)"
        >
          <ElRadioButton value="common">
            {{ t('usageLogs.tabs.common') }}
          </ElRadioButton>
          <ElRadioButton value="task">
            {{ t('usageLogs.tabs.task') }}
          </ElRadioButton>
        </ElRadioGroup>
      </div>
      <p class="usage-logs-page__desc">
        {{ pageDesc }}
      </p>
    </div>

    <div class="usage-logs-page__content">
      <CommonLogsView v-if="currentSection === 'common'" />
      <TaskLogsView v-else />
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens' as *;

.usage-logs-page {
  display: flex;
  flex-direction: column;
  gap: $spacing-4;
  padding: $spacing-6;

  &__hero {
    display: flex;
    flex-direction: column;
    gap: $spacing-2;
  }

  &__title-row {
    display: flex;
    flex-wrap: wrap;
    gap: $spacing-3;
    align-items: center;
    justify-content: space-between;
  }

  &__title {
    margin: 0;
    font-size: $font-size-xl;
    font-weight: $font-weight-semibold;
    letter-spacing: -0.025em;
  }

  &__desc {
    margin: 0;
    font-size: $font-size-sm;
    color: var(--el-text-color-secondary);
  }

  &__placeholder {
    display: flex;
    flex-direction: column;
    gap: $spacing-3;
    align-items: center;
    padding: $spacing-12 0;
  }
}
</style>
