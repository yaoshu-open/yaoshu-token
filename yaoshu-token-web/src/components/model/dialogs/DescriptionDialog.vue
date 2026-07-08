<script setup lang="ts">
import { ElDialog, ElInput } from 'element-plus'

defineProps<{
  visible: boolean
  modelName: string
  description: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
}>()
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="modelName"
    width="500px"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-input
      :model-value="description"
      type="textarea"
      :rows="6"
      readonly
      resize="none"
    />
  </el-dialog>
</template>
