<script setup lang="ts">
import { provide, ref, computed, watch, type Ref, type ComputedRef } from 'vue'

interface SettingsPageLayoutProps {
  title: string
  loading?: boolean
}

const props = defineProps<SettingsPageLayoutProps>()

const actionsContainer = ref<HTMLElement | null>(null)
const titleStatusContainer = ref<HTMLElement | null>(null)
const dirtySections = ref<Set<string>>(new Set())

function registerSection(sectionId: string, dirty: Ref<boolean>) {
  watch(dirty, (v) => {
    if (v) dirtySections.value.add(sectionId)
    else dirtySections.value.delete(sectionId)
  }, { immediate: true })
}

function unregisterSection(sectionId: string) {
  dirtySections.value.delete(sectionId)
}

const hasDirtySections: ComputedRef<boolean> = computed(() => dirtySections.value.size > 0)

provide('settingsPageContext', {
  actionsContainer,
  titleStatusContainer,
  registerSection,
  unregisterSection,
  hasDirtySections,
})
</script>

<template>
  <div class="settings-page-layout">
    <div class="settings-page-layout__header">
      <div class="settings-page-layout__title-row">
        <h1 class="settings-page-layout__title">
          {{ title }}
        </h1>
        <span
          ref="titleStatusContainer"
          class="settings-page-layout__status"
        />
      </div>
      <div
        ref="actionsContainer"
        class="settings-page-layout__actions"
      />
    </div>
    <div class="settings-page-layout__content">
      <ElSkeleton
        v-if="loading"
        :rows="8"
        animated
      />
      <slot v-else />
    </div>
  </div>
</template>

<style scoped lang="scss">
.settings-page-layout {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);
  padding: var(--ys-spacing-6);

  &__header {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-3);
    align-items: center;
    justify-content: space-between;
  }

  &__title-row {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
  }

  &__title {
    margin: 0;
    font-size: 22px;
    font-weight: 600;
    letter-spacing: -0.025em;
  }

  &__status {
    display: inline-flex;
    align-items: center;
  }

  &__actions {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-2);
    align-items: center;
    justify-content: flex-end;
  }

  &__content {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-4);
  }
}
</style>
