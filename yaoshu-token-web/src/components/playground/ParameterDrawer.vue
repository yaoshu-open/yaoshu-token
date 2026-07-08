<script setup lang="ts">
/**
 * ParameterDrawer - ElDrawer 右侧唤起（参数 / 自定义请求体 两个 Tab）。
 */
import { ref, watch } from 'vue'
import { Setting, EditPen } from '@element-plus/icons-vue'
import type {
  PlaygroundConfig,
  ParameterEnabled,
  ChatCompletionRequest
} from '@/api/playground/types'
import ParameterList from './ParameterList.vue'
import CustomRequestEditor from './CustomRequestEditor.vue'

interface Props {
  modelValue: boolean
  config: PlaygroundConfig
  parameterEnabled: ParameterEnabled
  customRequestMode: boolean
  customRequestBody: string
  defaultPayload?: ChatCompletionRequest | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'update:config': [value: PlaygroundConfig]
  'update:parameterEnabled': [value: ParameterEnabled]
  'update:customRequest': [value: { mode: boolean; body: string }]
  reset: []
}>()

const activeTab = ref<'parameter' | 'custom'>(props.customRequestMode ? 'custom' : 'parameter')

watch(
  () => props.customRequestMode,
  (isCustom) => {
    if (isCustom) activeTab.value = 'custom'
  }
)

function handleClose(): void {
  emit('update:modelValue', false)
}

function handleCustomUpdate(value: { mode: boolean; body: string }): void {
  emit('update:customRequest', value)
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    direction="rtl"
    size="420px"
    :with-header="true"
    :title="$t('playground.drawer.parameter')"
    @update:model-value="emit('update:modelValue', $event)"
    @close="handleClose"
  >
    <div class="parameter-drawer">
      <el-tabs
        v-model="activeTab"
        class="parameter-drawer__tabs"
      >
        <el-tab-pane :name="'parameter'">
          <template #label>
            <span class="parameter-drawer__tab">
              <el-icon><Setting /></el-icon>
              <span>{{ $t('playground.drawer.parameters') }}</span>
            </span>
          </template>
          <ParameterList
            :config="config"
            :parameter-enabled="parameterEnabled"
            @update:config="(v: PlaygroundConfig) => emit('update:config', v)"
            @update:parameter-enabled="(v: ParameterEnabled) => emit('update:parameterEnabled', v)"
            @reset="emit('reset')"
          />
        </el-tab-pane>

        <el-tab-pane :name="'custom'">
          <template #label>
            <span class="parameter-drawer__tab">
              <el-icon><EditPen /></el-icon>
              <span>{{ $t('playground.drawer.customRequest') }}</span>
            </span>
          </template>
          <CustomRequestEditor
            :model-value="{ mode: customRequestMode, body: customRequestBody }"
            :default-payload="defaultPayload || undefined"
            @update:model-value="handleCustomUpdate"
          />
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
.parameter-drawer {
  padding: 0 var(--ys-spacing-2);

  &__tab {
    display: inline-flex;
    gap: var(--ys-spacing-1);
    align-items: center;
  }
}
</style>
