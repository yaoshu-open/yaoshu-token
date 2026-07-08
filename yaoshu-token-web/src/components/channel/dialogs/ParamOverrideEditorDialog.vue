<script setup lang="ts">
/**
 * 参数覆写可视化编辑器对话框。
 * 支持 operations 格式的参数覆写规则编辑：添加/删除/排序操作、条件编辑、模板预设。
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ElAlert,
  ElButton,
  ElCollapse,
  ElCollapseItem,
  ElDialog,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTag
} from 'element-plus'
import { Plus, Delete, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import {
  CONDITION_MODE_OPTIONS,
  MODE_DESCRIPTIONS,
  MODE_META,
  OPERATION_MODE_OPTIONS,
  TEMPLATE_PRESETS,
  buildOperationsJson,
  createDefaultCondition,
  createDefaultOperation,
  getOperationSummary,
  isOperationBlank,
  parseParamOverride,
  validateOperations,
  type ParamOverrideOperation
} from '@/lib/channel/param-override-config'

const props = defineProps<{
  modelValue: boolean
  value: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'save', value: string): void
}>()

const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

// ============================================================================
// 状态
// ============================================================================

const operations = ref<ParamOverrideOperation[]>([createDefaultOperation()])
const isLegacy = ref(false)
const legacyValue = ref('')
const activeOperationIds = ref<string[]>([])

// ============================================================================
// 初始化
// ============================================================================

watch(visible, (v) => {
  if (v) {
    const parsed = parseParamOverride(props.value)
    operations.value = parsed.operations
    isLegacy.value = parsed.isLegacy
    legacyValue.value = parsed.legacyValue
    activeOperationIds.value = parsed.operations.length > 0 ? [parsed.operations[0].id] : []
  }
})

// ============================================================================
// 模板预设
// ============================================================================

const templateOptions = computed(() =>
  Object.entries(TEMPLATE_PRESETS).map(([key, preset]) => ({
    value: key,
    label: preset.label
  }))
)

function applyTemplate(templateKey: string): void {
  const preset = TEMPLATE_PRESETS[templateKey]
  if (!preset?.payload) return

  const parsed = parseParamOverride(JSON.stringify(preset.payload))
  operations.value = parsed.operations
  isLegacy.value = false
  if (operations.value.length > 0) {
    activeOperationIds.value = [operations.value[0].id]
  }
}

// ============================================================================
// 操作 CRUD
// ============================================================================

function addOperation(): void {
  const op = createDefaultOperation()
  operations.value.push(op)
  activeOperationIds.value = [...activeOperationIds.value, op.id]
}

function deleteOperation(id: string): void {
  const idx = operations.value.findIndex((o) => o.id === id)
  if (idx < 0) return
  operations.value.splice(idx, 1)
  if (operations.value.length === 0) {
    operations.value.push(createDefaultOperation())
  }
  activeOperationIds.value = operations.value.map((o) => o.id)
}

function moveOperation(id: string, direction: 'up' | 'down'): void {
  const idx = operations.value.findIndex((o) => o.id === id)
  if (idx < 0) return
  const targetIdx = direction === 'up' ? idx - 1 : idx + 1
  if (targetIdx < 0 || targetIdx >= operations.value.length) return
  const tmp = operations.value[idx]
  operations.value[idx] = operations.value[targetIdx]
  operations.value[targetIdx] = tmp
}

// ============================================================================
// 条件 CRUD
// ============================================================================

function addCondition(opId: string): void {
  const op = operations.value.find((o) => o.id === opId)
  if (!op) return
  op.conditions.push(createDefaultCondition())
}

function deleteCondition(opId: string, condId: string): void {
  const op = operations.value.find((o) => o.id === opId)
  if (!op) return
  const idx = op.conditions.findIndex((c) => c.id === condId)
  if (idx >= 0) op.conditions.splice(idx, 1)
}

// ============================================================================
// 保存
// ============================================================================

function handleSave(): void {
  try {
    if (isLegacy.value) {
      const trimmed = legacyValue.value.trim()
      if (trimmed) {
        JSON.parse(trimmed) // 校验
      }
      emit('save', trimmed ? JSON.stringify(JSON.parse(trimmed), null, 2) : '')
    } else {
      const error = validateOperations(operations.value)
      if (error) {
        ElMessage.error(error)
        return
      }
      const json = buildOperationsJson(operations.value)
      emit('save', json)
    }
    visible.value = false
  } catch (e) {
    ElMessage.error((e as Error)?.message || t('channel.dialog.paramOverride.saveFailed'))
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="t('channel.dialog.paramOverride.title')"
    width="800px"
    append-to-body
    class="param-override-dialog"
  >
    <p class="param-override__desc">
      {{ t('channel.dialog.paramOverride.description') }}
    </p>

    <!-- Legacy 模式提示 -->
    <el-alert
      v-if="isLegacy"
      :title="t('channel.dialog.paramOverride.legacyHint')"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
    >
      <el-input
        v-model="legacyValue"
        type="textarea"
        :autosize="{ minRows: 4, maxRows: 12 }"
        style="margin-top: 8px"
      />
    </el-alert>

    <!-- 可视化编辑 -->
    <div v-else>
      <!-- 工具栏 -->
      <div class="param-override__toolbar">
        <el-select
          :placeholder="t('channel.dialog.paramOverride.applyTemplate')"
          size="small"
          style="width: 280px"
          @change="applyTemplate"
        >
          <el-option
            v-for="opt in templateOptions"
            :key="opt.value"
            :value="opt.value"
            :label="opt.label"
          />
        </el-select>
        <el-button
          type="primary"
          size="small"
          :icon="Plus"
          @click="addOperation"
        >
          {{ t('channel.dialog.paramOverride.addOperation') }}
        </el-button>
      </div>

      <!-- 操作列表 -->
      <el-collapse
        v-model="activeOperationIds"
        class="param-override__operations"
      >
        <el-collapse-item
          v-for="(op, idx) in operations"
          :key="op.id"
          :name="op.id"
        >
          <template #title>
            <div class="param-override__op-header">
              <span class="param-override__op-summary">
                {{ getOperationSummary(op, idx) }}
              </span>
              <el-tag
                v-if="isOperationBlank(op)"
                size="small"
                type="info"
              >
                {{ t('channel.dialog.paramOverride.empty') }}
              </el-tag>
            </div>
          </template>

          <div class="param-override__op-body">
            <!-- 操作基础字段 -->
            <div class="param-override__op-row">
              <el-form-item :label="t('channel.dialog.paramOverride.descriptionLabel')">
                <el-input
                  v-model="op.description"
                  :placeholder="t('channel.dialog.paramOverride.descriptionPlaceholder')"
                  size="small"
                />
              </el-form-item>
            </div>

            <div class="param-override__op-row param-override__op-row--grid">
              <el-form-item :label="t('channel.dialog.paramOverride.mode')">
                <el-select
                  v-model="op.mode"
                  size="small"
                  style="width: 100%"
                >
                  <el-option
                    v-for="opt in OPERATION_MODE_OPTIONS"
                    :key="opt.value"
                    :value="opt.value"
                    :label="opt.label"
                  />
                </el-select>
                <p class="param-override__field-hint">
                  {{ MODE_DESCRIPTIONS[op.mode] || '' }}
                </p>
              </el-form-item>

              <el-form-item
                v-if="MODE_META[op.mode]?.path || MODE_META[op.mode]?.pathOptional"
                :label="t('channel.dialog.paramOverride.targetPath')"
              >
                <el-input
                  v-model="op.path"
                  placeholder="temperature"
                  size="small"
                />
              </el-form-item>
            </div>

            <!-- from / to -->
            <div
              v-if="MODE_META[op.mode]?.from || MODE_META[op.mode]?.to"
              class="param-override__op-row param-override__op-row--grid"
            >
              <el-form-item
                v-if="MODE_META[op.mode]?.from"
                :label="t('channel.dialog.paramOverride.sourceField')"
              >
                <el-input
                  v-model="op.from"
                  placeholder="model"
                  size="small"
                />
              </el-form-item>

              <el-form-item
                v-if="MODE_META[op.mode]?.to"
                :label="t('channel.dialog.paramOverride.targetField')"
              >
                <el-input
                  v-model="op.to"
                  placeholder="original_model"
                  size="small"
                />
              </el-form-item>
            </div>

            <!-- value -->
            <div
              v-if="MODE_META[op.mode]?.value"
              class="param-override__op-row"
            >
              <el-form-item :label="t('channel.dialog.paramOverride.value')">
                <el-input
                  v-model="op.value_text"
                  type="textarea"
                  :autosize="{ minRows: 1, maxRows: 6 }"
                  placeholder="0.7"
                  size="small"
                />
                <p class="param-override__field-hint">
                  {{ t('channel.dialog.paramOverride.valueHint') }}
                </p>
              </el-form-item>
            </div>

            <!-- keep_origin -->
            <div
              v-if="MODE_META[op.mode]?.keepOrigin"
              class="param-override__op-row"
            >
              <el-switch v-model="op.keep_origin" />
              <span class="param-override__switch-label">
                {{ t('channel.dialog.paramOverride.keepOrigin') }}
              </span>
            </div>

            <!-- 条件 -->
            <div class="param-override__conditions">
              <div class="param-override__conditions-header">
                <span class="param-override__conditions-title">
                  {{ t('channel.dialog.paramOverride.conditions') }}
                </span>
                <el-button
                  link
                  type="primary"
                  size="small"
                  :icon="Plus"
                  @click="addCondition(op.id)"
                >
                  {{ t('channel.dialog.paramOverride.addCondition') }}
                </el-button>
              </div>

              <div
                v-for="cond in op.conditions"
                :key="cond.id"
                class="param-override__condition-row"
              >
                <el-input
                  v-model="cond.path"
                  :placeholder="t('channel.dialog.paramOverride.condPathPlaceholder')"
                  size="small"
                  style="flex: 1"
                />
                <el-select
                  v-model="cond.mode"
                  size="small"
                  style="width: 140px"
                >
                  <el-option
                    v-for="opt in CONDITION_MODE_OPTIONS"
                    :key="opt.value"
                    :value="opt.value"
                    :label="opt.label"
                  />
                </el-select>
                <el-input
                  v-model="cond.value_text"
                  :placeholder="t('channel.dialog.paramOverride.condValuePlaceholder')"
                  size="small"
                  style="flex: 1"
                />
                <el-button
                  type="danger"
                  size="small"
                  :icon="Delete"
                  circle
                  @click="deleteCondition(op.id, cond.id)"
                />
              </div>

              <!-- 逻辑选择 -->
              <div
                v-if="op.conditions.length > 1"
                class="param-override__logic-row"
              >
                <span>{{ t('channel.dialog.paramOverride.logic') }}:</span>
                <el-select
                  v-model="op.logic"
                  size="small"
                  style="width: 100px"
                >
                  <el-option
                    value="AND"
                    label="AND (all match)"
                  />
                  <el-option
                    value="OR"
                    label="OR (any match)"
                  />
                </el-select>
              </div>
            </div>

            <!-- 排序 + 删除按钮 -->
            <div class="param-override__op-actions">
              <el-button
                size="small"
                :icon="ArrowUp"
                :disabled="idx === 0"
                @click="moveOperation(op.id, 'up')"
              >
                {{ t('channel.dialog.paramOverride.moveUp') }}
              </el-button>
              <el-button
                size="small"
                :icon="ArrowDown"
                :disabled="idx === operations.length - 1"
                @click="moveOperation(op.id, 'down')"
              >
                {{ t('channel.dialog.paramOverride.moveDown') }}
              </el-button>
              <el-button
                type="danger"
                size="small"
                :icon="Delete"
                @click="deleteOperation(op.id)"
              >
                {{ t('channel.dialog.paramOverride.deleteOp') }}
              </el-button>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>

    <template #footer>
      <el-button @click="visible = false">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        @click="handleSave"
      >
        {{ t('common.save') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.param-override__desc {
  margin: 0 0 var(--ys-spacing-4);
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-secondary);
}

.param-override__toolbar {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
  margin-bottom: 12px;
}

.param-override__operations {
  max-height: 500px;
  overflow-y: auto;
}

.param-override__op-header {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
  width: 100%;
}

.param-override__op-summary {
  font-size: var(--ys-font-size-sm);
  font-weight: 500;
}

.param-override__op-body {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-3);
  padding: 0 var(--ys-spacing-1);
}

.param-override__op-row {
  display: flex;
  flex-direction: column;
}

.param-override__op-row--grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--ys-spacing-3);
}

.param-override__field-hint {
  margin: 2px 0 0;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.param-override__switch-label {
  margin-left: 8px;
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-regular);
}

.param-override__conditions {
  padding: var(--ys-spacing-3);
  background-color: var(--el-fill-color-lighter);
  border-radius: var(--ys-radius-base);
}

.param-override__conditions-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.param-override__conditions-title {
  font-size: var(--ys-font-size-sm);
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.param-override__condition-row {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 6px;
}

.param-override__logic-row {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
  margin-top: 8px;
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}

.param-override__op-actions {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
  padding-top: 4px;
}
</style>
