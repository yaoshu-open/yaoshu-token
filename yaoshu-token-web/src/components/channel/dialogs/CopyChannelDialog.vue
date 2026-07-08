<script setup lang="ts">
/**
 * 复制渠道对话框。
 * 输入名称后缀，选择是否重置余额和已用配额。
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElButton, ElCheckbox, ElDialog, ElFormItem, ElInput, ElMessage } from 'element-plus'
import { copyChannel } from '@/api/channel'
import type { Channel } from '@/api/channel/types'

const props = defineProps<{
  modelValue: boolean
  channel: Channel | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}>()

const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const suffix = ref('_copy')
const resetBalance = ref(true)
const isCopying = ref(false)

watch(visible, (v) => {
  if (v) {
    suffix.value = '_copy'
    resetBalance.value = true
  }
})

const previewName = computed(() => {
  return `${props.channel?.name ?? ''}${suffix.value}`
})

async function handleCopy(): Promise<void> {
  if (!props.channel) return

  isCopying.value = true
  try {
    await copyChannel(props.channel.id, {
      suffix: suffix.value,
      reset_balance: resetBalance.value
    })
    ElMessage.success(t('channel.dialog.copy.success'))
    emit('success')
    visible.value = false
  } catch (e) {
    ElMessage.error((e as Error)?.message || t('channel.dialog.copy.failed'))
  } finally {
    isCopying.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="t('channel.dialog.copy.title')"
    width="440px"
    append-to-body
  >
    <template v-if="channel">
      <p class="copy-dialog__desc">
        {{ t('channel.dialog.copy.description') }}
        <strong>{{ channel.name }}</strong>
      </p>

      <div class="copy-dialog__form">
        <el-form-item :label="t('channel.dialog.copy.suffix')">
          <el-input
            v-model="suffix"
            :placeholder="t('channel.dialog.copy.suffixPlaceholder')"
            :disabled="isCopying"
          />
          <p class="copy-dialog__preview">
            {{ t('channel.dialog.copy.preview') }}: {{ previewName }}
          </p>
        </el-form-item>

        <div class="copy-dialog__checkbox-row">
          <el-checkbox
            v-model="resetBalance"
            :disabled="isCopying"
          >
            {{ t('channel.dialog.copy.resetBalance') }}
          </el-checkbox>
        </div>
      </div>
    </template>

    <template #footer>
      <el-button
        :disabled="isCopying"
        @click="visible = false"
      >
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="isCopying"
        @click="handleCopy"
      >
        {{ isCopying
          ? t('channel.dialog.copy.copying')
          : t('channel.dialog.copy.confirm')
        }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.copy-dialog__desc {
  margin: 0 0 var(--ys-spacing-5);
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-secondary);
}

.copy-dialog__desc strong {
  margin-left: 4px;
  color: var(--el-text-color-primary);
}

.copy-dialog__form {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);
}

.copy-dialog__preview {
  margin: var(--ys-spacing-1) 0 0;
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}

.copy-dialog__checkbox-row {
  padding: var(--ys-spacing-1) 0;
}
</style>
