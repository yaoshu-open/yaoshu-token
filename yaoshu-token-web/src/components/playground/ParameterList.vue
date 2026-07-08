<script setup lang="ts">
/**
 * ParameterList - 6 参数 ElSlider + 启用开关 + 流式开关。
 * 木偶组件：消费 props + emit update。
 */
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
  reset: []
}>()

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
  <div class="parameter-list">
    <!-- 流式开关（独立组） -->
    <div class="parameter-list__row parameter-list__row--switch">
      <div class="parameter-list__label">
        <span class="parameter-list__name">Stream</span>
        <span class="parameter-list__desc">{{ $t('playground.params.stream') }}</span>
      </div>
      <el-switch
        :model-value="config.stream"
        @update:model-value="(v: boolean | string | number) => updateConfig('stream', v === true)"
      />
    </div>

    <!-- Temperature -->
    <div class="parameter-list__row">
      <div class="parameter-list__header">
        <div class="parameter-list__label">
          <span class="parameter-list__name">Temperature</span>
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
        :show-tooltip="true"
        @update:model-value="(v: number | number[]) => updateConfig('temperature', Array.isArray(v) ? v[0] : v)"
      />
    </div>

    <!-- Top P -->
    <div class="parameter-list__row">
      <div class="parameter-list__header">
        <div class="parameter-list__label">
          <span class="parameter-list__name">Top P</span>
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
    <div class="parameter-list__row">
      <div class="parameter-list__header">
        <div class="parameter-list__label">
          <span class="parameter-list__name">Max Tokens</span>
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
        class="parameter-list__number"
        @update:model-value="(v) => updateConfig('max_tokens', typeof v === 'number' ? v : 1)"
      />
    </div>

    <!-- Frequency Penalty -->
    <div class="parameter-list__row">
      <div class="parameter-list__header">
        <div class="parameter-list__label">
          <span class="parameter-list__name">Frequency Penalty</span>
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
    <div class="parameter-list__row">
      <div class="parameter-list__header">
        <div class="parameter-list__label">
          <span class="parameter-list__name">Presence Penalty</span>
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
    <div class="parameter-list__row">
      <div class="parameter-list__header">
        <div class="parameter-list__label">
          <span class="parameter-list__name">Seed</span>
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
        class="parameter-list__number"
        @update:model-value="(v) => updateConfig('seed', typeof v === 'number' ? v : null)"
      />
    </div>

    <div class="parameter-list__reset">
      <el-button
        size="small"
        plain
        @click="emit('reset')"
      >
        {{ $t('playground.params.reset') }}
      </el-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.parameter-list {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-3);
  padding: var(--ys-spacing-2) var(--ys-spacing-1);

  &__row {
    padding: 10px var(--ys-spacing-3);
    background: var(--el-fill-color-blank);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--ys-radius-md);
  }

  &__row--switch {
    display: flex;
    align-items: center;
    justify-content: space-between;
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

  &__desc {
    font-size: 11px;
    color: var(--el-text-color-secondary);
  }

  &__number {
    width: 100%;
  }

  &__reset {
    display: flex;
    justify-content: flex-end;
    margin-top: 4px;
  }
}
</style>
