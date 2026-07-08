<script setup lang="ts">
/**
 * ConfigManager - 配置导入/导出/重置（ElDropdown）。
 */
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Download, Upload, Refresh, Files } from '@element-plus/icons-vue'
import {
  DEFAULT_CONFIG,
  DEFAULT_PARAMETER_ENABLED
} from '@/views/playground/constants'
import { clearPlaygroundData } from '@/views/playground/lib/storage'
import type {
  PlaygroundConfig,
  ParameterEnabled
} from '@/api/playground/types'

interface Props {
  config: PlaygroundConfig
  parameterEnabled: ParameterEnabled
  messages: Record<string, unknown>[]
}

const props = defineProps<Props>()

const emit = defineEmits<{
  import: [value: { config: PlaygroundConfig; parameterEnabled: ParameterEnabled; messages?: Record<string, unknown>[] }]
  reset: [value: { resetMessages: boolean }]
}>()

const { t } = useI18n()

const fileInputRef = ref<HTMLInputElement | null>(null)

function handleExport(): void {
  try {
    const data = {
      config: props.config,
      parameterEnabled: props.parameterEnabled,
      messages: props.messages,
      timestamp: new Date().toISOString()
    }
    const blob = new Blob([JSON.stringify(data, null, 2)], {
      type: 'application/json'
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    const ts = new Date().toISOString().replace(/[:.]/g, '-')
    a.href = url
    a.download = `playground-config-${ts}.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success(t('playground.configManager.exported'))
  } catch (err) {
    ElMessage.error(t('playground.configManager.exportFailed', { reason: err instanceof Error ? err.message : String(err) }))
  }
}

function handleImportClick(): void {
  fileInputRef.value?.click()
}

async function handleFileChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  try {
    const text = await file.text()
    const parsed = JSON.parse(text) as Record<string, unknown>

    // 校验 schema
    if (!parsed.config || typeof parsed.config !== 'object') {
      throw new Error(t('playground.configManager.importMissingConfig'))
    }
    const config = { ...DEFAULT_CONFIG, ...(parsed.config as PlaygroundConfig) }

    const parameterEnabled = {
      ...DEFAULT_PARAMETER_ENABLED,
      ...((parsed.parameterEnabled as ParameterEnabled) || {})
    }

    const messages = Array.isArray(parsed.messages)
      ? (parsed.messages as Record<string, unknown>[])
      : props.messages

    await ElMessageBox.confirm(
      t('playground.configManager.importConfirmMsg'),
      t('playground.configManager.importConfirmTitle'),
      {
        type: 'warning',
        confirmButtonText: t('playground.configManager.importConfirmBtn'),
        cancelButtonText: t('common.cancel')
      }
    )

    emit('import', { config, parameterEnabled, messages })
    ElMessage.success(t('playground.configManager.importSuccess'))
  } catch (err) {
    if (err === 'cancel' || (err instanceof Error && err.message === 'cancel')) return
    ElMessage.error(t('playground.configManager.importFailed', { reason: err instanceof Error ? err.message : String(err) }))
  } finally {
    input.value = ''
  }
}

async function handleReset(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('playground.configManager.resetConfirmMsg'),
      t('playground.configManager.resetConfirmTitle'),
      {
        type: 'warning',
        confirmButtonText: t('playground.configManager.resetConfirmBtn'),
        cancelButtonText: t('common.cancel')
      }
    )
  } catch {
    return
  }

  try {
    const result = await ElMessageBox.confirm(
      t('playground.configManager.resetMsgConfirmMsg'),
      t('playground.configManager.resetMsgConfirmTitle'),
      {
        confirmButtonText: t('playground.configManager.resetMsgConfirmBtn'),
        cancelButtonText: t('playground.configManager.resetMsgCancelBtn'),
        type: 'warning'
      }
    )
    clearPlaygroundData()
    emit('reset', { resetMessages: true })
    ElMessage.success(t('playground.configManager.resetAllSuccess'))
    void result
  } catch {
    // 用户选了「仅重置配置」+ cancel
    clearPlaygroundData()
    emit('reset', { resetMessages: false })
    ElMessage.success(t('playground.configManager.resetConfigSuccess'))
  }
}
</script>

<template>
  <el-dropdown trigger="click">
    <el-button
      circle
      :title="$t('playground.config.title')"
    >
      <el-icon><Files /></el-icon>
    </el-button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          :icon="Download"
          @click="handleExport"
        >
          {{ $t('playground.config.export') }}
        </el-dropdown-item>
        <el-dropdown-item
          :icon="Upload"
          @click="handleImportClick"
        >
          {{ $t('playground.config.import') }}
        </el-dropdown-item>
        <el-dropdown-item
          divided
          :icon="Refresh"
          @click="handleReset"
        >
          <span class="config-manager__danger">{{ $t('playground.config.reset') }}</span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
    <input
      ref="fileInputRef"
      type="file"
      accept=".json,application/json"
      class="config-manager__file"
      @change="handleFileChange"
    >
  </el-dropdown>
</template>

<style scoped lang="scss">
.config-manager {
  &__danger {
    color: var(--el-color-danger);
  }

  &__file {
    display: none;
  }
}
</style>
