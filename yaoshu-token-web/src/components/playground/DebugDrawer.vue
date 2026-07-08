<script setup lang="ts">
/**
 * DebugDrawer - ElDrawer 右侧唤起（预览请求体 / 实际请求 / 响应 SSE 三 Tab）。
 */
import { computed } from 'vue'
import { View, Promotion, Lightning } from '@element-plus/icons-vue'
import type { ChatCompletionRequest, SseEventRecord, DebugTab } from '@/api/playground/types'
import DebugJsonView from './DebugJsonView.vue'
import DebugSseTrace from './DebugSseTrace.vue'

interface Props {
  modelValue: boolean
  previewPayload: ChatCompletionRequest | null
  actualRequest: ChatCompletionRequest | null
  sseEvents: SseEventRecord[]
  activeTab: DebugTab
  customRequestMode: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'update:activeTab': [value: DebugTab]
  clearSse: []
}>()

const sseEventCount = computed(() => props.sseEvents.length)

function handleClose(): void {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    direction="rtl"
    size="560px"
    :with-header="true"
    :title="$t('playground.drawer.debug')"
    @update:model-value="emit('update:modelValue', $event)"
    @close="handleClose"
  >
    <div class="debug-drawer">
      <el-tabs
        :model-value="activeTab"
        class="debug-drawer__tabs"
        @update:model-value="(v: string | number) => emit('update:activeTab', v as DebugTab)"
      >
        <el-tab-pane name="preview">
          <template #label>
            <span class="debug-drawer__tab">
              <el-icon><View /></el-icon>
              <span>{{ $t('playground.debug.preview') }}</span>
              <el-tag
                v-if="customRequestMode"
                size="small"
                type="warning"
                effect="dark"
              >
                {{ $t('playground.debug.custom') }}
              </el-tag>
            </span>
          </template>
          <DebugJsonView
            :data="previewPayload"
            mode="preview"
          />
        </el-tab-pane>

        <el-tab-pane name="actual">
          <template #label>
            <span class="debug-drawer__tab">
              <el-icon><Promotion /></el-icon>
              <span>{{ $t('playground.debug.actual') }}</span>
            </span>
          </template>
          <DebugJsonView
            :data="actualRequest"
            mode="actual"
          />
        </el-tab-pane>

        <el-tab-pane name="sse">
          <template #label>
            <span class="debug-drawer__tab">
              <el-icon><Lightning /></el-icon>
              <span>{{ $t('playground.debug.response') }}</span>
              <el-tag
                v-if="sseEventCount > 0"
                size="small"
                type="primary"
                effect="dark"
              >
                SSE ({{ sseEventCount }})
              </el-tag>
            </span>
          </template>
          <div class="debug-drawer__sse">
            <div class="debug-drawer__sse-toolbar">
              <el-button
                size="small"
                :disabled="sseEventCount === 0"
                @click="emit('clearSse')"
              >
                {{ $t('playground.debug.clearSse') }}
              </el-button>
            </div>
            <DebugSseTrace :events="sseEvents" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
.debug-drawer {
  display: flex;
  flex-direction: column;
  height: 100%;

  &__tabs {
    display: flex;
    flex-direction: column;
    height: 100%;

    :deep(.el-tabs__content) {
      flex: 1;
      overflow: hidden;
    }

    :deep(.el-tab-pane) {
      height: 100%;
    }
  }

  &__tab {
    display: inline-flex;
    gap: var(--ys-spacing-1);
    align-items: center;
  }

  &__sse {
    display: flex;
    flex-direction: column;
    height: 100%;
  }

  &__sse-toolbar {
    display: flex;
    justify-content: flex-end;
    padding: var(--ys-spacing-1) 0;
  }
}
</style>
