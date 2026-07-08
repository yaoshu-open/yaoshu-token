<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { TaskLog } from '@/api/task/types'

interface TaskAudioPreviewDialogProps {
  open: boolean
  log: TaskLog | null
}

const props = defineProps<TaskAudioPreviewDialogProps>()
const emit = defineEmits<{ (e: 'update:open', v: boolean): void }>()
const { t } = useI18n()

const visible = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})

interface AudioClip {
  title?: string
  audioUrl?: string
  imageUrl?: string
}

const clips = computed<AudioClip[]>(() => {
  if (!props.log?.data) return []
  try {
    const parsed = JSON.parse(props.log.data)
    if (!Array.isArray(parsed)) return []
    return parsed.filter((c: unknown) => c && typeof c === 'object' && (c as AudioClip).audioUrl)
  } catch {
    return []
  }
})
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="t('usageLogs.task.audioPreview')"
    width="480px"
  >
    <div class="task-audio-dialog">
      <div
        v-for="(clip, idx) in clips"
        :key="idx"
        class="task-audio-dialog__item"
      >
        <span class="task-audio-dialog__title">{{ clip.title || `Clip ${idx + 1}` }}</span>
        <audio
          v-if="clip.audioUrl"
          :src="clip.audioUrl"
          controls
          class="task-audio-dialog__audio"
        />
      </div>
      <ElEmpty
        v-if="clips.length === 0"
        :description="t('usageLogs.task.noAudio')"
        :image-size="60"
      />
    </div>
  </ElDialog>
</template>

<style scoped lang="scss">
.task-audio-dialog {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-3);

  &__item {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: var(--ys-spacing-3);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--ys-radius-md);
  }

  &__title {
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-primary);
  }

  &__audio {
    width: 100%;
  }
}
</style>
