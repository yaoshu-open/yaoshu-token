<script setup lang="ts">
import { computed, inject, ref, watch, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { CircleCheck, ArrowRight } from '@element-plus/icons-vue'

interface SettingsSectionProps {
  id: string
  title: string
  defaultExpanded?: boolean
  loading?: boolean
  dirty?: boolean
}

const props = withDefaults(defineProps<SettingsSectionProps>(), {
  defaultExpanded: false,
  loading: false,
  dirty: false,
})

const { t } = useI18n()

const ctx = inject<{
  registerSection: (id: string, dirty: Ref<boolean>) => void
  unregisterSection: (id: string) => void
}>('settingsPageContext', {
  registerSection: () => {},
  unregisterSection: () => {},
})

const dirtyRef = computed(() => props.dirty)
watch(dirtyRef, () => ctx.registerSection(props.id, dirtyRef), { immediate: true })

const expanded = ref(props.defaultExpanded)

function toggle() {
  expanded.value = !expanded.value
}
</script>

<template>
  <div class="settings-section">
    <div
      class="settings-section__header"
      @click="toggle"
    >
      <div class="settings-section__title-row">
        <ElIcon
          class="settings-section__toggle"
          :class="{ 'is-expanded': expanded }"
        >
          <ArrowRight />
        </ElIcon>
        <span class="settings-section__title">{{ title }}</span>
        <ElIcon
          v-if="dirty"
          class="settings-section__dirty-icon"
          :title="t('systemSettings.dirty')"
        >
          <CircleCheck />
        </ElIcon>
      </div>
    </div>
    <ElCollapseTransition>
      <div
        v-show="expanded"
        class="settings-section__body"
      >
        <ElSkeleton
          v-if="loading"
          :rows="4"
          animated
        />
        <slot v-else />
      </div>
    </ElCollapseTransition>
  </div>
</template>

<style scoped lang="scss">
.settings-section {
  overflow: hidden;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--ys-radius-md);

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 14px var(--ys-spacing-4);
    cursor: pointer;
    user-select: none;

    &:hover {
      background: var(--el-fill-color-light);
    }
  }

  &__title-row {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
  }

  &__toggle {
    font-size: var(--ys-font-size-base);
    color: var(--el-text-color-placeholder);
    transition: transform 0.2s;

    &.is-expanded {
      transform: rotate(90deg);
    }
  }

  &__title {
    font-size: var(--ys-font-size-base);
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__dirty-icon {
    font-size: var(--ys-font-size-base);
    color: var(--el-color-warning);
  }

  &__body {
    padding: var(--ys-spacing-4);
    border-top: 1px solid var(--el-border-color-lighter);
  }
}
</style>
