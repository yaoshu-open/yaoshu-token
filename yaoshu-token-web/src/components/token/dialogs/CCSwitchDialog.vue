<script setup lang="ts">
/**
 * Claim 切换对话框（导入到 CC Switch 桌面应用）。
 *
 * 构建 ccswitch:// 协议 URL 并通过 window.open 打开，非后端 API 调用。
 */
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElDialog, ElForm, ElFormItem, ElInput, ElButton, ElRadioGroup, ElRadioButton, ElSelect, ElOption, ElMessage } from 'element-plus'
import { getUserModels } from '@/api/playground'
import type { Token } from '@/api/token/types'

const { t } = useI18n()

const props = defineProps<{
  visible: boolean
  token: Token | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
}>()

type AppType = 'claude' | 'codex' | 'gemini'

const APP_CONFIGS: Record<AppType, { label: string; defaultName: string; modelFields: { key: string; label: string; required: boolean }[] }> = {
  claude: {
    label: 'Claude',
    defaultName: 'My Claude',
    modelFields: [
      { key: 'model', label: 'Primary Model', required: true },
      { key: 'haikuModel', label: 'Haiku Model', required: false },
      { key: 'sonnetModel', label: 'Sonnet Model', required: false },
      { key: 'opusModel', label: 'Opus Model', required: false },
    ],
  },
  codex: {
    label: 'Codex',
    defaultName: 'My Codex',
    modelFields: [{ key: 'model', label: 'Primary Model', required: true }],
  },
  gemini: {
    label: 'Gemini',
    defaultName: 'My Gemini',
    modelFields: [{ key: 'model', label: 'Primary Model', required: true }],
  },
}

const app = ref<AppType>('claude')
const name = ref('')
const models = ref<Record<string, string>>({})
const modelOptions = ref<{ label: string; value: string }[]>([])
const loadingModels = ref(false)

const currentConfig = computed(() => APP_CONFIGS[app.value])

watch(
  () => props.visible,
  async (val) => {
    if (val) {
      app.value = 'claude'
      name.value = APP_CONFIGS.claude.defaultName
      models.value = {}
      await loadModels()
    }
  }
)

async function loadModels(): Promise<void> {
  loadingModels.value = true
  try {
    modelOptions.value = await getUserModels()
  } catch {
    modelOptions.value = []
  } finally {
    loadingModels.value = false
  }
}

function handleAppChange(val: string | number | boolean | undefined): void {
  const appVal = val as AppType
  app.value = appVal
  name.value = APP_CONFIGS[appVal].defaultName
  models.value = {}
}

function getServerAddress(): string {
  try {
    const raw = localStorage.getItem('status')
    if (raw) {
      const status = JSON.parse(raw)
      if (status.serverAddress) return status.serverAddress
    }
  } catch {
    // ignore
  }
  return window.location.origin
}

function handleSubmit(): void {
  if (!models.value.model) {
    ElMessage.warning(t('token.dialog.ccSwitch.selectPrimaryModel'))
    return
  }
  if (!props.token) return

  const apiKey = props.token.key.startsWith('sk-')
    ? props.token.key
    : `sk-${props.token.key}`

  const serverAddress = getServerAddress()
  const endpoint = app.value === 'codex' ? `${serverAddress}/v1` : serverAddress
  const params = new URLSearchParams()
  params.set('resource', 'provider')
  params.set('app', app.value)
  params.set('name', name.value)
  params.set('endpoint', endpoint)
  params.set('apiKey', apiKey)
  for (const [k, v] of Object.entries(models.value)) {
    if (v) params.set(k, v)
  }
  params.set('homepage', serverAddress)
  params.set('enabled', 'true')
  const url = `ccswitch://v1/import?${params.toString()}`

  window.open(url, '_blank')
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="t('token.dialog.ccSwitch.title')"
    width="480px"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form label-width="120px">
      <el-form-item :label="t('token.dialog.ccSwitch.token')">
        <el-input
          :model-value="token?.name ?? ''"
          disabled
        />
      </el-form-item>
      <el-form-item :label="t('token.dialog.ccSwitch.application')">
        <el-radio-group
          :model-value="app"
          @change="handleAppChange"
        >
          <el-radio-button
            v-for="(cfg, key) in APP_CONFIGS"
            :key="key"
            :value="key"
          >
            {{ cfg.label }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item :label="t('token.dialog.ccSwitch.name')">
        <el-input
          v-model="name"
          :placeholder="currentConfig.defaultName"
        />
      </el-form-item>
      <el-form-item
        v-for="field in currentConfig.modelFields"
        :key="field.key"
        :label="field.label + (field.required ? ' *' : '')"
      >
        <el-select
          v-model="models[field.key]"
          filterable
          allow-create
          :loading="loadingModels"
          :placeholder="t('token.dialog.ccSwitch.selectModel')"
          style="width: 100%"
        >
          <el-option
            v-for="opt in modelOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <div style="display: flex; gap: var(--ys-spacing-2); justify-content: flex-end">
        <el-button @click="emit('update:visible', false)">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          @click="handleSubmit"
        >
          {{ t('token.dialog.ccSwitch.openCCSwitch') }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>
