<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ElDialog, ElTable, ElTableColumn, ElTag } from 'element-plus'
import type { SyncDiffData } from '@/api/model/types'

const { t } = useI18n()

withDefaults(
  defineProps<{
    visible: boolean
    conflicts?: SyncDiffData['conflicts']
  }>(),
  { conflicts: () => [] }
)

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
}>()
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="t('model.dialog.upstreamConflict.title')"
    width="700px"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-table
      :data="conflicts"
      max-height="400"
      size="small"
    >
      <el-table-column
        prop="model_name"
        :label="t('model.dialog.upstreamConflict.modelName')"
        width="180"
      />
      <el-table-column :label="t('model.dialog.upstreamConflict.conflicts')">
        <template #default="{ row }">
          <div
            v-for="f in (row.fields ?? [])"
            :key="f.field"
            style="margin-bottom: 4px"
          >
            <el-tag
              size="small"
              style="margin-right: 8px"
            >
              {{ f.field }}
            </el-tag>
            <span style="font-size: var(--ys-font-size-xs); color: var(--el-text-color-secondary)">
              {{ t('model.dialog.upstreamConflict.local') }}: {{ f.local ?? '-' }} → {{ t('model.dialog.upstreamConflict.upstream') }}: {{ f.upstream ?? '-' }}
            </span>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>
