<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Bell } from '@element-plus/icons-vue'
import type { Announcement } from '@/api/system/types'

interface AnnouncementsPanelProps {
  items: Announcement[]
  loading?: boolean
}

const props = defineProps<AnnouncementsPanelProps>()
const { t } = useI18n()

const selected = ref<Announcement | null>(null)
const dialogVisible = ref(false)

const typeColorMap: Record<string, string> = {
  success: 'var(--el-color-success)',
  warning: 'var(--el-color-warning)',
  danger: 'var(--el-color-danger)',
  info: 'var(--el-color-info)',
}

function typeColor(type?: string): string {
  return typeColorMap[type || 'info'] || typeColorMap.info
}

function preview(content?: string): string {
  if (!content) return ''
  return content.length > 80 ? content.slice(0, 80) + '...' : content
}

function openDetail(item: Announcement) {
  selected.value = item
  dialogVisible.value = true
}

const hasItems = computed(() => props.items.length > 0)
</script>

<template>
  <div class="announcements-panel">
    <div class="announcements-panel__header">
      <ElIcon class="announcements-panel__icon">
        <Bell />
      </ElIcon>
      <span class="announcements-panel__title">{{ t('dashboard.announcements') }}</span>
    </div>

    <ElSkeleton
      v-if="loading"
      :rows="4"
      animated
    />

    <ElEmpty
      v-else-if="!hasItems"
      :description="t('dashboard.noAnnouncements')"
      :image-size="60"
    />

    <div
      v-else
      class="announcements-panel__list"
    >
      <div
        v-for="item in items"
        :key="item.id || item.publishDate"
        class="announcements-panel__item"
        @click="openDetail(item)"
      >
        <span
          class="announcements-panel__dot"
          :style="{ background: typeColor(item.type) }"
        />
        <div class="announcements-panel__content">
          <p class="announcements-panel__preview">
            {{ preview(item.content) }}
          </p>
          <span
            v-if="item.publishDate"
            class="announcements-panel__date"
          >{{ item.publishDate }}</span>
        </div>
      </div>
    </div>

    <ElDialog
      v-model="dialogVisible"
      :title="t('dashboard.announcementDetail')"
      width="600px"
    >
      <div
        v-if="selected"
        class="announcements-panel__detail"
      >
        <div
          v-if="selected.publishDate"
          class="announcements-panel__detail-date"
        >
          {{ selected.publishDate }}
        </div>
        <div class="announcements-panel__detail-content">
          {{ selected.content }}
        </div>
      </div>
    </ElDialog>
  </div>
</template>

<style scoped lang="scss">
.announcements-panel {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-3);
  padding: var(--ys-spacing-5);
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--ys-radius-lg);

  &__header {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
  }

  &__icon {
    font-size: var(--ys-font-size-lg);
    color: var(--el-text-color-secondary);
  }

  &__title {
    font-size: 15px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__list {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
  }

  &__item {
    display: flex;
    gap: 10px;
    padding: 10px;
    cursor: pointer;
    border-radius: var(--ys-radius-md);
    transition: background 0.2s;

    &:hover {
      background: var(--el-fill-color-light);
    }
  }

  &__dot {
    flex-shrink: 0;
    width: 6px;
    height: 6px;
    margin-top: 6px;
    border-radius: 50%;
  }

  &__content {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-1);
    min-width: 0;
  }

  &__preview {
    margin: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-primary);
    white-space: nowrap;
  }

  &__date {
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-placeholder);
  }

  &__detail {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-3);
  }

  &__detail-date {
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-placeholder);
  }

  &__detail-content {
    font-size: var(--ys-font-size-base);
    line-height: 1.6;
    color: var(--el-text-color-primary);
    white-space: pre-wrap;
  }
}
</style>
