<script setup lang="ts">
/**
 * API 信息面板（PD-08 后续）。
 * 数据源：/api/status → apiInfo（已支持，管理员通过系统设置配置）。
 * 兜底：apiInfo 为空时用 location.origin 展示 Base URL。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElIcon } from 'element-plus'
import EmptyState from '@/components/EmptyState.vue'
import CopyButton from '@/components/common/CopyButton.vue'
import { useSystemConfigStore } from '@/store/modules/system-config'

interface ApiInfoItem {
  url?: string
  route?: string
  description?: string
  color?: string
}

const { t } = useI18n()
const systemConfig = useSystemConfigStore()

const items = computed<ApiInfoItem[]>(() => {
  const raw = systemConfig.rawStatus?.apiInfo
  return Array.isArray(raw) ? (raw as ApiInfoItem[]) : []
})

// 兜底：apiInfo 为空时，展示当前站点地址作为 API Base URL
const baseUrl = computed(() => {
  const addr = systemConfig.rawStatus?.serverAddress
  return addr && addr.trim() ? addr.trim() : (typeof window !== 'undefined' ? window.location.origin : '')
})

const hasAnyInfo = computed(() => items.value.length > 0 || baseUrl.value)

// 简单延迟测试（不做实际 ping，仅展示 URL）
function shortUrl(url: string): string {
  try {
    const u = new URL(url)
    return u.host
  } catch {
    return url
  }
}
</script>

<template>
  <div class="api-info-panel">
    <div class="api-info-panel__header">
      <ElIcon class="api-info-panel__icon">
        <i class="i-ep-guide" />
      </ElIcon>
      <span class="api-info-panel__title">{{ t('dashboard.apiInfo.title') }}</span>
    </div>
    <div
      v-if="!hasAnyInfo"
      class="api-info-panel__empty"
    >
      <EmptyState :description="t('dashboard.apiInfo.empty')" />
    </div>
    <div
      v-else
      class="api-info-panel__content"
    >
      <!-- Base URL 兜底展示：serverAddress 为空时用 location.origin -->
      <div
        v-if="baseUrl"
        class="api-info-panel__server-address"
      >
        <span class="api-info-panel__route">Base URL</span>
        <span class="api-info-panel__url">{{ baseUrl }}</span>
        <CopyButton
          :value="baseUrl"
          :tooltip="t('common.copy')"
          :success-tooltip="t('common.copied')"
        />
      </div>
      <ul
        v-if="items.length > 0"
        class="api-info-panel__list"
      >
        <li
          v-for="(item, idx) in items"
          :key="idx"
          class="api-info-panel__item"
        >
          <div class="api-info-panel__item-header">
            <span
              v-if="item.route"
              class="api-info-panel__route"
            >{{ item.route }}</span>
            <span class="api-info-panel__url">{{ shortUrl(item.url ?? '') }}</span>
          </div>
          <p
            v-if="item.description"
            class="api-info-panel__desc"
          >{{ item.description }}</p>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens' as *;

.api-info-panel {
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

  &__server-address {
    display: flex;
    gap: $spacing-2;
    align-items: center;
    padding: $spacing-2 0;
    margin-bottom: $spacing-2;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  &__list {
    padding: 0;
    margin: 0;
    list-style: none;
  }

  &__item {
    padding: $spacing-2 0;
    border-bottom: 1px solid var(--el-border-color-lighter);

    &:last-child {
      border-bottom: none;
    }
  }

  &__item-header {
    display: flex;
    gap: $spacing-2;
    align-items: center;
    font-size: $font-size-sm;
  }

  &__route {
    font-family: var(--el-font-family-mono, monospace);
    color: var(--el-color-primary);
  }

  &__url {
    font-family: var(--el-font-family-mono, monospace);
    color: var(--el-text-color-primary);
  }

  &__desc {
    margin: var(--ys-spacing-1) 0 0;
    font-size: $font-size-xs;
    color: var(--el-text-color-secondary);
  }
}
</style>
