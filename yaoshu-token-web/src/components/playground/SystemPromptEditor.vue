<script setup lang="ts">
/**
 * SystemPromptEditor — ParameterDrawer system Tab 内容。
 *
 * system prompt 作为配置在每次请求前置注入到 messages 数组最前（transport.buildPayload），
 * 不进入 BubbleList 消息列表（它是调试配置，非对话消息）。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElInput } from 'element-plus'

interface Props {
  modelValue: string
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const { t } = useI18n()

const value = computed({
  get: () => props.modelValue,
  set: (v: string) => emit('update:modelValue', v)
})

const charCount = computed(() => props.modelValue.length)
const hasContent = computed(() => props.modelValue.trim().length > 0)
</script>

<template>
  <div class="system-prompt-editor">
    <p class="system-prompt-editor__desc">
      {{ t('playground.systemPrompt.desc') }}
    </p>
    <ElInput
      v-model="value"
      type="textarea"
      :rows="8"
      :placeholder="t('playground.systemPrompt.placeholder')"
      resize="vertical"
      class="system-prompt-editor__textarea"
    />
    <div class="system-prompt-editor__meta">
      <span
        class="system-prompt-editor__status"
        :class="{ 'is-active': hasContent }"
      >
        {{ hasContent ? t('playground.systemPrompt.active') : t('playground.systemPrompt.inactive') }}
      </span>
      <span class="system-prompt-editor__count">{{ charCount }} 字</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.system-prompt-editor {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-2);

  &__desc {
    margin: 0;
    font-size: var(--ys-font-size-xs);
    line-height: 1.5;
    color: var(--el-text-color-secondary);
  }

  &__textarea {
    :deep(.el-textarea__inner) {
      font-family: inherit;
      font-size: var(--ys-font-size-sm);
      line-height: 1.6;
    }
  }

  &__meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__status {
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-placeholder);

    &.is-active {
      font-weight: 600;
      color: var(--el-color-success);
    }
  }

  &__count {
    font-size: 11px;
    color: var(--el-text-color-placeholder);
  }
}
</style>
