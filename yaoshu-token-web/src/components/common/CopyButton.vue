<script setup lang="ts">
// 后续模块可复用，放入 components/common/。
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { CopyDocument, Check } from '@element-plus/icons-vue'

const { t } = useI18n()

const props = withDefaults(defineProps<{
  value: string
  tooltip?: string
  successTooltip?: string
}>(), {
  tooltip: 'Copy',
  successTooltip: 'Copied!'
})

const copied = ref(false)

async function handleCopy() {
  try {
    await navigator.clipboard.writeText(props.value)
    copied.value = true
    ElMessage.success(props.successTooltip)
    setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch {
    ElMessage.error(t('common.copyFailed'))
  }
}
</script>

<template>
  <button
    class="copy-button"
    :title="tooltip"
    :aria-label="tooltip"
    @click="handleCopy"
  >
    <el-icon v-if="!copied">
      <CopyDocument />
    </el-icon>
    <el-icon
      v-else
      class="copy-button--success"
    >
      <Check />
    </el-icon>
  </button>
</template>

<style scoped lang="scss">
.copy-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: var(--ys-radius-sm);
  transition: all 0.15s;

  &:hover {
    color: var(--el-color-primary);
    background: var(--el-fill-color-light);
  }

  &--success {
    color: var(--el-color-success);
  }
}
</style>
