<script setup lang="ts">
/**
 * PlaygroundSettingsPanel — 左侧常驻设置面板。
 *
 * 分区：模型选择 → 模型参数(默认折叠) → System Prompt → 预设模板 → 工具入口
 */
import type {
  PlaygroundConfig,
  ParameterEnabled,
  ModelOption,
  GroupOption
} from '@/api/playground/types'
import ModelGroupSelector from './ModelGroupSelector.vue'
import CollapsibleAdvancedParams from './CollapsibleAdvancedParams.vue'
import SystemPromptEditor from './SystemPromptEditor.vue'
import ConfigManager from './ConfigManager.vue'
import PromptTemplateSection from './PromptTemplateSection.vue'
import type { PromptTemplate } from '@/composables/playground/usePromptTemplates'
import { EditPen, Tools } from '@element-plus/icons-vue'

interface Props {
  config: PlaygroundConfig
  parameterEnabled: ParameterEnabled
  models: ModelOption[]
  groups: GroupOption[]
  customRequestMode: boolean
  disabled?: boolean
  loading?: boolean
  exportableMessages: Record<string, unknown>[]
  templates: PromptTemplate[]
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  loading: false
})

const emit = defineEmits<{
  'update:config': [value: PlaygroundConfig]
  'update:parameterEnabled': [value: ParameterEnabled]
  'update:model': [value: string]
  'update:group': [value: string]
  'update:systemPrompt': [value: string]
  'open-custom-request': []
  'open-debug': []
  'config-import': [value: { config: PlaygroundConfig; parameterEnabled: ParameterEnabled }]
  'config-reset': [value: { resetMessages: boolean }]
  'save-template': [name: string]
  'load-template': [id: string]
  'delete-template': [id: string]
}>()
</script>

<template>
  <div class="settings-panel">
    <!-- 模型与分组 -->
    <div class="settings-panel__section">
      <ModelGroupSelector
        :models="models"
        :model-value="config.model"
        :groups="groups"
        :group-value="config.group"
        :disabled="disabled"
        :loading="loading"
        @update:model-value="(v: string) => emit('update:model', v)"
        @update:group-value="(v: string) => emit('update:group', v)"
      />
    </div>

    <el-divider class="settings-panel__divider" />

    <!-- 模型参数（默认折叠，遵循大模型默认行为，开启开关后覆盖模型默认值） -->
    <CollapsibleAdvancedParams
      :config="config"
      :parameter-enabled="parameterEnabled"
      @update:config="(v: PlaygroundConfig) => emit('update:config', v)"
      @update:parameter-enabled="(v: ParameterEnabled) => emit('update:parameterEnabled', v)"
    />

    <el-divider class="settings-panel__divider" />

    <!-- System Prompt -->
    <div class="settings-panel__section">
      <SystemPromptEditor
        :model-value="config.systemPrompt"
        @update:model-value="(v: string) => emit('update:systemPrompt', v)"
      />
    </div>

    <el-divider class="settings-panel__divider" />

    <!-- 预设模板 -->
    <div class="settings-panel__section">
      <PromptTemplateSection
        :templates="templates"
        @save="(name: string) => emit('save-template', name)"
        @load="(id: string) => emit('load-template', id)"
        @delete="(id: string) => emit('delete-template', id)"
      />
    </div>

    <el-divider class="settings-panel__divider" />

    <!-- 工具入口 -->
    <div class="settings-panel__tools">
      <el-button
        size="small"
        :icon="EditPen"
        :type="customRequestMode ? 'warning' : 'default'"
        @click="emit('open-custom-request')"
      >
        {{ $t('playground.drawer.customRequest') }}
      </el-button>
      <el-button
        size="small"
        :icon="Tools"
        @click="emit('open-debug')"
      >
        {{ $t('playground.toolbar.debug') }}
      </el-button>
      <ConfigManager
        :config="config"
        :parameter-enabled="parameterEnabled"
        :messages="exportableMessages"
        @import="(v) => emit('config-import', v)"
        @reset="(v) => emit('config-reset', v)"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.settings-panel {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-1);
  height: 100%;
  padding: var(--ys-spacing-3);
  overflow-y: auto;

  &__section {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-3);
  }

  &__divider {
    margin: var(--ys-spacing-1) 0;
  }

  &__param {
    padding: 10px var(--ys-spacing-3);
    background: var(--el-fill-color-blank);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--ys-radius-md);
  }

  &__param-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 4px;
  }

  &__param-label {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
  }

  &__param-name {
    font-size: var(--ys-font-size-sm);
    font-weight: 500;
  }

  &__number {
    width: 100%;
  }

  &__tools {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-2);
    align-items: center;
  }
}
</style>
