<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatQuotaBilling, formatBillingCurrencyFromUSD } from '@/utils/currency'
import { getLogTypeMapping } from '@/api/usage-log/constants'
import type { UsageLog, LogOtherData } from '@/api/usage-log/types'
import type { BillingDisplayMode } from '@/api/usage-log/constants'

interface CommonLogsDetailsDialogProps {
  open: boolean
  log: UsageLog | null
  billingDisplayMode: BillingDisplayMode
}

const props = defineProps<CommonLogsDetailsDialogProps>()
const emit = defineEmits<{ (e: 'update:open', v: boolean): void }>()
const { t } = useI18n()

const visible = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})

function parseOther(other?: string): LogOtherData | null {
  if (!other) return null
  try {
    return JSON.parse(other) as LogOtherData
  } catch {
    return null
  }
}

const otherData = computed(() => (props.log ? parseOther(props.log.other) : null))

// 流式标识：优先 other.is_stream，回退 logs 表 is_stream 列
const isStream = computed(() => {
  if (otherData.value?.isStream != null) return otherData.value.isStream
  return props.log?.isStream ?? false
})

const quotaText = computed(() => {
  if (!props.log) return '-'
  if (props.billingDisplayMode === 'usd') {
    return formatQuotaBilling(props.log.quota)
  }
  return new Intl.NumberFormat().format(props.log.quota)
})

function formatTime(ts: number): string {
  if (!ts) return '-'
  const d = new Date(ts * 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function formatUSD(usd: number): string {
  const n = Number(usd)
  if (!Number.isFinite(n)) return '-'
  const formatted = formatBillingCurrencyFromUSD(n, { digitsLarge: 4, digitsSmall: 6, abbreviate: false })
  return `${formatted}/M`
}

function formatPrice(price: number): string {
  const n = Number(price)
  if (!Number.isFinite(n)) return '-'
  return formatBillingCurrencyFromUSD(n, { digitsLarge: 4, digitsSmall: 4, abbreviate: false })
}

function num(val: unknown): number {
  const n = Number(val)
  return isFinite(n) ? n : 0
}

// ms → 人类可读（如 1.2s / 429ms）
function formatMs(ms: number | undefined | null): string {
  if (ms == null || !Number.isFinite(Number(ms))) return '-'
  const v = Number(ms)
  if (v >= 1000) return `${(v / 1000).toFixed(2)}s`
  return `${Math.round(v)}ms`
}

// tok/s 生成速度（completion_tokens / completion_latency * 1000）
function tokensPerSecond(): string {
  const cl = num(otherData.value?.completion_latency)
  const ct = num(props.log?.completionTokens)
  if (cl <= 0 || ct <= 0) return '-'
  return `${(ct / cl * 1000).toFixed(1)} tok/s`
}

interface BillingRow {
  label: string
  value: string
  highlight?: boolean
}

const billingRows = computed<BillingRow[]>(() => {
  const other = otherData.value
  if (!other || !props.log) return []
  const rows: BillingRow[] = []

  const modelRatio = other.modelRatio ?? 0
  const completionRatio = other.completionRatio ?? 1
  const modelPrice = other.modelPrice ?? 0
  const baseInputUSD = modelRatio * 2.0

  const isTiered = other.billingMode === 'tiered_expr'
  const isPerCall = modelPrice > 0

  if (isTiered) {
    rows.push({ label: t('usageLogs.details.billingMode'), value: t('usageLogs.details.billingTiered') })
    if (other.matchedTier) {
      rows.push({ label: t('usageLogs.details.matchedTier'), value: other.matchedTier })
    }
  } else if (isPerCall) {
    rows.push({ label: t('usageLogs.details.billingMode'), value: t('usageLogs.details.billingPerCall') })
    rows.push({ label: t('usageLogs.details.modelPrice'), value: formatPrice(modelPrice) })
  } else {
    rows.push({ label: t('usageLogs.details.billingMode'), value: t('usageLogs.details.billingPerToken') })
    if (baseInputUSD > 0) {
      rows.push({ label: t('usageLogs.details.inputPrice'), value: formatUSD(baseInputUSD) })
    }
    if (completionRatio !== 1 && baseInputUSD > 0) {
      rows.push({ label: t('usageLogs.details.outputPrice'), value: formatUSD(baseInputUSD * completionRatio) })
    }
  }

  const ugr = Number(other.userGroupRatio)
  if (other.userGroupRatio != null && Number.isFinite(ugr) && ugr !== -1) {
    rows.push({ label: t('usageLogs.details.userGroupRatio'), value: `${ugr.toFixed(4)}x` })
  } else if (other.groupRatio != null && Number.isFinite(Number(other.groupRatio))) {
    rows.push({ label: t('usageLogs.details.groupRatioDetail'), value: `${Number(other.groupRatio).toFixed(4)}x` })
  }

  const hasCache = (other.cachedTokens ?? 0) > 0 || (other.cacheCreationTokens ?? 0) > 0
  if (!isTiered && hasCache) {
    if (other.cache_ratio != null && other.cache_ratio !== 1 && baseInputUSD > 0) {
      rows.push({ label: t('usageLogs.details.cacheRead'), value: formatUSD(baseInputUSD * other.cache_ratio) })
    }
    if (other.cacheCreationRatio != null && other.cacheCreationRatio !== 1 && baseInputUSD > 0) {
      rows.push({ label: t('usageLogs.details.cacheWrite'), value: formatUSD(baseInputUSD * other.cacheCreationRatio) })
    }
    const cc5m = num(other.cache_creation_ratio_5m)
    if (cc5m !== 0 && baseInputUSD > 0) {
      rows.push({ label: t('usageLogs.details.cacheWrite5m'), value: formatUSD(baseInputUSD * cc5m) })
    }
    const cc1h = num(other.cache_creation_ratio_1h)
    if (cc1h !== 0 && baseInputUSD > 0) {
      rows.push({ label: t('usageLogs.details.cacheWrite1h'), value: formatUSD(baseInputUSD * cc1h) })
    }
  }

  if (!isTiered && baseInputUSD > 0) {
    const audioRatio = num(other.audio_ratio)
    if (audioRatio !== 1 && audioRatio > 0) {
      rows.push({ label: t('usageLogs.details.audioInput'), value: formatUSD(baseInputUSD * audioRatio) })
    }
    const audioCompletionRatio = num(other.audio_completion_ratio)
    if (audioCompletionRatio !== 1 && audioCompletionRatio > 0) {
      rows.push({ label: t('usageLogs.details.audioOutput'), value: formatUSD(baseInputUSD * audioCompletionRatio) })
    }
    const imageRatio = num(other.image_ratio)
    if (imageRatio !== 1 && imageRatio > 0) {
      rows.push({ label: t('usageLogs.details.imageInput'), value: formatUSD(baseInputUSD * imageRatio) })
    }
  }

  const wsCount = num(other.web_search_call_count)
  if (wsCount > 0) {
    const wsPrice = num(other.web_search_price)
    rows.push({ label: t('usageLogs.details.webSearch'), value: `${wsCount}x (${formatPrice(wsPrice)})` })
  }
  const fsCount = num(other.file_search_call_count)
  if (fsCount > 0) {
    const fsPrice = num(other.file_search_price)
    rows.push({ label: t('usageLogs.details.fileSearch'), value: `${fsCount}x (${formatPrice(fsPrice)})` })
  }
  const imgGen = num(other.image_generation_call)
  if (imgGen > 0) {
    const imgPrice = num(other.image_generation_call_price)
    rows.push({ label: t('usageLogs.details.imageGeneration'), value: formatPrice(imgPrice) })
  }
  if (other.audio_input_seperate_price && num(other.audio_input_price) > 0) {
    rows.push({ label: t('usageLogs.details.audioInputPrice'), value: formatPrice(num(other.audio_input_price)) })
  }

  rows.push({ label: t('usageLogs.details.totalCost'), value: quotaText.value, highlight: true })

  return rows
})

// Token 明细（基于 other.usage 扩展对象）
interface TokenRow {
  label: string
  value: number
}

const tokenRows = computed<TokenRow[]>(() => {
  const usage = otherData.value?.usage
  const rows: TokenRow[] = []
  // 优先使用 other.usage 完整对象，回退到 logs 表字段
  const prompt = usage?.prompt_tokens ?? props.log?.promptTokens ?? 0
  const completion = usage?.completion_tokens ?? props.log?.completionTokens ?? 0

  rows.push({ label: t('usageLogs.details.tokenInput'), value: num(prompt) })
  rows.push({ label: t('usageLogs.details.tokenOutput'), value: num(completion) })

  // 缓存读/写（多源：other.usage.promptTokensDetails / other.usage.promptCacheHitTokens / other.cachedTokens 兼容）
  const cachedRead = usage?.promptTokensDetails?.cachedTokens ?? usage?.promptTokenDetails?.cachedTokens ?? usage?.promptCacheHitTokens ?? otherData.value?.cachedTokens ?? 0
  const cachedWrite = usage?.completionTokenDetails?.reasoningTokens != null
    ? 0
    : (otherData.value?.cacheCreationTokens ?? 0)
  if (num(cachedRead) > 0) rows.push({ label: t('usageLogs.details.tokenCacheRead'), value: num(cachedRead) })
  if (num(cachedWrite) > 0) rows.push({ label: t('usageLogs.details.tokenCacheWrite'), value: num(cachedWrite) })

  // 推理 tokens（仅 reasoning 模型）
  const reasoning = usage?.reasoning_tokens ?? usage?.completionTokenDetails?.reasoningTokens
  if (reasoning != null && num(reasoning) > 0) {
    rows.push({ label: t('usageLogs.details.tokenReasoning'), value: num(reasoning) })
  }

  // 音频/图像 tokens（多模态）
  const audio = usage?.audio_tokens
  if (audio != null && num(audio) > 0) {
    rows.push({ label: t('usageLogs.details.tokenAudio'), value: num(audio) })
  }
  const image = usage?.image_tokens
  if (image != null && num(image) > 0) {
    rows.push({ label: t('usageLogs.details.tokenImage'), value: num(image) })
  }

  return rows
})
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="t('usageLogs.details.title')"
    width="640px"
  >
    <div
      v-if="log"
      class="logs-details"
    >
      <div class="logs-details__row">
        <span class="logs-details__label">{{ t('usageLogs.columns.time') }}</span>
        <span class="logs-details__value">{{ formatTime(log.createdAt) }}</span>
      </div>
      <div class="logs-details__row">
        <span class="logs-details__label">{{ t('usageLogs.columns.type') }}</span>
        <span class="logs-details__value">
          {{ t(getLogTypeMapping(log.type).labelKey) }}
          <ElTag
            v-if="isStream"
            size="small"
            type="success"
            effect="light"
            class="logs-details__stream-tag"
          >{{ t('usageLogs.details.stream') }}</ElTag>
        </span>
      </div>
      <div class="logs-details__row">
        <span class="logs-details__label">{{ t('usageLogs.columns.model') }}</span>
        <span class="logs-details__value">{{ log.modelName || '-' }}</span>
      </div>
      <div class="logs-details__row">
        <span class="logs-details__label">{{ t('usageLogs.columns.token') }}</span>
        <span class="logs-details__value">{{ log.tokenName || '-' }}</span>
      </div>
      <div class="logs-details__row">
        <span class="logs-details__label">{{ t('usageLogs.columns.quota') }}</span>
        <span class="logs-details__value">{{ quotaText }}</span>
      </div>

      <!-- 耗时明细（PD-08：基于契约 other.frt/total_latency/completion_latency） -->
      <ElDivider content-position="left">
        {{ t('usageLogs.details.timingBreakdown') }}
      </ElDivider>
      <div class="logs-details__row">
        <span class="logs-details__label">{{ t('usageLogs.details.totalLatency') }}</span>
        <span class="logs-details__value">{{ formatMs(otherData?.total_latency) }}</span>
      </div>
      <div v-if="isStream" class="logs-details__row">
        <span class="logs-details__label">{{ t('usageLogs.details.firstTokenLatency') }}</span>
        <span class="logs-details__value">{{ formatMs(otherData?.frt) }}</span>
      </div>
      <div class="logs-details__row">
        <span class="logs-details__label">{{ t('usageLogs.details.completionLatency') }}</span>
        <span class="logs-details__value">{{ formatMs(otherData?.completion_latency) }}</span>
      </div>
      <div v-if="isStream" class="logs-details__row">
        <span class="logs-details__label">{{ t('usageLogs.details.avgSpeed') }}</span>
        <span class="logs-details__value">{{ tokensPerSecond() }}</span>
      </div>

      <!-- Token 明细（PD-08：基于契约 other.usage 完整对象） -->
      <ElDivider content-position="left">
        {{ t('usageLogs.details.tokenBreakdown') }}
      </ElDivider>
      <div
        v-for="row in tokenRows"
        :key="row.label"
        class="logs-details__row"
      >
        <span class="logs-details__label">{{ row.label }}</span>
        <span class="logs-details__value">{{ row.value }}</span>
      </div>

      <ElDivider
        v-if="billingRows.length > 0"
        content-position="left"
      >
        {{ t('usageLogs.details.billingBreakdown') }}
      </ElDivider>

      <template
        v-for="row in billingRows"
        :key="row.label"
      >
        <div
          class="logs-details__row"
          :class="{ 'logs-details__row--highlight': row.highlight }"
        >
          <span class="logs-details__label">{{ row.label }}</span>
          <span class="logs-details__value">{{ row.value }}</span>
        </div>
      </template>

      <div
        v-if="otherData?.upstreamModelName"
        class="logs-details__row"
      >
        <span class="logs-details__label">{{ t('usageLogs.details.upstreamModel') }}</span>
        <span class="logs-details__value">{{ otherData.upstreamModelName }}</span>
      </div>
      <div class="logs-details__row">
        <span class="logs-details__label">{{ t('usageLogs.columns.content') }}</span>
        <span class="logs-details__value logs-details__value--content">{{ log.content || '-' }}</span>
      </div>
    </div>
  </ElDialog>
</template>

<style scoped lang="scss">
.logs-details {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-3);

  &__row {
    display: grid;
    grid-template-columns: 120px 1fr;
    gap: var(--ys-spacing-3);
    font-size: var(--ys-font-size-sm);
  }

  &__label {
    color: var(--el-text-color-secondary);
  }

  &__value {
    color: var(--el-text-color-primary);
    word-break: break-all;

    &--content {
      max-height: 200px;
      overflow-y: auto;
      white-space: pre-wrap;
    }
  }

  &__row--highlight &__value {
    font-weight: 600;
    color: var(--el-color-primary);
  }

  &__stream-tag {
    margin-left: 8px;
    vertical-align: middle;
  }
}
</style>
