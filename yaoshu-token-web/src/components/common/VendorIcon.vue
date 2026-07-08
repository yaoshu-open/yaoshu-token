<script setup lang="ts">
import { computed } from 'vue'

/**
 * 厂商品牌图标（公共组件）。
 * ），Vue3 侧用 @lobehub/icons-static-svg 静态 SVG 文件。
 * 策略：已知厂商用真实 SVG logo（vendor.icon 映射），未知厂商用品牌色+首字母 fallback。
 */

// 静态导入已知厂商 SVG（@lobehub/icons-static-svg，vendor.icon 值为 key）
import openaiSvg from '@lobehub/icons-static-svg/icons/openai.svg?raw'
import deepseekColorSvg from '@lobehub/icons-static-svg/icons/deepseek-color.svg?raw'
import doubaoColorSvg from '@lobehub/icons-static-svg/icons/doubao-color.svg?raw'
import geminiColorSvg from '@lobehub/icons-static-svg/icons/gemini-color.svg?raw'
import zhipuColorSvg from '@lobehub/icons-static-svg/icons/zhipu-color.svg?raw'
import moonshotSvg from '@lobehub/icons-static-svg/icons/moonshot.svg?raw'
import claudeColorSvg from '@lobehub/icons-static-svg/icons/claude-color.svg?raw'
import qwenColorSvg from '@lobehub/icons-static-svg/icons/qwen-color.svg?raw'

/** vendor.icon → SVG 字符串映射（覆盖后端 /api/pricing 返回的全部厂商） */
const VENDOR_SVG_MAP: Record<string, string> = {
  OpenAI: openaiSvg,
  'DeepSeek.Color': deepseekColorSvg,
  'Doubao.Color': doubaoColorSvg,
  'Gemini.Color': geminiColorSvg,
  'Zhipu.Color': zhipuColorSvg,
  Moonshot: moonshotSvg,
  'Claude.Color': claudeColorSvg,
  'Qwen.Color': qwenColorSvg
}

// 头部厂商品牌色（fallback 用）
const BRAND_COLOURS: Record<string, string> = {
  openai: '#10A37F',
  anthropic: '#D97757',
  google: '#4285F4',
  gemini: '#4285F4',
  deepseek: '#4D6BFE',
  mistral: '#FA520F',
  meta: '#0467DF',
  llama: '#0467DF',
  alibaba: '#615CED',
  qwen: '#615CED',
  zhipu: '#3366FF',
  glm: '#3366FF',
  chatglm: '#3366FF',
  baidu: '#2932E1',
  ernie: '#2932E1',
  moonshot: '#1D1D1F',
  kimi: '#1D1D1F',
  xai: '#1D1D1F',
  grok: '#1D1D1F',
  microsoft: '#0078D4',
  azure: '#0078D4',
  amazon: '#FF9900',
  aws: '#FF9900',
  cohere: '#39594D'
}

interface Props {
  /** 厂商显示名（用于首字母与配色 hash） */
  vendor: string
  /** 后端返回的图标标识，优先用于 SVG 精确匹配 */
  vendorIcon?: string
  /** 图标尺寸（px） */
  size?: number
}

const props = withDefaults(defineProps<Props>(), {
  vendorIcon: undefined,
  size: 22
})

// vendor.icon 精确匹配真实 SVG
const svgContent = computed(() => {
  if (props.vendorIcon && VENDOR_SVG_MAP[props.vendorIcon]) {
    return VENDOR_SVG_MAP[props.vendorIcon]
  }
  return undefined
})

// fallback 品牌色
function resolveBrandColour(): string | undefined {
  if (props.vendorIcon) {
    const direct = BRAND_COLOURS[props.vendorIcon.toLowerCase()]
    if (direct) return direct
  }
  const lower = props.vendor.toLowerCase()
  for (const key of Object.keys(BRAND_COLOURS)) {
    if (lower.includes(key)) return BRAND_COLOURS[key]
  }
  return undefined
}

function hashColour(name: string): string {
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash)
  }
  const h = Math.abs(hash) % 360
  return `hsl(${h}, 65%, 50%)`
}

const colour = computed(() => resolveBrandColour() ?? hashColour(props.vendor))
const initial = computed(() => props.vendor.charAt(0).toUpperCase())
</script>

<template>
  <span
    class="vendor-icon"
    :style="{ '--icon-size': `${size}px` }"
    :title="vendor"
  >
    <span
      v-if="svgContent"
      class="vendor-icon__svg"
      v-html="svgContent"
    />
    <span
      v-else
      class="vendor-icon__fallback"
      :style="{
        width: `${size}px`,
        height: `${size}px`,
        backgroundColor: colour,
        fontSize: `${Math.round(size * 0.45)}px`
      }"
    >{{ initial }}</span>
  </span>
</template>

<style scoped>
.vendor-icon {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
}

.vendor-icon__svg {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.vendor-icon__svg :deep(svg) {
  width: var(--icon-size, 22px);
  height: var(--icon-size, 22px);
}

.vendor-icon__fallback {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  font-family: var(--uno-font-mono, ui-monospace, monospace);
  font-weight: 600;
  line-height: 1;
  color: #fff;
  user-select: none;
  border-radius: 50%;
}
</style>
