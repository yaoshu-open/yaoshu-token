<script setup lang="ts">
/**
 * 上游同步向导对话框。
 *
 * 职责：预览上游差异 → 选择覆盖项 → 正式同步。
 */
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElDialog, ElSteps, ElStep, ElButton, ElTable, ElTableColumn, ElTag, ElRadioGroup, ElRadioButton } from 'element-plus'
import { ElMessage } from 'element-plus'
import { previewUpstreamDiff, syncUpstream } from '@/api/model'
import { SYNC_LOCALE_OPTIONS, SYNC_SOURCE_OPTIONS } from '@/api/model/constants'
import type { SyncDiffData, SyncLocale, SyncSource } from '@/api/model/types'

const { t } = useI18n()

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'success'): void
  (e: 'conflicts', conflicts: SyncDiffData['conflicts']): void
}>()

const activeStep = ref(0)
const loading = ref(false)
const syncing = ref(false)
const diffData = ref<SyncDiffData | null>(null)
const locale = ref<SyncLocale>('zh')
const source = ref<SyncSource>('official')

watch(
  () => props.visible,
  (val) => {
    if (val) {
      activeStep.value = 0
      diffData.value = null
      loadPreview()
    }
  }
)

async function loadPreview(): Promise<void> {
  loading.value = true
  try {
    diffData.value = await previewUpstreamDiff({ locale: locale.value, source: source.value })
  } catch {
    ElMessage.error(t('model.dialog.sync.previewFailed'))
  } finally {
    loading.value = false
  }
}

async function handleSync(): Promise<void> {
  // 有冲突时通知父组件打开冲突处理对话框
  const conflicts = diffData.value?.conflicts
  if (conflicts && conflicts.length > 0) {
    emit('conflicts', conflicts)
    return
  }

  // 无冲突直接同步
  syncing.value = true
  try {
    const result = await syncUpstream({ locale: locale.value, source: source.value })
    ElMessage.success(
      t('model.dialog.sync.syncSuccess', {
        created: result.createdModels ?? 0,
        updated: result.updatedModels ?? 0
      })
    )
    emit('success')
    emit('update:visible', false)
  } catch {
    ElMessage.error(t('model.dialog.sync.syncFailed'))
  } finally {
    syncing.value = false
  }
}

function nextStep(): void {
  if (activeStep.value < 1) activeStep.value++
}

function prevStep(): void {
  if (activeStep.value > 0) activeStep.value--
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="t('model.dialog.sync.title')"
    width="700px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-steps
      :active="activeStep"
      finish-status="success"
      style="margin-bottom: 20px"
    >
      <el-step :title="t('model.dialog.sync.preview')" />
      <el-step :title="t('model.dialog.sync.confirm')" />
    </el-steps>

    <!-- 步骤1：预览 -->
    <div
      v-show="activeStep === 0"
      v-loading="loading"
    >
      <div style=" display: flex; gap: var(--ys-spacing-3);margin-bottom: var(--ys-spacing-3)">
        <el-radio-group
          v-model="locale"
          size="small"
          @change="loadPreview"
        >
          <el-radio-button
            v-for="opt in SYNC_LOCALE_OPTIONS"
            :key="opt.value"
            :value="opt.value"
          >
            {{ opt.label }}
          </el-radio-button>
        </el-radio-group>
        <el-radio-group
          v-model="source"
          size="small"
          @change="loadPreview"
        >
          <el-radio-button
            v-for="opt in SYNC_SOURCE_OPTIONS"
            :key="opt.value"
            :value="opt.value"
          >
            {{ opt.label }}
          </el-radio-button>
        </el-radio-group>
      </div>

      <template v-if="diffData">
        <h4>{{ t('model.dialog.sync.missingModels', { count: diffData.missing?.length ?? 0 }) }}</h4>
        <el-table
          :data="diffData.missing ?? []"
          max-height="200"
          size="small"
          style="margin-bottom: 16px"
        >
          <el-table-column
            prop="model_name"
            :label="t('model.dialog.sync.modelName')"
          />
          <el-table-column
            prop="vendor"
            :label="t('model.dialog.sync.vendor')"
            width="150"
          />
        </el-table>

        <h4>{{ t('model.dialog.sync.conflicts', { count: diffData.conflicts?.length ?? 0 }) }}</h4>
        <el-table
          :data="diffData.conflicts ?? []"
          max-height="200"
          size="small"
        >
          <el-table-column
            prop="model_name"
            :label="t('model.dialog.sync.modelName')"
          />
          <el-table-column
            :label="t('model.dialog.sync.fields')"
            width="200"
          >
            <template #default="{ row }">
              <el-tag
                v-for="f in (row.fields ?? [])"
                :key="f.field"
                size="small"
                style="margin-right: 4px"
              >
                {{ f.field }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </div>

    <!-- 步骤2：确认 -->
    <div v-show="activeStep === 1">
      <p v-if="diffData && diffData.conflicts?.length">
        {{ t('model.dialog.sync.conflictFound', { count: diffData.conflicts.length }) }}
      </p>
      <p v-else>
        {{ t('model.dialog.sync.noConflict') }}
      </p>
    </div>

    <template #footer>
      <div style="display: flex; justify-content: space-between">
        <el-button
          v-if="activeStep > 0"
          @click="prevStep"
        >
          {{ t('model.dialog.sync.previous') }}
        </el-button>
        <span v-else />
        <div>
          <el-button @click="emit('update:visible', false)">
            {{ t('common.cancel') }}
          </el-button>
          <el-button
            v-if="activeStep === 0"
            type="primary"
            @click="nextStep"
          >
            {{ t('model.dialog.sync.next') }}
          </el-button>
          <el-button
            v-if="activeStep === 1"
            type="primary"
            :loading="syncing"
            @click="handleSync"
          >
            {{ diffData && diffData.conflicts?.length ? t('model.dialog.sync.resolveConflicts') : t('model.dialog.sync.sync') }}
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>
