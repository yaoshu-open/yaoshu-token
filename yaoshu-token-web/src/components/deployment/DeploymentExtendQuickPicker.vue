<script setup lang="ts">
/**
 * 延长快速选择按钮组。
 */
import { useI18n } from 'vue-i18n'

defineProps<{
  modelValue: number
  options: ReadonlyArray<number>
  disabled: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: number): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="deployment-quick-picker">
    <span class="deployment-quick-picker__label">{{ t('deployment.extend.quickPick') }}:</span>
    <div class="deployment-quick-picker__buttons">
      <el-button
        v-for="opt in options"
        :key="opt"
        :type="modelValue === opt ? 'primary' : 'default'"
        :disabled="disabled"
        size="small"
        @click="emit('update:modelValue', opt)"
      >
        {{ opt }}h
      </el-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.deployment-quick-picker {
  display: flex;
  flex-direction: column;
  gap: 6px;

  &__label {
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-secondary);
  }

  &__buttons {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }
}
</style>
