<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

interface TaskFailReasonDialogProps {
  open: boolean
  content: string
}

const props = defineProps<TaskFailReasonDialogProps>()
const emit = defineEmits<{ (e: 'update:open', v: boolean): void }>()
const { t } = useI18n()

const visible = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="t('usageLogs.task.failReason')"
    width="640px"
  >
    <div class="task-fail-dialog">
      <pre class="task-fail-dialog__content">{{ content }}</pre>
    </div>
  </ElDialog>
</template>

<style scoped lang="scss">
.task-fail-dialog {
  &__content {
    max-height: 400px;
    padding: var(--ys-spacing-3);
    margin: 0;
    overflow-y: auto;
    font-family: var(--el-font-family-mono, monospace);
    font-size: var(--ys-font-size-xs);
    line-height: 1.6;
    color: var(--el-color-danger);
    word-break: break-all;
    white-space: pre-wrap;
    background: var(--el-fill-color-light);
    border-radius: var(--ys-radius-md);
  }
}
</style>
