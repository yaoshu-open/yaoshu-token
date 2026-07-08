<script setup lang="ts">
/**
 * Ollama 模型管理对话框：模型列表 + SSE 流式 pull + 删除 + 应用到渠道。
 *
 * SSE pull 用原生 fetch + ReadableStream（不走 Axios），AbortController 可中断。
 * Mock 环境下 pull 降级为 toast 提示。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  deleteOllamaModel,
  fetchModels,
  fetchUpstreamModels,
  updateChannel
} from '@/api/channel'
import {
  formatBytes,
  normalizeOllamaModels,
  resolveOllamaBaseUrl,
  type OllamaModel,
  type PullProgress
} from '@/lib/channel/ollama-utils'
import type { Channel } from '@/api/channel/types'

const CHANNEL_TYPE_OLLAMA = 4

const props = defineProps<{
  modelValue: boolean
  channel: Channel | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}>()

const { t } = useI18n()

const isOllamaChannel = computed(() => props.channel?.type === CHANNEL_TYPE_OLLAMA)
const channelId = computed(() => props.channel?.id ?? 0)

const isFetching = ref(false)
const models = ref<OllamaModel[]>([])
const selected = ref<string[]>([])
const search = ref('')

const pullName = ref('')
const isPulling = ref(false)
const pullProgress = ref<PullProgress | null>(null)
let pullAbortController: AbortController | null = null

const deleteOpen = ref(false)
const deleteTarget = ref<string | null>(null)
const isDeleting = ref(false)

const filteredModels = computed(() => {
  if (!search.value.trim()) return models.value
  const kw = search.value.trim().toLowerCase()
  return models.value.filter((m) => m.id.toLowerCase().includes(kw))
})

const existingModels = computed(() => {
  const raw = props.channel?.models ?? ''
  return raw
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
})

// 打开/关闭时重置状态
watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      models.value = []
      selected.value = []
      search.value = ''
      pullName.value = ''
      isPulling.value = false
      pullProgress.value = null
      pullAbortController?.abort()
      pullAbortController = null
      return
    }

    if (open && isOllamaChannel.value && channelId.value) {
      void fetchOllamaModels()
    }
  }
)

/** 拉取 Ollama 模型列表（优先 live fetch，fallback 到 server-side） */
async function fetchOllamaModels(): Promise<void> {
  if (!channelId.value) return
  isFetching.value = true
  try {
    let normalized: OllamaModel[] = []
    let lastErr = ''

    // 1) 优先从自定义端点 live fetch（支持未保存的变更）
    const baseUrl = resolveOllamaBaseUrl(props.channel)
    if (isOllamaChannel.value && baseUrl) {
      try {
        const payloadLive = await fetchModels({
          baseUrl: baseUrl,
          type: CHANNEL_TYPE_OLLAMA,
          key: typeof props.channel?.key === 'string' ? props.channel.key : ''
        })
        if (Array.isArray(payloadLive)) {
          normalized = normalizeOllamaModels(payloadLive)
        }
      } catch (err) {
        lastErr = (err as Error)?.message || ''
      }
    }

    // 2) Fallback 到 server-side fetch by channelId
    if (!normalized.length) {
      const payload = await fetchUpstreamModels(Number(channelId.value))
      if (Array.isArray(payload)) {
        normalized = normalizeOllamaModels(payload)
        lastErr = ''
      }
    }

    if (!normalized.length && lastErr) {
      ElMessage.error(lastErr || t('channel.ollama.fetchFailed'))
    }

    models.value = normalized
    // 默认选中所有模型（若之前没有选中项）
    if (!selected.value.length) {
      selected.value = normalized.map((m) => m.id)
    } else {
      const stillAvailable = selected.value.filter((id) =>
        normalized.some((m) => m.id === id)
      )
      selected.value = stillAvailable.length
        ? stillAvailable
        : normalized.map((m) => m.id)
    }
  } catch (err) {
    ElMessage.error((err as Error)?.message || t('channel.ollama.fetchFailed'))
    models.value = []
  } finally {
    isFetching.value = false
  }
}

function toggleSelected(modelId: string, checked: boolean): void {
  if (checked) {
    if (!selected.value.includes(modelId)) {
      selected.value = [...selected.value, modelId]
    }
  } else {
    selected.value = selected.value.filter((id) => id !== modelId)
  }
}

function selectAllFiltered(): void {
  const next = new Set(selected.value)
  filteredModels.value.forEach((m) => next.add(m.id))
  selected.value = Array.from(next)
}

function clearSelection(): void {
  selected.value = []
}

/** 应用选中模型到渠道（append 追加 / replace 替换） */
async function applySelection(mode: 'append' | 'replace'): Promise<void> {
  if (!props.channel) return
  if (!selected.value.length) {
    ElMessage.info(t('channel.ollama.noSelection'))
    return
  }

  const next =
    mode === 'replace'
      ? Array.from(new Set(selected.value))
      : Array.from(new Set([...existingModels.value, ...selected.value]))

  try {
    await updateChannel(props.channel.id, { models: next.join(',') })
    ElMessage.success(
      mode === 'replace'
        ? t('channel.ollama.replaceSuccess')
        : t('channel.ollama.appendSuccess')
    )
    emit('success')
  } catch (err) {
    ElMessage.error((err as Error)?.message || t('channel.ollama.applyFailed'))
  }
}

/** 流式拉取模型（Mock 环境降级） */
async function pullModel(): Promise<void> {
  if (!channelId.value) return
  if (!pullName.value.trim()) {
    ElMessage.error(t('channel.ollama.enterModelName'))
    return
  }

  if (!resolveOllamaBaseUrl(props.channel)) {
    ElMessage.error(t('channel.ollama.setBaseUrlFirst'))
    return
  }

  // Mock 环境降级
  if (import.meta.env.DEV && import.meta.env.VITE_CHANNEL_MOCK === 'true') {
    ElMessage.warning(t('channel.ollama.mockPullUnsupported'))
    return
  }

  pullAbortController?.abort()
  const controller = new AbortController()
  pullAbortController = controller

  isPulling.value = true
  pullProgress.value = { status: 'starting', completed: 0, total: 0 }

  try {
    const response = await fetch('/api/channel/ollama/pull/stream', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream'
      },
      body: JSON.stringify({
        channel_id: channelId.value,
        model_name: pullName.value.trim()
      }),
      signal: controller.signal
    })

    if (!response.ok || !response.body) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (!line.startsWith('data: ')) continue
        const eventData = line.slice(6)
        if (!eventData) continue

        if (eventData === '[DONE]') {
          finishPull()
          return
        }

        try {
          const data = JSON.parse(eventData) as PullProgress & {
            error?: string
            message?: string
          }
          if (data?.status) {
            pullProgress.value = data
          } else if (data?.error) {
            ElMessage.error(String(data.error))
            finishPull()
            return
          } else if (data?.message) {
            ElMessage.success(String(data.message))
            pullName.value = ''
            finishPull()
            await fetchOllamaModels()
            emit('success')
            return
          }
        } catch {
          // 忽略格式错误的事件
        }
      }
    }

    finishPull()
    await fetchOllamaModels()
    emit('success')
  } catch (err) {
    const isAbort =
      typeof err === 'object' &&
      err !== null &&
      'name' in err &&
      (err as { name?: unknown }).name === 'AbortError'
    if (!isAbort) {
      ElMessage.error(t('channel.ollama.pullFailed', { msg: (err as Error)?.message || '' }))
    }
    finishPull()
  }
}

function finishPull(): void {
  isPulling.value = false
  pullProgress.value = null
  pullAbortController = null
}

/** 删除模型 */
async function deleteModel(modelName: string): Promise<void> {
  if (!channelId.value) return
  try {
    isDeleting.value = true
    await deleteOllamaModel({
      channelId: Number(channelId.value),
      modelName: modelName
    })
    ElMessage.success(t('channel.ollama.deleteSuccess'))
    await fetchOllamaModels()
    emit('success')
    deleteOpen.value = false
    deleteTarget.value = null
  } catch (err) {
    ElMessage.error((err as Error)?.message || t('channel.ollama.deleteFailed'))
  } finally {
    isDeleting.value = false
  }
}

function close(): void {
  pullAbortController?.abort()
  pullAbortController = null
  emit('update:modelValue', false)
}

const pullPercent = computed(() => {
  const p = pullProgress.value
  if (
    typeof p?.completed === 'number' &&
    typeof p?.total === 'number' &&
    p.total > 0
  ) {
    return Math.min(100, Math.round((p.completed / p.total) * 100))
  }
  return 0
})
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    width="750px"
    :close-on-click-modal="false"
    append-to-body
    @update:model-value="(v: boolean) => !v && close()"
  >
    <template #header>
      <div>
        <h3 class="ollama__title">
          {{ t('channel.ollama.title') }}
        </h3>
        <p class="ollama__desc">
          {{ t('channel.ollama.manageFor') }}
          <strong>{{ channel?.name }}</strong>
        </p>
      </div>
    </template>

    <div class="ollama">
      <!-- 非 Ollama 渠道提示 -->
      <div
        v-if="!isOllamaChannel"
        class="ollama__not-ollama"
      >
        {{ t('channel.ollama.notOllamaChannel') }}
      </div>

      <template v-else>
        <!-- Pull 区域 -->
        <div class="ollama__pull-section">
          <div class="ollama__pull-input">
            <label class="ollama__pull-label">
              {{ t('channel.ollama.pullModel') }}
            </label>
            <div class="ollama__pull-row">
              <ElInput
                v-model="pullName"
                :placeholder="t('channel.ollama.pullPlaceholder')"
                :disabled="!channelId || isPulling"
              />
              <ElButton
                type="primary"
                :loading="isPulling"
                :disabled="!channelId"
                @click="pullModel"
              >
                <i
                  v-if="!isPulling"
                  class="i-ep-download mr-1"
                />
                {{ isPulling ? t('channel.ollama.pulling') : t('channel.ollama.pull') }}
              </ElButton>
            </div>
          </div>

          <!-- Pull 进度 -->
          <div
            v-if="pullProgress"
            class="ollama__pull-progress"
          >
            <div class="ollama__pull-status">
              {{ t('channel.ollama.status') }}: {{ String(pullProgress.status || '-') }}
            </div>
            <ElProgress
              :percentage="pullPercent"
              :stroke-width="8"
            />
          </div>

          <!-- 刷新按钮 -->
          <ElButton
            :loading="isFetching"
            :disabled="!channelId"
            @click="fetchOllamaModels"
          >
            <i
              v-if="!isFetching"
              class="i-ep-refresh mr-1"
            />
            {{ t('common.refresh') }}
          </ElButton>
        </div>

        <ElDivider />

        <!-- 模型列表 -->
        <div class="ollama__models-section">
          <div class="ollama__models-header">
            <div>
              <div class="ollama__models-title">
                {{ t('channel.ollama.localModels') }}
              </div>
              <div class="ollama__models-hint">
                {{ t('channel.ollama.selectHint') }}
              </div>
            </div>
            <ElInput
              v-model="search"
              :placeholder="t('channel.ollama.searchModels')"
              class="ollama__search"
              clearable
            >
              <template #prefix>
                <i class="i-ep-search" />
              </template>
            </ElInput>
          </div>

          <!-- 批量操作按钮 -->
          <div class="ollama__batch-actions">
            <ElButton
              size="small"
              @click="selectAllFiltered"
            >
              {{ t('channel.ollama.selectAllFiltered') }}
            </ElButton>
            <ElButton
              size="small"
              @click="clearSelection"
            >
              {{ t('channel.ollama.clearSelection') }}
            </ElButton>
            <ElButton
              type="primary"
              size="small"
              :disabled="!selected.length"
              @click="applySelection('append')"
            >
              {{ t('channel.ollama.appendToChannel') }}
            </ElButton>
            <ElButton
              size="small"
              :disabled="!selected.length"
              @click="applySelection('replace')"
            >
              {{ t('channel.ollama.replaceChannelModels') }}
            </ElButton>
          </div>

          <!-- 模型列表 -->
          <div class="ollama__model-list">
            <template v-if="filteredModels.length > 0">
              <div
                v-for="m in filteredModels"
                :key="m.id"
                class="ollama__model-item"
              >
                <div class="ollama__model-info">
                  <ElCheckbox
                    :model-value="selected.includes(m.id)"
                    @change="toggleSelected(m.id, !!$event)"
                  />
                  <div class="ollama__model-detail">
                    <div class="ollama__model-id mono">
                      {{ m.id }}
                    </div>
                    <div class="ollama__model-meta">
                      <span>{{ t('channel.ollama.size') }}: {{ formatBytes(m.size) }}</span>
                      <span
                        v-if="m.digest"
                        class="ollama__model-digest"
                      >
                        {{ t('channel.ollama.digest') }}: {{ m.digest }}
                      </span>
                    </div>
                  </div>
                </div>
                <ElButton
                  text
                  type="danger"
                  size="small"
                  :disabled="!channelId"
                  @click="deleteTarget = m.id; deleteOpen = true"
                >
                  <i class="i-ep-delete" />
                </ElButton>
              </div>
            </template>
            <div
              v-else
              class="ollama__model-empty"
            >
              {{ t('channel.ollama.noModelsFound') }}
            </div>
          </div>
        </div>
      </template>
    </div>

    <template #footer>
      <ElButton @click="close">
        {{ t('common.close') }}
      </ElButton>
    </template>
  </ElDialog>

  <!-- 删除确认 -->
  <ElDialog
    v-model="deleteOpen"
    :title="t('channel.ollama.confirmDelete')"
    width="420px"
    append-to-body
    @update:model-value="(v: boolean) => !v && (deleteTarget = null)"
  >
    <p>{{ t('channel.ollama.deleteHint', { name: deleteTarget || '' }) }}</p>
    <template #footer>
      <ElButton
        :disabled="isDeleting"
        @click="deleteOpen = false"
      >
        {{ t('common.cancel') }}
      </ElButton>
      <ElButton
        type="danger"
        :loading="isDeleting"
        :disabled="!deleteTarget"
        @click="deleteTarget && deleteModel(deleteTarget)"
      >
        {{ t('common.delete') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped lang="scss">
.ollama {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-3);

  &__title {
    margin: 0;
    font-size: var(--ys-font-size-lg);
    font-weight: 600;
  }

  &__desc {
    margin: var(--ys-spacing-1) 0 0;
    font-size: var(--ys-font-size-base);
    color: var(--el-text-color-secondary);
  }

  &__not-ollama {
    padding: var(--ys-spacing-8);
    color: var(--el-text-color-secondary);
    text-align: center;
  }

  &__pull-section {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
  }

  &__pull-label {
    font-size: var(--ys-font-size-base);
    font-weight: 500;
  }

  &__pull-row {
    display: flex;
    gap: var(--ys-spacing-2);
  }

  &__pull-progress {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  &__pull-status {
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-secondary);
  }

  &__models-section {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
  }

  &__models-header {
    display: flex;
    gap: var(--ys-spacing-3);
    align-items: flex-start;
    justify-content: space-between;
  }

  &__models-title {
    font-size: var(--ys-font-size-base);
    font-weight: 500;
  }

  &__models-hint {
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-secondary);
  }

  &__search {
    max-width: 280px;
  }

  &__batch-actions {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-2);
  }

  &__model-list {
    max-height: 420px;
    overflow-y: auto;
    border: 1px solid var(--el-border-color);
    border-radius: var(--ys-radius-sm);
  }

  &__model-item {
    display: flex;
    gap: var(--ys-spacing-3);
    align-items: center;
    justify-content: space-between;
    padding: 10px;
    border-bottom: 1px solid var(--el-border-color-lighter);

    &:last-child {
      border-bottom: none;
    }
  }

  &__model-info {
    display: flex;
    gap: 10px;
    align-items: flex-start;
    min-width: 0;
  }

  &__model-detail {
    min-width: 0;
  }

  &__model-id {
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: var(--ys-font-size-sm);
    white-space: nowrap;
  }

  &__model-meta {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-3);
    margin-top: 2px;
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-secondary);
  }

  &__model-digest {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__model-empty {
    padding: var(--ys-spacing-6);
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-secondary);
    text-align: center;
  }
}

.mono {
  font-family: var(--el-font-family-mono, monospace);
}
</style>
