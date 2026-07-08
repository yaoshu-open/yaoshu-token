<script setup lang="ts">
// 包含 ModalityIcons（内联图标）和 ModalitiesMatrix（输入输出矩阵）。
import { useI18n } from 'vue-i18n'
import {
  Document,
  Picture,
  Microphone,
  EditPen,
  VideoCamera
} from '@element-plus/icons-vue'
import type { Modality } from '@/api/pricing/types'

const props = withDefaults(defineProps<{
  modalities: Modality[]
  className?: string
}>(), {
  className: ''
})

const { t } = useI18n()

const MODALITY_META: Record<Modality, { icon: any; labelKey: string }> = {
  text: { icon: EditPen, labelKey: 'pricing.text' },
  image: { icon: Picture, labelKey: 'pricing.image' },
  audio: { icon: Microphone, labelKey: 'pricing.audio' },
  video: { icon: VideoCamera, labelKey: 'pricing.video' },
  file: { icon: Document, labelKey: 'pricing.file' }
}
</script>

<template>
  <span
    v-if="modalities.length === 0"
    class="modality-icons modality-icons--empty"
  >—</span>
  <span
    v-else
    class="modality-icons"
    :class="className"
  >
    <el-tooltip
      v-for="modality in modalities"
      :key="modality"
      :content="t(MODALITY_META[modality].labelKey)"
      placement="top"
    >
      <el-icon
        :size="14"
        class="modality-icons__icon"
      >
        <component :is="MODALITY_META[modality].icon" />
      </el-icon>
    </el-tooltip>
  </span>
</template>

<style scoped lang="scss">
.modality-icons {
  display: inline-flex;
  gap: var(--ys-spacing-1);
  align-items: center;

  &__icon {
    color: var(--el-text-color-regular);
  }

  &--empty {
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-placeholder);
  }
}
</style>
