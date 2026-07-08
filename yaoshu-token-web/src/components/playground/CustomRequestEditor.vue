<script setup lang="ts">
/**
 * CustomRequestEditor - 自定义请求体 JSON 编辑器（开启开关 + 校验 + 格式化）。
 */
import { ref, watch, computed } from 'vue'
import { Edit, Check, Close } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

interface Props {
  modelValue: { mode: boolean; body: string }
  defaultPayload?: object
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: { mode: boolean; body: string }]
}>()

const localMode = ref<boolean>(props.modelValue.mode)
const localBody = ref<string>(props.modelValue.body)

watch(
  () => props.modelValue,
  (next) => {
    localMode.value = next.mode
    localBody.value = next.body
  },
  { deep: true }
)

const isValidJson = computed<boolean>(() => {
  if (!localBody.value.trim()) return true
  try {
    JSON.parse(localBody.value)
    return true
  } catch {
    return false
  }
})

const errorMessage = computed<string>(() => {
  if (isValidJson.value) return ''
  try {
    JSON.parse(localBody.value)
    return ''
  } catch (err) {
    return err instanceof Error ? err.message : 'JSON 解析失败'
  }
})

function emitChange(): void {
  emit('update:modelValue', { mode: localMode.value, body: localBody.value })
}

function handleModeChange(enabled: boolean): void {
  localMode.value = enabled
  if (enabled && props.defaultPayload && !localBody.value.trim()) {
    localBody.value = JSON.stringify(props.defaultPayload, null, 2)
  }
  emitChange()
}

function handleBodyChange(value: string): void {
  localBody.value = value
  emitChange()
}

function formatJson(): void {
  if (!isValidJson.value) {
    ElMessage.error('JSON 格式错误，无法格式化')
    return
  }
  try {
    const parsed = JSON.parse(localBody.value)
    localBody.value = JSON.stringify(parsed, null, 2)
    emitChange()
  } catch {
    // ignore
  }
}
</script>

<template>
  <div class="custom-request-editor">
    <div class="custom-request-editor__header">
      <div class="custom-request-editor__title">
        <el-icon><Edit /></el-icon>
        <span>{{ $t('playground.customRequest.mode') }}</span>
      </div>
      <el-switch
        :model-value="localMode"
        @update:model-value="(v: boolean | string | number) => handleModeChange(v === true)"
      />
    </div>

    <el-alert
      v-if="localMode"
      type="warning"
      :closable="false"
      show-icon
      :title="$t('playground.customRequest.warning')"
    />

    <template v-if="localMode">
      <div class="custom-request-editor__toolbar">
        <div class="custom-request-editor__status">
          <el-icon
            v-if="isValidJson"
            class="is-valid"
          >
            <Check />
          </el-icon>
          <el-icon
            v-else
            class="is-invalid"
          >
            <Close />
          </el-icon>
          <span :class="isValidJson ? 'is-valid' : 'is-invalid'">
            {{ isValidJson ? $t('playground.customRequest.valid') : $t('playground.customRequest.invalid') }}
          </span>
        </div>
        <el-button
          size="small"
          :disabled="!isValidJson"
          @click="formatJson"
        >
          {{ $t('playground.customRequest.format') }}
        </el-button>
      </div>

      <el-input
        v-model="localBody"
        type="textarea"
        :autosize="{ minRows: 8, maxRows: 18 }"
        placeholder="{&quot;model&quot;: &quot;gpt-4o&quot;, &quot;messages&quot;: [...]}"
        resize="vertical"
        class="custom-request-editor__textarea"
        @update:model-value="handleBodyChange"
      />

      <div
        v-if="!isValidJson"
        class="custom-request-editor__error"
      >
        {{ errorMessage }}
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.custom-request-editor {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-2);
  padding: var(--ys-spacing-2) var(--ys-spacing-1);

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__title {
    display: flex;
    gap: 6px;
    align-items: center;
    font-size: var(--ys-font-size-sm);
    font-weight: 500;
  }

  &__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__status {
    display: flex;
    gap: var(--ys-spacing-1);
    align-items: center;
    font-size: var(--ys-font-size-xs);

    .is-valid {
      color: var(--el-color-success);
    }

    .is-invalid {
      color: var(--el-color-danger);
    }
  }

  &__textarea {
    :deep(.el-textarea__inner) {
      font-family: SFMono-Regular, Consolas, 'Liberation Mono', Menlo, monospace;
      font-size: var(--ys-font-size-xs);
      line-height: 1.5;
    }
  }

  &__error {
    font-size: var(--ys-font-size-xs);
    line-height: 1.4;
    color: var(--el-color-danger);
  }
}
</style>
