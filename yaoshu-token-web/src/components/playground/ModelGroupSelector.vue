<script setup lang="ts">
/**
 * ModelGroupSelector - 模型 + 分组双 ElSelect（PC 横排 / 移动端折叠为单 Select）。
 */
import { computed } from 'vue'
import type { ModelOption, GroupOption } from '@/api/playground/types'
import { isFeatureHidden } from '@/plugins/spi/registry'

interface Props {
  models: ModelOption[]
  modelValue: string
  groups: GroupOption[]
  groupValue: string
  disabled?: boolean
  isMobile?: boolean
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  isMobile: false,
  loading: false
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:groupValue': [value: string]
}>()

const modelList = computed(() => props.models)
const groupList = computed(() => props.groups)

// PD-03：商业版无倍率概念，SPI 隐藏 group 选择器
const groupSelectorHidden = computed(() => isFeatureHidden('group-ratio'))

// modelValue is now always string
const resolvedModel = computed<string>(() => {
  return props.modelValue || ''
})

function onModelChange(value: string): void {
  emit('update:modelValue', value)
}

function onGroupChange(value: string): void {
  emit('update:groupValue', value)
}

// 移动端：单 Select 合并显示（group 隐藏时仅 model）
const mobileSelectValue = computed<string>(() => {
  if (groupSelectorHidden.value) return resolvedModel.value
  if (!resolvedModel.value) return ''
  return `${props.groupValue}::${resolvedModel.value}`
})

const mobileOptions = computed(() => {
  const list: { label: string; value: string }[] = []
  if (groupSelectorHidden.value) {
    for (const m of props.models) {
      list.push({ label: m.label, value: m.value })
    }
    return list
  }
  for (const g of props.groups) {
    for (const m of props.models) {
      list.push({
        label: `${g.label} / ${m.label}`,
        value: `${g.value}::${m.value}`
      })
    }
  }
  return list
})

function onMobileChange(combined: string): void {
  if (groupSelectorHidden.value) {
    emit('update:modelValue', combined)
    return
  }
  const [group, model] = combined.split('::')
  if (group) emit('update:groupValue', group)
  if (model) emit('update:modelValue', model)
}
</script>

<template>
  <div class="model-group-selector">
    <!-- 移动端：单 Select 合并 -->
    <el-select
      v-if="isMobile"
      :model-value="mobileSelectValue"
      :disabled="disabled"
      :loading="loading"
      :placeholder="$t('playground.modelPlaceholder')"
      size="default"
      class="model-group-selector__mobile"
      @change="onMobileChange"
    >
      <el-option
        v-for="opt in mobileOptions"
        :key="opt.value"
        :label="opt.label"
        :value="opt.value"
      />
    </el-select>

    <!-- PC：双 Select 横排 -->
    <template v-else>
      <el-select
        v-if="!groupSelectorHidden"
        :model-value="groupValue"
        :disabled="disabled || groupList.length === 0"
        :placeholder="$t('playground.groupPlaceholder')"
        size="default"
        class="model-group-selector__group"
        @change="onGroupChange"
      >
        <el-option
          v-for="g in groupList"
          :key="g.value"
          :label="g.label"
          :value="g.value"
        >
          <span class="model-group-selector__group-label">{{ g.label }}</span>
          <span class="model-group-selector__group-ratio">×{{ g.ratio }}</span>
        </el-option>
      </el-select>

      <el-select
        :model-value="resolvedModel"
        :disabled="disabled || modelList.length === 0"
        :loading="loading"
        :placeholder="$t('playground.modelPlaceholder')"
        size="default"
        class="model-group-selector__model"
        filterable
        @change="onModelChange"
      >
        <el-option
          v-for="m in modelList"
          :key="m.value"
          :label="m.label"
          :value="m.value"
        />
      </el-select>
    </template>
  </div>
</template>

<style scoped lang="scss">
.model-group-selector {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;

  &__group {
    width: 140px;
  }

  &__model {
    width: 200px;
  }

  &__mobile {
    flex: 1;
    min-width: 180px;
  }

  &__group-label {
    font-weight: 500;
  }

  &__group-ratio {
    float: right;
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
  }
}
</style>
