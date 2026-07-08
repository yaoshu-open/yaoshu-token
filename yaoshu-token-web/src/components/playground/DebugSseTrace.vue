<script setup lang="ts">
/**
 * DebugSseTrace - SSE 事件流时间线（实时追加，每条事件展示 raw + delta 摘要）。
 */
import { computed, ref, watch, nextTick } from 'vue'
import { Check, CircleClose, DocumentCopy } from '@element-plus/icons-vue'
import { useCopyToClipboard } from '@/composables/useCopyToClipboard'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import type { SseEventRecord } from '@/api/playground/types'

interface Props {
  events: SseEventRecord[]
}

const props = defineProps<Props>()

const { t } = useI18n()

const { copy } = useCopyToClipboard({ notify: false })
const listRef = ref<HTMLElement | null>(null)

watch(
  () => props.events.length,
  () => {
    nextTick(() => {
      if (listRef.value) {
        listRef.value.scrollTop = listRef.value.scrollHeight
      }
    })
  }
)

const summary = computed(() => ({
  total: props.events.length,
  errors: props.events.filter((e) => e.isError).length,
  done: props.events.filter((e) => e.isDone).length
}))

function formatDelta(delta: SseEventRecord['delta']): string {
  if (!delta) return ''
  const parts: string[] = []
  if (delta.content) parts.push(`content: ${truncate(delta.content, 30)}`)
  if (delta.reasoning_content) parts.push(`reasoning: ${truncate(delta.reasoning_content, 30)}`)
  return parts.join(' · ')
}

function truncate(text: string, n: number): string {
  if (text.length <= n) return text
  return text.slice(0, n) + '...'
}

function formatTime(timestamp: number): string {
  const d = new Date(timestamp)
  return d.toLocaleTimeString('zh-CN', { hour12: false })
}

async function copyAll(): Promise<void> {
  const text = props.events
    .map((e) => e.raw)
    .join('\n\n')
  const ok = await copy(text)
  if (ok) ElMessage.success(t('playground.debug.copiedAllEvents'))
}

async function copyOne(item: SseEventRecord): Promise<void> {
  const ok = await copy(item.raw)
  if (ok) ElMessage.success(t('playground.debug.copiedSingleEvent'))
}
</script>

<template>
  <div class="debug-sse-trace">
    <div class="debug-sse-trace__header">
      <div class="debug-sse-trace__stats">
        <el-tag
          size="small"
          type="info"
          round
        >
          Total: {{ summary.total }}
        </el-tag>
        <el-tag
          v-if="summary.errors > 0"
          size="small"
          type="danger"
          round
        >
          Errors: {{ summary.errors }}
        </el-tag>
        <el-tag
          v-if="summary.done > 0"
          size="small"
          type="success"
          round
        >
          Done: {{ summary.done }}
        </el-tag>
      </div>
      <el-button
        size="small"
        :icon="DocumentCopy"
        :disabled="events.length === 0"
        @click="copyAll"
      >
        {{ $t('playground.debug.copyAll') }}
      </el-button>
    </div>

    <div
      ref="listRef"
      class="debug-sse-trace__list"
    >
      <el-empty
        v-if="events.length === 0"
        :description="$t('playground.debug.emptySse')"
        :image-size="80"
      />
      <div
        v-for="item in events"
        :key="item.id"
        class="debug-sse-trace__item"
        :class="{
          'is-done': item.isDone,
          'is-error': item.isError
        }"
      >
        <div class="debug-sse-trace__item-header">
          <span class="debug-sse-trace__time">{{ formatTime(item.timestamp) }}</span>
          <el-icon
            v-if="item.isDone"
            class="is-done-icon"
          >
            <Check />
          </el-icon>
          <el-icon
            v-else-if="item.isError"
            class="is-error-icon"
          >
            <CircleClose />
          </el-icon>
          <el-button
            v-if="!item.isDone && !item.isError"
            :icon="DocumentCopy"
            size="small"
            link
            @click="copyOne(item)"
          />
        </div>
        <pre
          v-if="item.isDone"
          class="debug-sse-trace__raw"
        >[DONE]</pre>
        <pre
          v-else
          class="debug-sse-trace__raw"
        >{{ item.raw }}</pre>
        <div
          v-if="formatDelta(item.delta)"
          class="debug-sse-trace__delta"
        >
          {{ formatDelta(item.delta) }}
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.debug-sse-trace {
  display: flex;
  flex-direction: column;
  height: 100%;

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--ys-spacing-2) var(--ys-spacing-3);
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  &__stats {
    display: flex;
    gap: 6px;
    align-items: center;
  }

  &__list {
    flex: 1;
    padding: var(--ys-spacing-2) var(--ys-spacing-3);
    overflow-y: auto;
  }

  &__item {
    padding: var(--ys-spacing-2) var(--ys-spacing-3);
    margin-bottom: 8px;
    background: var(--el-fill-color-blank);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--ys-radius-base);

    &.is-done {
      background: var(--el-color-success-light-9);
      border-color: var(--el-color-success-light-5);
    }

    &.is-error {
      background: var(--el-color-danger-light-9);
      border-color: var(--el-color-danger-light-5);
    }
  }

  &__item-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 4px;
  }

  &__time {
    font-size: 11px;
    color: var(--el-text-color-secondary);
  }

  &__raw {
    margin: 0;
    font-family: SFMono-Regular, Consolas, 'Liberation Mono', Menlo, monospace;
    font-size: 11px;
    line-height: 1.4;
    color: var(--el-text-color-regular);
    word-break: break-all;
    white-space: pre-wrap;
  }

  &__delta {
    padding: var(--ys-spacing-1) var(--ys-spacing-2);
    margin-top: 4px;
    font-size: 11px;
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
    border-radius: var(--ys-radius-sm);
  }

  .is-done-icon {
    color: var(--el-color-success);
  }

  .is-error-icon {
    color: var(--el-color-danger);
  }
}
</style>
