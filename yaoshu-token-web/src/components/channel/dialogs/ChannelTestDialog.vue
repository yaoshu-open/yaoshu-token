<script setup lang="ts">
/**
 * 渠道连通性测试对话框。
 * 展示渠道所有模型，支持单模型/批量测试，显示响应时间和错误信息。
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag
} from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { testChannel } from '@/api/channel'
import type { Channel, ChannelTestResponse } from '@/api/channel/types'
import { formatResponseTime } from '@/lib/channel/channel-utils'
import type { BusinessError } from '@/utils/request'

type TestStatus = 'idle' | 'testing' | 'success' | 'error'

interface TestResult {
  status: TestStatus
  responseTime?: number
  error?: string
  /** 后端业务错误码（BusinessError.code，仅 status==='error' 时存在） */
  code?: number
}

const props = defineProps<{
  modelValue: boolean
  channel: Channel | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

// ============================================================================
// 端点类型常量
// ============================================================================

const ENDPOINT_TYPE_OPTIONS = [
  { value: 'auto', label: 'channel.dialog.test.endpointAuto' },
  { value: 'openai', label: 'channel.dialog.test.endpointOpenai' },
  { value: 'openai-response', label: 'channel.dialog.test.endpointOpenaiResponse' },
  { value: 'anthropic', label: 'channel.dialog.test.endpointAnthropic' },
  { value: 'gemini', label: 'channel.dialog.test.endpointGemini' },
  { value: 'jina-rerank', label: 'channel.dialog.test.endpointJinaRerank' },
  { value: 'image-generation', label: 'channel.dialog.test.endpointImageGen' },
  { value: 'embeddings', label: 'channel.dialog.test.endpointEmbeddings' }
] as const

const STREAM_INCOMPATIBLE_ENDPOINTS = new Set([
  'embeddings',
  'image-generation',
  'jina-rerank'
])

const FAILURE_SUMMARY_MAX_LENGTH = 96
// channel test 失败错误码 → i18n 文案映射（后端 ResultCode 601-607，见契约_渠道管理.md）
// code===600 或未命中时走通用 msg 文本分支
const CHANNEL_TEST_ERROR_CODE_MAP: Record<number, string> = {
  601: 'channel.dialog.test.modelPriceError',
  602: 'channel.dialog.test.invalidApiType',
  603: 'channel.dialog.test.invalidChannelKey',
  604: 'channel.dialog.test.badResponseStatus',
  605: 'channel.dialog.test.badResponseBody',
  606: 'channel.dialog.test.emptyResponse',
  607: 'channel.dialog.test.modelNotFound'
}

// ============================================================================
// 内部状态
// ============================================================================

const endpointType = ref('auto')
const isStreamTest = ref(false)
const searchTerm = ref('')
const testResults = ref<Record<string, TestResult>>({})
const selectedModels = ref<string[]>([])
const testingModels = ref<Set<string>>(new Set())
const isBatchTesting = ref(false)
const expandedErrorModel = ref<string | null>(null)
const tableRef = ref<{ clearSelection: () => void }>()

// ============================================================================
// 计算属性
// ============================================================================

const streamDisabled = computed(() =>
  STREAM_INCOMPATIBLE_ENDPOINTS.has(endpointType.value)
)

const models = computed(() => {
  if (!props.channel?.models) return []
  return props.channel.models
    .split(',')
    .map((m) => m.trim())
    .filter(Boolean)
})

const filteredModels = computed(() => {
  if (!searchTerm.value.trim()) return models.value
  const kw = searchTerm.value.toLowerCase()
  return models.value.filter((m) => m.toLowerCase().includes(kw))
})

/** 表格数据（预计算，避免内联 .map() 每次响应式触发创建新对象致选择状态丢失） */
const tableData = computed(() => filteredModels.value.map((m) => ({ model: m })))

const defaultTestModel = computed(() =>
  props.channel?.testModel?.trim() || ''
)

const successCount = computed(
  () => Object.values(testResults.value).filter((r) => r.status === 'success').length
)

const failedCount = computed(
  () => Object.values(testResults.value).filter((r) => r.status === 'error').length
)

// ============================================================================
// 重置 & 监听
// ============================================================================

function resetState(): void {
  endpointType.value = 'auto'
  isStreamTest.value = false
  searchTerm.value = ''
  testResults.value = {}
  selectedModels.value = []
  testingModels.value = new Set()
  isBatchTesting.value = false
  expandedErrorModel.value = null
}

watch(visible, (v) => {
  if (v) resetState()
})

watch(streamDisabled, (disabled) => {
  if (disabled) isStreamTest.value = false
})

// ============================================================================
// 测试逻辑
// ============================================================================

function markModelTesting(model: string, isTesting: boolean): void {
  const next = new Set(testingModels.value)
  if (isTesting) next.add(model)
  else next.delete(model)
  testingModels.value = next
}

function updateTestResult(model: string, result: TestResult): void {
  testResults.value = { ...testResults.value, [model]: result }
}

function getFailureSummary(error: string | undefined, code?: number): string {
  // 优先按后端 ResultCode 返回专属分类文案（601-607）
  if (code !== undefined && CHANNEL_TEST_ERROR_CODE_MAP[code]) {
    return t(CHANNEL_TEST_ERROR_CODE_MAP[code])
  }
  const rawError = error?.trim()
  if (!rawError) return t('channel.dialog.test.testFailed')

  const firstLine = rawError.split(/\r?\n/).map((l) => l.trim()).find(Boolean) ?? rawError
  const normalized = firstLine.replace(/\s+/g, ' ').trim()
  return normalized.length > FAILURE_SUMMARY_MAX_LENGTH
    ? `${normalized.slice(0, FAILURE_SUMMARY_MAX_LENGTH).trimEnd()}...`
    : normalized
}

async function testSingleModel(model: string, silent = false): Promise<TestResult | undefined> {
  if (!props.channel) return

  markModelTesting(model, true)
  updateTestResult(model, { status: 'testing' })

  let finalResult: TestResult | undefined

  try {
    const res: ChannelTestResponse = await testChannel(props.channel.id, {
      model,
      endpoint_type: endpointType.value === 'auto' ? undefined : endpointType.value,
      stream: isStreamTest.value || undefined
    })
    // 请求未抛异常即为成功
    finalResult = {
      status: 'success',
      responseTime: res.time
    }
    updateTestResult(model, finalResult)

    if (!silent) {
      const time = res.time
      ElMessage.success(time !== undefined
        ? t('channel.dialog.test.singleSuccess', { time: formatResponseTime(time) })
        : t('channel.dialog.test.singleSuccessNoTime')
      )
    }
  } catch (e) {
    const errCode = (e as BusinessError)?.code
    finalResult = {
      status: 'error',
      error: (e as Error)?.message || t('channel.dialog.test.testFailed'),
      code: errCode
    }
    updateTestResult(model, finalResult)
    if (!silent) {
      ElMessage.error(getFailureSummary(finalResult.error, errCode))
    }
  } finally {
    markModelTesting(model, false)
  }
  return finalResult
}

/** 批量测试指定模型列表，结果更新到表格状态列 */
async function batchTestModels(modelsToTest: string[]): Promise<void> {
  if (!modelsToTest.length) return

  isBatchTesting.value = true
  // 立即标记所有模型为 testing 状态，让用户看到即时反馈
  for (const m of modelsToTest) {
    updateTestResult(m, { status: 'testing' })
  }

  try {
    // 逐个测试（非并发），避免大量模型同时请求导致浏览器连接排队和后端过载
    let success = 0
    let failed = 0
    for (const m of modelsToTest) {
      const result = await testSingleModel(m, true)
      if (result?.status === 'success') success++
      else failed++
    }

    if (failed > 0) {
      ElMessage.warning(
        t('channel.dialog.test.batchResult', { success, failed })
      )
    } else {
      ElMessage.success(
        t('channel.dialog.test.batchAllSuccess', { count: success })
      )
    }
  } finally {
    isBatchTesting.value = false
    tableRef.value?.clearSelection()
  }
}

/** 批量测试选中的模型（由批量操作栏按钮触发） */
async function handleBatchTest(): Promise<void> {
  // 复制一份，避免 clearSelection 清空 selectedModels 影响遍历
  const modelsToTest = [...selectedModels.value]
  await batchTestModels(modelsToTest)
}

/** 测试当前过滤后的全部模型 */
async function handleTestAll(): Promise<void> {
  if (!filteredModels.value.length || isBatchTesting.value) return
  await batchTestModels(filteredModels.value.slice())
}

function toggleErrorExpand(model: string): void {
  expandedErrorModel.value = expandedErrorModel.value === model ? null : model
}

function handleClose(): void {
  resetState()
  visible.value = false
}

// ============================================================================
// 选择处理
// ============================================================================

function handleSelectionChange(selection: { model: string }[]): void {
  selectedModels.value = selection.map((s) => s.model)
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="t('channel.dialog.test.title')"
    width="800px"
    append-to-body
    @close="handleClose"
  >
    <template v-if="channel">
      <p class="test-dialog__desc">
        {{ t('channel.dialog.test.description') }}
        <strong>{{ channel.name }}</strong>
      </p>

      <!-- 端点类型 + 流式开关 -->
      <div class="test-dialog__config">
        <div class="test-dialog__config-item">
          <label class="test-dialog__label">{{ t('channel.dialog.test.endpointType') }}</label>
          <el-select
            v-model="endpointType"
            size="default"
            style="width: 100%"
          >
            <el-option
              v-for="opt in ENDPOINT_TYPE_OPTIONS"
              :key="opt.value"
              :value="opt.value"
              :label="t(opt.label)"
            />
          </el-select>
          <p class="test-dialog__hint">
            {{ t('channel.dialog.test.endpointHint') }}
          </p>
        </div>

        <div class="test-dialog__config-item">
          <label class="test-dialog__label">{{ t('channel.dialog.test.streamMode') }}</label>
          <div class="test-dialog__switch-row">
            <el-switch
              v-model="isStreamTest"
              :disabled="streamDisabled"
            />
            <span class="test-dialog__switch-label">
              {{ isStreamTest ? t('common.enabled') : t('common.disabled') }}
            </span>
          </div>
          <p class="test-dialog__hint">
            {{ t('channel.dialog.test.streamHint') }}
          </p>
        </div>
      </div>

      <!-- 模型列表 -->
      <div class="test-dialog__models">
        <div class="test-dialog__models-header">
          <div>
            <span class="test-dialog__models-title">{{ t('channel.dialog.test.models') }}</span>
            <span class="test-dialog__models-count">({{ models.length }})</span>
          </div>
          <div class="test-dialog__models-actions">
            <el-button
              size="small"
              :loading="isBatchTesting"
              :disabled="filteredModels.length === 0"
              @click="handleTestAll"
            >
              {{ t('channel.dialog.test.testAll') }}
            </el-button>
            <el-input
              v-model="searchTerm"
              :placeholder="t('channel.dialog.test.searchPlaceholder')"
              clearable
              size="small"
              style="width: 220px"
            />
          </div>
        </div>

        <!-- 批量操作栏 -->
        <div
          v-if="selectedModels.length > 0"
          class="test-dialog__batch-bar"
        >
          <span>
            {{ t('channel.dialog.test.selectedCount', { count: selectedModels.length }) }}
          </span>
          <el-button
            type="primary"
            size="small"
            :loading="isBatchTesting"
            @click="handleBatchTest"
          >
            {{ t('channel.dialog.test.batchTest') }}
          </el-button>
        </div>

        <el-table
          ref="tableRef"
          :data="tableData"
          :row-key="(row: { model: string }) => row.model"
          max-height="400"
          size="small"
          @selection-change="handleSelectionChange"
        >
          <el-table-column
            type="selection"
            width="40"
          />

          <el-table-column
            :label="t('channel.dialog.test.modelColumn')"
            min-width="200"
          >
            <template #default="{ row }">
              <div class="test-dialog__model-cell">
                <span
                  class="test-dialog__model-name"
                  :title="row.model"
                >
                  {{ row.model }}
                </span>
                <el-tag
                  v-if="row.model === defaultTestModel"
                  size="small"
                  type="info"
                >
                  {{ t('channel.dialog.test.default') }}
                </el-tag>
              </div>
            </template>
          </el-table-column>

          <el-table-column
            :label="t('channel.dialog.test.statusColumn')"
            min-width="200"
          >
            <template #default="{ row }">
              <div
                v-if="!testResults[row.model] || testResults[row.model].status === 'idle'"
                class="test-dialog__status-idle"
              >
                <el-tag
                  size="small"
                  type="info"
                >
                  {{ t('channel.dialog.test.notTested') }}
                </el-tag>
              </div>

              <div
                v-else-if="testResults[row.model].status === 'testing'"
                class="test-dialog__status-testing"
              >
                <el-icon class="is-loading">
                  <Loading />
                </el-icon>
                <span>{{ t('channel.dialog.test.testing') }}</span>
              </div>

              <div
                v-else-if="testResults[row.model].status === 'success'"
                class="test-dialog__status-success"
              >
                <el-tag
                  size="small"
                  type="success"
                >
                  {{ t('channel.dialog.test.success') }}
                </el-tag>
                <span
                  v-if="testResults[row.model].responseTime !== undefined"
                  class="test-dialog__response-time"
                >
                  {{ formatResponseTime(testResults[row.model].responseTime!) }}
                </span>
              </div>

              <div
                v-else
                class="test-dialog__status-error"
              >
                <el-tag
                  size="small"
                  type="danger"
                >
                  {{ t('channel.dialog.test.failed') }}
                </el-tag>
                <span class="test-dialog__error-summary">
                  {{ getFailureSummary(testResults[row.model].error, testResults[row.model].code) }}
                </span>
                <el-button
                  v-if="testResults[row.model].error && testResults[row.model].error!.length > 96"
                  link
                  type="primary"
                  size="small"
                  @click="toggleErrorExpand(row.model)"
                >
                  {{ expandedErrorModel === row.model
                    ? t('channel.dialog.test.hideDetails')
                    : t('channel.dialog.test.showDetails')
                  }}
                </el-button>
                <div
                  v-if="expandedErrorModel === row.model"
                  class="test-dialog__error-details"
                >
                  {{ testResults[row.model].error }}
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column
            :label="t('common.action')"
            width="100"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                size="small"
                :loading="testingModels.has(row.model)"
                :disabled="isBatchTesting"
                @click="testSingleModel(row.model)"
              >
                {{ t('channel.dialog.test.test') }}
              </el-button>
            </template>
          </el-table-column>

          <template #empty>
            <el-empty
              :description="models.length
                ? t('channel.dialog.test.noMatch')
                : t('channel.dialog.test.noModels')
              "
              :image-size="60"
            />
          </template>
        </el-table>

        <!-- 统计摘要 -->
        <div
          v-if="successCount > 0 || failedCount > 0"
          class="test-dialog__summary"
        >
          <el-tag
            type="success"
            size="small"
          >
            {{ t('channel.dialog.test.summarySuccess', { count: successCount }) }}
          </el-tag>
          <el-tag
            v-if="failedCount > 0"
            type="danger"
            size="small"
          >
            {{ t('channel.dialog.test.summaryFailed', { count: failedCount }) }}
          </el-tag>
        </div>
      </div>
    </template>

    <template #footer>
      <el-button @click="handleClose">
        {{ t('common.close') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.test-dialog__desc {
  margin: 0 0 var(--ys-spacing-4);
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-secondary);
}

.test-dialog__desc strong {
  margin-left: var(--ys-spacing-1);
  color: var(--el-text-color-primary);
}

.test-dialog__config {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--ys-spacing-4);
  margin-bottom: var(--ys-spacing-5);
}

.test-dialog__config-item {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-1);
}

.test-dialog__label {
  font-size: var(--ys-font-size-sm);
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.test-dialog__hint {
  margin: 0;
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}

.test-dialog__switch-row {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
  height: 32px;
}

.test-dialog__switch-label {
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-regular);
}

.test-dialog__models {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-3);
}

.test-dialog__models-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.test-dialog__models-actions {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
}

.test-dialog__models-title {
  font-size: var(--ys-font-size-base);
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.test-dialog__models-count {
  margin-left: var(--ys-spacing-1);
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}

.test-dialog__batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--ys-spacing-2) var(--ys-spacing-3);
  font-size: var(--ys-font-size-sm);
  background-color: var(--el-fill-color-light);
  border-radius: var(--ys-radius-sm);
}

.test-dialog__model-cell {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
}

.test-dialog__model-name {
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 500;
  white-space: nowrap;
}

.test-dialog__status-idle,
.test-dialog__status-testing,
.test-dialog__status-success,
.test-dialog__status-error {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  font-size: var(--ys-font-size-xs);
}

.test-dialog__status-testing {
  color: var(--el-text-color-secondary);
}

.test-dialog__response-time {
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}

.test-dialog__error-summary {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--ys-font-size-xs);
  color: var(--el-color-danger);
  white-space: nowrap;
}

.test-dialog__error-details {
  width: 100%;
  padding: var(--ys-spacing-2);
  margin-top: 4px;
  font-family: monospace;
  font-size: 11px;
  color: var(--el-color-danger);
  word-break: break-all;
  white-space: pre-wrap;
  background-color: var(--el-color-danger-light-9);
  border-radius: var(--ys-radius-sm);
}

.test-dialog__summary {
  display: flex;
  gap: var(--ys-spacing-2);
  padding: var(--ys-spacing-2) 0;
}
</style>
