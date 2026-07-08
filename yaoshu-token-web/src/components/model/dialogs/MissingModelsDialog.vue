<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ElDialog, ElTag, ElEmpty } from 'element-plus'

const { t } = useI18n()

defineProps<{
  visible: boolean
  models: string[]
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
}>()
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="t('model.dialog.missingModels.title')"
    width="500px"
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-loading="loading">
      <el-empty
        v-if="models.length === 0"
        :description="t('model.dialog.missingModels.empty')"
      />
      <div
        v-else
        style="display: flex; flex-wrap: wrap; gap: var(--ys-spacing-2)"
      >
        <el-tag
          v-for="model in models"
          :key="model"
          type="warning"
          size="default"
        >
          {{ model }}
        </el-tag>
      </div>
    </div>
  </el-dialog>
</template>
