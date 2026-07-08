<script setup lang="ts">
/**
 * 缺失模型确认对话框。
 * 当 model_mapping 引用了不在 models 列表中的源模型时，提示用户确认。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElAlert, ElButton, ElDialog, ElTag } from 'element-plus'

const props = defineProps<{
  modelValue: boolean
  missingModels: string[]
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

function handleConfirm(): void {
  emit('confirm')
  visible.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="t('channel.edit.missingDialog.title')"
    width="500px"
    append-to-body
  >
    <el-alert
      :title="t('channel.edit.missingDialog.warning')"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: var(--ys-spacing-4)"
    />

    <p class="missing-dialog__desc">
      {{ t('channel.edit.missingDialog.description') }}
    </p>

    <div class="missing-dialog__models">
      <el-tag
        v-for="model in props.missingModels"
        :key="model"
        type="warning"
        size="small"
        class="missing-dialog__tag"
      >
        {{ model }}
      </el-tag>
    </div>

    <p class="missing-dialog__hint">
      {{ t('channel.edit.missingDialog.hint') }}
    </p>

    <template #footer>
      <el-button @click="visible = false">
        {{ t('channel.edit.missingDialog.cancel') }}
      </el-button>
      <el-button
        type="warning"
        @click="handleConfirm"
      >
        {{ t('channel.edit.missingDialog.confirmSave') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.missing-dialog__desc {
  margin: 0 0 var(--ys-spacing-3);
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-primary);
}

.missing-dialog__models {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: var(--ys-spacing-3);
  margin-bottom: var(--ys-spacing-3);
  background: var(--el-fill-color-light);
  border-radius: var(--ys-radius-sm);
}

.missing-dialog__hint {
  margin: 0;
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}
</style>
