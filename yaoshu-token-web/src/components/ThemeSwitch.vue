<script setup lang="ts">
import { computed } from 'vue'
import { ElDropdown, ElDropdownMenu, ElDropdownItem } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useThemeStore } from '@/store/modules/theme'
import type { ThemeMode } from '@/store/modules/theme'
import { THEME_MODES } from '@/components/layout/constants'

interface Props {
  showLabel?: boolean
  size?: 'small' | 'default' | 'large'
}

withDefaults(defineProps<Props>(), {
  showLabel: false,
  size: 'default'
})

const { t } = useI18n()
const themeStore = useThemeStore()

const currentIcon = computed(() => {
  switch (themeStore.mode) {
    case 'light':
      return 'i-ep-sunny'
    case 'dark':
      return 'i-ep-moon'
    case 'system':
    default:
      return 'i-ep-monitor'
  }
})

function setMode(mode: ThemeMode) {
  themeStore.setMode(mode)
}
</script>

<template>
  <ElDropdown trigger="click">
    <button
      type="button"
      class="theme-switch"
      :class="`theme-switch--${size}`"
      :aria-label="t('layout.header.theme')"
      :title="t('layout.header.theme')"
    >
      <i :class="currentIcon" />
      <span
        v-if="showLabel"
        class="theme-switch__label"
      >{{ t(`theme.mode.${themeStore.mode}`) }}</span>
    </button>
    <template #dropdown>
      <ElDropdownMenu>
        <ElDropdownItem
          v-for="mode in THEME_MODES"
          :key="mode"
          :class="{ 'is-active': themeStore.mode === mode }"
          @click="setMode(mode)"
        >
          <i
            class="theme-switch__menu-icon"
            :class="{
              'i-ep-sunny': mode === 'light',
              'i-ep-moon': mode === 'dark',
              'i-ep-monitor': mode === 'system'
            }"
          />
          <span>{{ t(`theme.mode.${mode}`) }}</span>
        </ElDropdownItem>
      </ElDropdownMenu>
    </template>
  </ElDropdown>
</template>

<style scoped lang="scss">
.theme-switch {
  display: inline-flex;
  gap: var(--ys-spacing-1);
  align-items: center;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--el-border-radius-base);
  transition: background-color 0.2s, color 0.2s;

  &:hover {
    color: var(--el-text-color-primary);
    background: var(--el-fill-color-light);
  }

  &--small {
    justify-content: center;
    width: 28px;
    height: 28px;
    font-size: var(--ys-font-size-lg);
  }

  &--default {
    justify-content: center;
    width: 32px;
    height: 32px;
    font-size: 18px;
  }

  &--large {
    justify-content: center;
    width: 36px;
    height: 36px;
    font-size: var(--ys-font-size-xl);
  }

  &__label {
    margin-left: 4px;
    font-size: var(--el-font-size-base);
  }

  &__menu-icon {
    margin-right: 6px;
  }
}
</style>
