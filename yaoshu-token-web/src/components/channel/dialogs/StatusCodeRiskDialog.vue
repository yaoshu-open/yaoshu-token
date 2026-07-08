<script setup lang="ts">
/**
 * 高危状态码重试确认守卫对话框。
 * 用户必须勾选全部风险确认项 + 输入精确确认文字才能通过。
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElButton, ElCheckbox, ElDialog, ElInput } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'

const props = defineProps<{
  modelValue: boolean
  detailItems: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm'): void
}>()

const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const CHECKLIST_KEYS = [
  'channel.dialog.risk.check1',
  'channel.dialog.risk.check2',
  'channel.dialog.risk.check3',
  'channel.dialog.risk.check4'
] as const

const checkedItems = ref<Set<number>>(new Set())
const confirmText = ref('')

const requiredText = computed(() => t('channel.dialog.risk.confirmText'))
const allChecked = computed(() => checkedItems.value.size === CHECKLIST_KEYS.length)
const textMatches = computed(() => confirmText.value.trim() === requiredText.value.trim())
const canConfirm = computed(() => allChecked.value && textMatches.value)

watch(visible, (v) => {
  if (v) {
    checkedItems.value = new Set()
    confirmText.value = ''
  }
})

function toggleCheck(idx: number): void {
  const next = new Set(checkedItems.value)
  if (next.has(idx)) next.delete(idx)
  else next.add(idx)
  checkedItems.value = next
}

function handleConfirm(): void {
  if (!canConfirm.value) return
  checkedItems.value = new Set()
  confirmText.value = ''
  emit('confirm')
  visible.value = false
}

function handleCancel(): void {
  checkedItems.value = new Set()
  confirmText.value = ''
  visible.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="480px"
    append-to-body
  >
    <template #header>
      <div class="risk-dialog__header">
        <el-icon
          class="risk-dialog__icon"
          :size="20"
        >
          <WarningFilled />
        </el-icon>
        <span class="risk-dialog__title">{{ t('channel.dialog.risk.title') }}</span>
      </div>
    </template>

    <p class="risk-dialog__disclaimer">
      {{ t('channel.dialog.risk.disclaimer') }}
    </p>

    <!-- 检测到的高危规则 -->
    <div
      v-if="detailItems.length > 0"
      class="risk-dialog__detected"
    >
      <p class="risk-dialog__detected-title">
        {{ t('channel.dialog.risk.detectedRules') }}
      </p>
      <ul class="risk-dialog__detected-list">
        <li
          v-for="item in detailItems"
          :key="item"
          class="risk-dialog__detected-item"
        >
          {{ item }}
        </li>
      </ul>
    </div>

    <!-- 确认清单 -->
    <div class="risk-dialog__checks">
      <div
        v-for="(key, idx) in CHECKLIST_KEYS"
        :key="key"
        class="risk-dialog__check-item"
      >
        <el-checkbox
          :model-value="checkedItems.has(idx)"
          @change="toggleCheck(idx)"
        >
          {{ t(key) }}
        </el-checkbox>
      </div>
    </div>

    <!-- 文字确认 -->
    <div class="risk-dialog__confirm-text">
      <label class="risk-dialog__confirm-label">
        {{ t('channel.dialog.risk.actionConfirm') }}:
        <code class="risk-dialog__confirm-code">{{ requiredText }}</code>
      </label>
      <el-input
        v-model="confirmText"
        :placeholder="t('channel.dialog.risk.inputPlaceholder')"
      />
      <p
        v-if="confirmText && !textMatches"
        class="risk-dialog__mismatch"
      >
        {{ t('channel.dialog.risk.inputMismatch') }}
      </p>
    </div>

    <template #footer>
      <el-button @click="handleCancel">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="danger"
        :disabled="!canConfirm"
        @click="handleConfirm"
      >
        {{ t('channel.dialog.risk.confirmButton') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.risk-dialog__header {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
}

.risk-dialog__icon {
  color: var(--el-color-danger);
}

.risk-dialog__title {
  font-size: var(--ys-font-size-lg);
  font-weight: 600;
  color: var(--el-color-danger);
}

.risk-dialog__disclaimer {
  margin: 0 0 var(--ys-spacing-4);
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-regular);
}

.risk-dialog__detected {
  padding: var(--ys-spacing-3);
  margin-bottom: var(--ys-spacing-4);
  background-color: var(--el-color-danger-light-9);
  border: 1px solid var(--el-color-danger-light-7);
  border-radius: var(--ys-radius-base);
}

.risk-dialog__detected-title {
  margin: 0 0 var(--ys-spacing-2);
  font-size: var(--ys-font-size-sm);
  font-weight: 600;
}

.risk-dialog__detected-list {
  padding-left: var(--ys-spacing-5);
  margin: 0;
  font-size: var(--ys-font-size-xs);
}

.risk-dialog__detected-item {
  font-family: monospace;
  line-height: 1.6;
  color: var(--el-color-danger);
}

.risk-dialog__checks {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-2);
  margin-bottom: var(--ys-spacing-4);
}

.risk-dialog__check-item {
  font-size: var(--ys-font-size-sm);
  line-height: 1.4;
}

.risk-dialog__confirm-text {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.risk-dialog__confirm-label {
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-regular);
}

.risk-dialog__confirm-code {
  padding: 2px 6px;
  font-size: var(--ys-font-size-xs);
  background-color: var(--el-fill-color);
  border-radius: 3px;
}

.risk-dialog__mismatch {
  margin: 0;
  font-size: var(--ys-font-size-xs);
  color: var(--el-color-danger);
}
</style>
