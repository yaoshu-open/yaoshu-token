<script setup lang="ts">
/**
 * MJ 结果图片预览对话框。
 */
import { ElDialog, ElImage } from 'element-plus'
import { useI18n } from 'vue-i18n'

defineProps<{
  modelValue: boolean
  imageUrl: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const { t } = useI18n()
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :title="t('midjourney.image.view')"
    width="600px"
    align-center
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="mj-image-dialog__body">
      <ElImage
        v-if="imageUrl"
        :src="imageUrl"
        fit="contain"
        :preview-src-list="[imageUrl]"
        hide-on-click-modal
        class="mj-image-dialog__img"
      />
    </div>
  </ElDialog>
</template>

<style scoped>
.mj-image-dialog__body {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}

.mj-image-dialog__img {
  max-width: 100%;
  max-height: 60vh;
}
</style>
