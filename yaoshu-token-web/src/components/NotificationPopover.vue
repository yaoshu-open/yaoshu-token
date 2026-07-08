<script setup lang="ts">
import { computed } from 'vue'
import { ElPopover, ElButton, ElBadge, ElEmpty, ElSkeleton } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { renderMarkdown } from '@/utils/markdown'
import type { Announcement } from '@/api/system/types'

interface Props {
  modelValue: boolean
  unreadCount: number
  announcements: Announcement[]
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'markAllAnnouncementsRead'): void
}>()

const { t } = useI18n()

const popoverVisible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v)
})

function markAllRead() {
  emit('markAllAnnouncementsRead')
}

function openAnnouncementLink(item: Announcement) {
  if (typeof item.link === 'string' && item.link) {
    window.open(item.link, '_blank', 'noopener,noreferrer')
  }
}
</script>

<template>
  <ElPopover
    v-model:visible="popoverVisible"
    placement="bottom-end"
    :width="380"
    trigger="click"
    popper-class="notification-popover"
  >
    <template #reference>
      <ElBadge
        :value="unreadCount"
        :hidden="unreadCount === 0"
        :max="99"
      >
        <button
          type="button"
          class="notification-popover__trigger"
          :aria-label="t('layout.header.notifications')"
        >
          <i class="i-ep-bell" />
        </button>
      </ElBadge>
    </template>

    <div class="notification-popover__panel">
      <header class="notification-popover__header">
        <span class="notification-popover__title">{{ t('notification.title') }}</span>
        <ElButton
          text
          size="small"
          :disabled="unreadCount === 0"
          @click="markAllRead"
        >
          {{ t('notification.markAllRead') }}
        </ElButton>
      </header>

      <div class="notification-popover__pane">
        <ElSkeleton
          v-if="loading"
          :rows="4"
          animated
        />
        <ul
          v-else-if="announcements.length"
          class="notification-popover__list"
        >
          <li
            v-for="(item, idx) in announcements"
            :key="idx"
            class="notification-popover__item"
            :class="{ 'is-link': item.link }"
            @click="openAnnouncementLink(item)"
          >
            <h5
              v-if="item.title"
              class="notification-popover__item-title"
            >
              {{ item.title }}
            </h5>
            <div
              v-if="item.content"
              class="notification-popover__item-content markdown-body"
              v-html="renderMarkdown(item.content)"
            />
            <div
              v-if="item.extra"
              class="notification-popover__item-extra markdown-body"
              v-html="renderMarkdown(item.extra)"
            />
            <time
              v-if="item.publishDate"
              class="notification-popover__item-date"
            >{{ t('notification.publishDate', { date: item.publishDate }) }}</time>
          </li>
        </ul>
        <ElEmpty
          v-else
          :description="t('notification.empty')"
          :image-size="60"
        />
      </div>
    </div>
  </ElPopover>
</template>

<style scoped lang="scss">
.notification-popover {
  &__trigger {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
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
  }

  &__panel {
    margin: -var(--ys-spacing-3) -var(--ys-spacing-2);
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--ys-spacing-2) var(--ys-spacing-3);
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  &__title {
    font-size: var(--el-font-size-base);
    font-weight: 500;
    color: var(--el-text-color-primary);
  }

  &__pane {
    max-height: 320px;
    padding: 0 var(--ys-spacing-3);
    overflow-y: auto;
  }

  &__list {
    padding: 0;
    margin: 0;
    list-style: none;
  }

  &__item {
    padding: var(--ys-spacing-2) 0;
    border-bottom: 1px solid var(--el-border-color-lighter);

    &:last-child {
      border-bottom: 0;
    }

    &.is-link {
      cursor: pointer;

      &:hover {
        background: var(--el-fill-color-light);
      }
    }
  }

  &__item-title {
    margin: 0 0 var(--ys-spacing-1);
    font-size: var(--el-font-size-base);
    font-weight: 500;
    color: var(--el-text-color-primary);
  }

  &__item-content {
    margin: 0;
    font-size: var(--el-font-size-small);
    line-height: 1.6;
    color: var(--el-text-color-regular);
  }

  &__item-date {
    display: block;
    margin-top: 4px;
    font-size: var(--el-font-size-extra-small);
    color: var(--el-text-color-secondary);
  }
}
</style>
