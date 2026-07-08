<script setup lang="ts">
import { computed } from 'vue'
import { ElOption, ElSelect, ElTag } from 'element-plus'
import { useI18n } from 'vue-i18n'

interface MultiSelectOption {
  label: string
  value: string
}

const props = withDefaults(
  defineProps<{
    options: MultiSelectOption[]
    modelValue: string[]
    placeholder?: string
    disabled?: boolean
    allowCreate?: boolean
    maxVisibleTags?: number
    clearable?: boolean
    filterable?: boolean
  }>(),
  {
    placeholder: undefined,
    disabled: false,
    allowCreate: false,
    maxVisibleTags: undefined,
    clearable: true,
    filterable: true,
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', values: string[]): void
  (e: 'change', values: string[]): void
}>()

const { t } = useI18n()

const selectedValues = computed({
  get: () => props.modelValue,
  set: (val) => {
    emit('update:modelValue', val)
    emit('change', val)
  },
})

const labelMap = computed(() => {
  const map = new Map<string, string>()
  for (const opt of props.options) {
    map.set(opt.value, opt.label)
  }
  return map
})

const visibleTags = computed(() => {
  if (props.maxVisibleTags === undefined) return props.modelValue
  return props.modelValue.slice(0, props.maxVisibleTags)
})

const hiddenCount = computed(() => {
  if (props.maxVisibleTags === undefined) return 0
  return props.modelValue.length - props.maxVisibleTags
})

const placeholderText = computed(
  () => props.placeholder ?? t('common.selectItems'),
)
</script>

<template>
  <ElSelect
    v-model="selectedValues"
    multiple
    :placeholder="placeholderText"
    :disabled="disabled"
    :clearable="clearable"
    :filterable="filterable"
    :allow-create="allowCreate"
    :default-first-option="false"
    collapse-tags
    collapse-tags-tooltip
    class="multi-select"
  >
    <template #tag>
      <template v-if="maxVisibleTags !== undefined">
        <ElTag
          v-for="value in visibleTags"
          :key="value"
          closable
          :disable-transitions="false"
          @close="emit('update:modelValue', modelValue.filter(v => v !== value))"
        >
          {{ labelMap.get(value) ?? value }}
        </ElTag>
        <ElTag
          v-if="hiddenCount > 0"
          type="info"
        >
          +{{ hiddenCount }}
        </ElTag>
      </template>
    </template>

    <ElOption
      v-for="option in options"
      :key="option.value"
      :label="option.label"
      :value="option.value"
    />
  </ElSelect>
</template>

<style scoped lang="scss">
.multi-select {
  width: 100%;
}
</style>
