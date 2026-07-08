<script setup lang="ts">
/**
 * CollapsibleAdvancedParams — 模型参数折叠区（默认收起）。
 * 收纳全部采样参数：Temperature / Top P / Max Tokens / Frequency Penalty / Presence Penalty / Seed。
 * 默认折叠，遵循大模型默认行为（参数开关默认关闭，开启后覆盖模型默认值）。
 */
import { ref } from 'vue'
import { MAX_TOKENS_LIMIT } from '@/views/playground/constants'
import type { PlaygroundConfig, ParameterEnabled } from '@/api/playground/types'

interface Props {
  config: PlaygroundConfig
  parameterEnabled: ParameterEnabled
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:config': [value: PlaygroundConfig]
  'update:parameterEnabled': [value: ParameterEnabled]
}>()

// 默认折叠：activeNames 为空数组表示全部收起
const activeNames = ref<string[]>([])

function updateConfig<K extends keyof PlaygroundConfig>(
  key: K,
  value: PlaygroundConfig[K]
): void {
  emit('update:config', { ...props.config, [key]: value })
}

function updateEnabled(key: keyof ParameterEnabled, value: boolean): void {
  emit('update:parameterEnabled', { ...props.parameterEnabled, [key]: value })
}
</script>

<template>
  <el-collapse
    v-model="activeNames"
    class="advanced-params"
  >
    <el-collapse-item
      name="advanced"
    >
      <template #title>
        <span class="advanced-params__title">{{ $t('playground.settings.advancedParams') }}</span>
      </template>
      <div class="advanced-params__content">
        <!-- Temperature -->
        <div class="advanced-params__row">
          <div class="advanced-params__header">
            <div class="advanced-params__label">
              <span class="advanced-params__name">Temperature</span>
              <el-tag
                size="small"
                round
              >
                {{ Number(config.temperature).toFixed(2) }}
              </el-tag>
            </div>
            <el-switch
              :model-value="parameterEnabled.temperature"
              @update:model-value="(v: boolean | string | number) => updateEnabled('temperature', v === true)"
            />
          </div>
          <el-slider
            :model-value="config.temperature"
            :min="0"
            :max="2"
            :step="0.1"
            :disabled="!parameterEnabled.temperature"
            @update:model-value="(v: number | number[]) => updateConfig('temperature', Array.isArray(v) ? v[0] : v)"
          />
        </div>

        <!-- Top P -->
        <div class="advanced-params__row">
          <div class="advanced-params__header">
            <div class="advanced-params__label">
              <span class="advanced-params__name">Top P</span>
              <el-tag
                size="small"
                round
              >
                {{ Number(config.top_p).toFixed(2) }}
              </el-tag>
            </div>
            <el-switch
              :model-value="parameterEnabled.top_p"
              @update:model-value="(v: boolean | string | number) => updateEnabled('top_p', v === true)"
            />
          </div>
          <el-slider
            :model-value="config.top_p"
            :min="0"
            :max="1"
            :step="0.01"
            :disabled="!parameterEnabled.top_p"
            @update:model-value="(v: number | number[]) => updateConfig('top_p', Array.isArray(v) ? v[0] : v)"
          />
        </div>

        <!-- Max Tokens -->
        <div class="advanced-params__row">
          <div class="advanced-params__header">
            <div class="advanced-params__label">
              <span class="advanced-params__name">Max Tokens</span>
              <el-tag
                size="small"
                round
              >
                {{ config.max_tokens }}
              </el-tag>
            </div>
            <el-switch
              :model-value="parameterEnabled.max_tokens"
              @update:model-value="(v: boolean | string | number) => updateEnabled('max_tokens', v === true)"
            />
          </div>
          <el-input-number
            :model-value="config.max_tokens"
            :min="1"
            :max="MAX_TOKENS_LIMIT"
            :step="64"
            :disabled="!parameterEnabled.max_tokens"
            size="small"
            class="advanced-params__number"
            @update:model-value="(v) => updateConfig('max_tokens', typeof v === 'number' ? v : 1)"
          />
        </div>

        <el-divider class="advanced-params__sub-divider" />

        <!-- Frequency Penalty -->
        <div class="advanced-params__row">
          <div class="advanced-params__header">
            <div class="advanced-params__label">
              <span class="advanced-params__name">Frequency Penalty</span>
              <el-tag
                size="small"
                round
              >
                {{ Number(config.frequency_penalty).toFixed(2) }}
              </el-tag>
            </div>
            <el-switch
              :model-value="parameterEnabled.frequency_penalty"
              @update:model-value="(v: boolean | string | number) => updateEnabled('frequency_penalty', v === true)"
            />
          </div>
          <el-slider
            :model-value="config.frequency_penalty"
            :min="-2"
            :max="2"
            :step="0.1"
            :disabled="!parameterEnabled.frequency_penalty"
            @update:model-value="(v: number | number[]) => updateConfig('frequency_penalty', Array.isArray(v) ? v[0] : v)"
          />
        </div>

        <!-- Presence Penalty -->
        <div class="advanced-params__row">
          <div class="advanced-params__header">
            <div class="advanced-params__label">
              <span class="advanced-params__name">Presence Penalty</span>
              <el-tag
                size="small"
                round
              >
                {{ Number(config.presence_penalty).toFixed(2) }}
              </el-tag>
            </div>
            <el-switch
              :model-value="parameterEnabled.presence_penalty"
              @update:model-value="(v: boolean | string | number) => updateEnabled('presence_penalty', v === true)"
            />
          </div>
          <el-slider
            :model-value="config.presence_penalty"
            :min="-2"
            :max="2"
            :step="0.1"
            :disabled="!parameterEnabled.presence_penalty"
            @update:model-value="(v: number | number[]) => updateConfig('presence_penalty', Array.isArray(v) ? v[0] : v)"
          />
        </div>

        <!-- Seed -->
        <div class="advanced-params__row">
          <div class="advanced-params__header">
            <div class="advanced-params__label">
              <span class="advanced-params__name">Seed</span>
              <el-tag
                size="small"
                round
              >
                {{ config.seed ?? '—' }}
              </el-tag>
            </div>
            <el-switch
              :model-value="parameterEnabled.seed"
              @update:model-value="(v: boolean | string | number) => updateEnabled('seed', v === true)"
            />
          </div>
          <el-input-number
            :model-value="config.seed ?? undefined"
            :step="1"
            :disabled="!parameterEnabled.seed"
            size="small"
            class="advanced-params__number"
            @update:model-value="(v) => updateConfig('seed', typeof v === 'number' ? v : null)"
          />
        </div>
      </div>
    </el-collapse-item>
  </el-collapse>
</template>

<style scoped lang="scss">
.advanced-params {
  border: none;

  &__title {
    font-size: var(--ys-font-size-sm);
    font-weight: 500;
    color: var(--el-text-color-secondary);
  }

  &__content {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-3);
    padding: var(--ys-spacing-2) 0;
  }

  &__row {
    padding: 10px var(--ys-spacing-3);
    background: var(--el-fill-color-blank);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--ys-radius-md);
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 4px;
  }

  &__label {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
  }

  &__name {
    font-size: var(--ys-font-size-sm);
    font-weight: 500;
  }

  &__number {
    width: 100%;
  }

  &__sub-divider {
    margin: var(--ys-spacing-2) 0;
  }
}

:deep(.el-collapse-item__header) {
  padding: 0 var(--ys-spacing-1);
  font-size: var(--ys-font-size-sm);
  background: transparent;
  border-bottom: none;
}

:deep(.el-collapse-item__wrap) {
  background: transparent;
  border-bottom: none;
}

:deep(.el-collapse-item__content) {
  padding: 0 var(--ys-spacing-1);
}
</style>
