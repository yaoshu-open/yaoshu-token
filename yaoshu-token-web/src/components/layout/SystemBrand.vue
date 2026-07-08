<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useSystemConfig } from '@/composables/useSystemConfig'
import { useStatus } from '@/composables/useStatus'
import { DEFAULT_SYSTEM_NAME } from '@/store/modules/system-config'

interface Props {
  variant?: 'sidebar' | 'inline'
  defaultName?: string
  defaultVersion?: string
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'sidebar',
  defaultName: undefined,
  defaultVersion: undefined
})

const { t } = useI18n()
const router = useRouter()
// 仅取 logo（站点名/版本走 status 派生，避免双数据源不一致）
const { logo } = useSystemConfig()
const { status } = useStatus()

// 站点名优先级：后端 status.systemName > 调用方 defaultName > 内置 DEFAULT_SYSTEM_NAME
const displayName = computed(() => {
  const sn = status.value?.systemName
  return sn || props.defaultName || DEFAULT_SYSTEM_NAME
})

// 版本号：后端 status.version > 调用方 defaultVersion > i18n 'Unknown version' 占位
const displayVersion = computed(() => {
  const v = status.value?.version as string | undefined
  return v || props.defaultVersion || ''
})

function goHome() {
  router.push('/')
}
</script>

<template>
  <!-- inline 形态：顶部紧凑 pill，点击回首页 -->
  <button
    v-if="variant === 'inline'"
    type="button"
    class="system-brand system-brand--inline"
    :aria-label="t('layout.auth.goHome')"
    @click="goHome"
  >
    <div class="system-brand__logo system-brand__logo--inline">
      <img
        :src="logo"
        :alt="t('layout.auth.logoAlt')"
        class="system-brand__img"
      >
    </div>
    <span class="system-brand__name system-brand__name--inline">{{ displayName }}</span>
  </button>

  <!-- sidebar 形态：侧边栏头部卡片，展示但不导航 -->
  <div
    v-else
    class="system-brand system-brand--sidebar"
  >
    <div class="system-brand__logo system-brand__logo--sidebar">
      <img
        :src="logo"
        :alt="t('layout.auth.logoAlt')"
        class="system-brand__img"
      >
    </div>
    <div class="system-brand__meta">
      <span class="system-brand__name">{{ displayName }}</span>
      <span
        v-if="displayVersion"
        class="system-brand__version"
      >{{ displayVersion }}</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.system-brand {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: var(--ys-spacing-1) 6px;
  font-size: var(--el-font-size-base);
  color: var(--el-text-color-primary);
  cursor: pointer;
  background: transparent;
  border: 0;
  transition: background-color 0.2s;

  &--inline {
    height: 28px;
    border-radius: var(--el-border-radius-base);

    &:hover {
      background: var(--el-fill-color-light);
    }

    &:focus-visible {
      outline: 2px solid var(--el-color-primary);
      outline-offset: 2px;
    }
  }

  &--sidebar {
    width: 100%;
    padding: var(--ys-spacing-2) var(--ys-spacing-3);
    cursor: default;
  }

  &__logo {
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    background: var(--el-fill-color-light);
    border-radius: var(--el-border-radius-base);

    &--inline {
      width: 20px;
      height: 20px;
    }

    &--sidebar {
      width: 32px;
      height: 32px;
    }
  }

  &__img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    border-radius: inherit;
  }

  &__meta {
    display: flex;
    flex-direction: column;
    gap: 2px;
    text-align: left;
  }

  &__name {
    max-width: 12rem;
    overflow: hidden;
    text-overflow: ellipsis;
    font-weight: 500;
    white-space: nowrap;

    &--inline {
      font-size: var(--el-font-size-base);
    }
  }

  &__version {
    font-size: var(--el-font-size-extra-small);
    color: var(--el-text-color-secondary);
  }
}
</style>
