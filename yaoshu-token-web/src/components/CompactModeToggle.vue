<script setup lang="ts">
/**
 * 表格紧凑模式切换器（T-CH-01 通用组件）。
 *
 * 任何表格均可复用：传入 tableKey 即可按维度持久化紧凑模式状态。
 * 移动端建议由调用方通过 v-if 隐藏（紧凑模式在移动端无意义）。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTableCompactMode } from '@/composables/useTableCompactMode'

const props = withDefaults(
  defineProps<{
    /** 表格唯一标识，用于持久化紧凑模式状态 */
    tableKey: string
    /** 显示模式：switch 开关 / button 图标按钮 */
    variant?: 'switch' | 'button'
    /** 是否禁用 */
    disabled?: boolean
  }>(),
  {
    variant: 'switch',
    disabled: false
  }
)

const emit = defineEmits<{
  (e: 'change', value: boolean): void
}>()

const { t } = useI18n()
const [isCompact, setCompact] = useTableCompactMode(props.tableKey)

const tooltipText = computed(() =>
  isCompact.value ? t('common.compactMode.exit') : t('common.compactMode.enable')
)

function handleChange(value: boolean | string | number): void {
  const next = Boolean(value)
  setCompact(next)
  emit('change', next)
}
</script>

<template>
  <div class="compact-mode-toggle">
    <el-tooltip
      :content="tooltipText"
      placement="top"
    >
      <el-switch
        v-if="variant === 'switch'"
        :model-value="isCompact"
        :disabled="disabled"
        size="small"
        @change="handleChange"
      />
      <el-button
        v-else
        :type="isCompact ? 'primary' : 'default'"
        :disabled="disabled"
        size="small"
        circle
        @click="handleChange(!isCompact)"
      >
        <el-icon>
          <svg
            viewBox="0 0 24 24"
            width="14"
            height="14"
          >
            <path
              v-if="isCompact"
              d="M3 9h18M3 15h18M3 5h18M3 19h18"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
            />
            <path
              v-else
              d="M3 9h18M3 15h18M3 5h18M3 19h18"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
            />
          </svg>
        </el-icon>
      </el-button>
    </el-tooltip>
  </div>
</template>

<style scoped>
.compact-mode-toggle {
  display: inline-flex;
  align-items: center;
}
</style>
