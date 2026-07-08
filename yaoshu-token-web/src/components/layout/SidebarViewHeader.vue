<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import type { SidebarView } from './types'

interface Props {
  view: SidebarView
}

const props = defineProps<Props>()
const router = useRouter()
const { t } = useI18n()
function goBack() {
  router.push(props.view.parent.to)
}
</script>

<template>
  <div class="sidebar-view-header">
    <button
      type="button"
      class="sidebar-view-header__back"
      :title="t(view.parent.label)"
      @click="goBack"
    >
      <i class="i-ep-arrow-left" />
      <span>{{ t(view.parent.label) }}</span>
    </button>
  </div>
</template>

<style scoped lang="scss">
.sidebar-view-header {
  padding: var(--ys-spacing-2) var(--ys-spacing-3);
  border-bottom: 1px solid var(--el-border-color-lighter);

  &__back {
    display: flex;
    gap: 6px;
    align-items: center;
    width: 100%;
    padding: 6px var(--ys-spacing-2);
    font-weight: 500;
    color: var(--el-text-color-secondary);
    cursor: pointer;
    background: transparent;
    border: 0;
    border-radius: var(--el-border-radius-base);
    transition: color 0.2s, background-color 0.2s;

    &:hover {
      color: var(--el-text-color-primary);
      background: var(--el-fill-color-light);
    }
  }
}
</style>
