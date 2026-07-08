<script setup lang="ts">
/**
 * RoleSelector — Playground 输入区上方的消息角色选择器。
 *
 * 选 user（默认）：submit 触发 sendMessage（发起请求）
 * 选 assistant/system：submit 触发 insertMessage（仅插入消息，不发起请求，用于 few-shot 构造）
 */
import { useI18n } from 'vue-i18n'
import { ElSelect, ElOption } from 'element-plus'

type ChatRole = 'user' | 'assistant' | 'system'

interface Props {
  modelValue: ChatRole
  disabled?: boolean
}

defineProps<Props>()
defineEmits<{
  'update:modelValue': [value: ChatRole]
}>()

const { t } = useI18n()

const options: Array<{ value: ChatRole; labelKey: string }> = [
  { value: 'user', labelKey: 'playground.role.user' },
  { value: 'assistant', labelKey: 'playground.role.assistant' },
  { value: 'system', labelKey: 'playground.role.system' }
]
</script>

<template>
  <div class="role-selector">
    <span class="role-selector__label">{{ t('playground.role.selectLabel') }}</span>
    <ElSelect
      :model-value="modelValue"
      size="small"
      :disabled="disabled"
      class="role-selector__select"
      @update:model-value="(v: ChatRole) => $emit('update:modelValue', v)"
    >
      <ElOption
        v-for="opt in options"
        :key="opt.value"
        :value="opt.value"
        :label="t(opt.labelKey)"
      />
    </ElSelect>
    <span class="role-selector__hint">
      {{ modelValue === 'user' ? t('playground.role.hintUser') : t('playground.role.hintInsert') }}
    </span>
  </div>
</template>

<style scoped lang="scss">
.role-selector {
  display: inline-flex;
  gap: 6px;
  align-items: center;

  &__label {
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
  }

  &__select {
    width: 110px;
  }

  &__hint {
    font-size: 11px;
    color: var(--el-text-color-placeholder);
  }
}
</style>
