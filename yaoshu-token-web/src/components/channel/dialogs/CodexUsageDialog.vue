<script setup lang="ts">
/**
 * Codex 用量对话框：展示账户信息 + 速率限制窗口 + 额外计量 + Raw JSON。
 *
 * 内部自管理数据获取（打开时自动调 getCodexUsage）。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { getCodexUsage, type CodexUsageResponse } from '@/api/channel'
import { useCopyToClipboard } from '@/composables/useCopyToClipboard'
import {
  clampPercent,
  formatDurationSeconds,
  formatUnixSeconds,
  getAccountTypeBadge,
  getRateLimitStatus,
  getWindowBadge,
  parseCodexUsagePayload,
  resolveRateLimitWindows,
  type CodexAdditionalRateLimit,
  type CodexRateLimit,
  type CodexRateLimitWindow,
  type CodexUsagePayload
} from '@/lib/channel/codex-utils'
import StatusBadge from '@/components/StatusBadge.vue'
import type { Channel } from '@/api/channel/types'

const props = defineProps<{
  modelValue: boolean
  channel: Channel | null
}>()

defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const { t } = useI18n()
const { copy, copiedText } = useCopyToClipboard({ notify: false })

const response = ref<CodexUsageResponse | null>(null)
const fetchError = ref('')
const isRefreshing = ref(false)
const showRawJson = ref<string[]>([])

// 打开时自动获取数据
watch(
  () => props.modelValue,
  async (open) => {
    if (open && props.channel?.id) {
      showRawJson.value = []
      await fetchUsage()
    } else if (!open) {
      response.value = null
      showRawJson.value = []
    }
  }
)

async function fetchUsage(): Promise<void> {
  if (!props.channel?.id) return
  isRefreshing.value = true
  fetchError.value = ''
  try {
    response.value = await getCodexUsage(props.channel.id)
  } catch (e) {
    fetchError.value = (e as Error)?.message || t('channel.codex.fetchFailed')
    ElMessage.error(fetchError.value)
  } finally {
    isRefreshing.value = false
  }
}

const payload = computed<CodexUsagePayload | null>(() =>
  parseCodexUsagePayload(response.value)
)

const rateLimit = computed(() => payload.value?.rate_limit)
const accountType = computed(
  () => payload.value?.plan_type ?? rateLimit.value?.plan_type
)
const accountBadge = computed(() => getAccountTypeBadge(accountType.value))
const statusBadge = computed(() =>
  getRateLimitStatus(rateLimit.value ?? null)
)

const additionalRateLimits = computed<CodexAdditionalRateLimit[]>(() =>
  (payload.value?.additional_rate_limits ?? []).filter(
    (item) => item && Object.keys(item).length > 0
  )
)

const errorMessage = computed(() => fetchError.value.trim())

const rawJsonText = computed(() => {
  if (!response.value) return ''
  try {
    return JSON.stringify(response.value, null, 2)
  } catch {
    return String(response.value?.data ?? '')
  }
})

/** 速率限制窗口子组件数据 */
interface WindowData {
  title: string
  window: CodexRateLimitWindow | null
}

function buildSectionWindows(
  source: { plan_type?: string; rate_limit?: CodexRateLimit } | null
): { fiveHour: WindowData; weekly: WindowData } {
  const { fiveHourWindow, weeklyWindow } = resolveRateLimitWindows(source)
  return {
    fiveHour: { title: t('channel.codex.fiveHourWindow'), window: fiveHourWindow },
    weekly: { title: t('channel.codex.weeklyWindow'), window: weeklyWindow }
  }
}

function handleCopy(text: string): void {
  void copy(text)
}
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :title="t('channel.codex.usageTitle')"
    width="800px"
    :close-on-click-modal="false"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="codex-usage">
      <!-- 错误提示 -->
      <div
        v-if="errorMessage"
        class="codex-usage__error"
      >
        {{ errorMessage }}
      </div>

      <!-- 账户摘要 -->
      <div class="codex-usage__account">
        <div class="codex-usage__account-header">
          <div class="codex-usage__badges">
            <StatusBadge
              :label="accountBadge.label"
              :variant="accountBadge.variant"
            />
            <StatusBadge
              :label="t(statusBadge.label)"
              :variant="statusBadge.variant"
            />
            <StatusBadge
              v-if="typeof response?.upstreamStatus === 'number'"
              :label="`${t('common.status')}: ${response.upstreamStatus}`"
              variant="neutral"
            />
          </div>
          <ElButton
            size="small"
            :loading="isRefreshing"
            @click="fetchUsage"
          >
            <i class="i-ep-refresh mr-1" />
            {{ t('common.refresh') }}
          </ElButton>
        </div>

        <!-- 账户身份信息 -->
        <div class="codex-usage__identity">
          <div class="codex-usage__identity-row">
            <span class="codex-usage__identity-label">User ID</span>
            <span class="codex-usage__identity-value mono">{{ payload?.user_id || '-' }}</span>
            <ElButton
              v-if="payload?.user_id"
              text
              size="small"
              @click="handleCopy(payload.user_id)"
            >
              <i
                v-if="copiedText === payload.user_id"
                class="i-ep-check text-green-600"
              />
              <i
                v-else
                class="i-ep-document-copy"
              />
            </ElButton>
          </div>
          <div class="codex-usage__identity-row">
            <span class="codex-usage__identity-label">{{ t('channel.codex.email') }}</span>
            <span class="codex-usage__identity-value">{{ payload?.email || '-' }}</span>
            <ElButton
              v-if="payload?.email"
              text
              size="small"
              @click="handleCopy(payload.email)"
            >
              <i
                v-if="copiedText === payload.email"
                class="i-ep-check text-green-600"
              />
              <i
                v-else
                class="i-ep-document-copy"
              />
            </ElButton>
          </div>
          <div class="codex-usage__identity-row">
            <span class="codex-usage__identity-label">Account ID</span>
            <span class="codex-usage__identity-value mono">{{ payload?.account_id || '-' }}</span>
            <ElButton
              v-if="payload?.account_id"
              text
              size="small"
              @click="handleCopy(payload.account_id)"
            >
              <i
                v-if="copiedText === payload.account_id"
                class="i-ep-check text-green-600"
              />
              <i
                v-else
                class="i-ep-document-copy"
              />
            </ElButton>
          </div>
        </div>
      </div>

      <!-- 速率限制窗口 -->
      <div class="codex-usage__rate-limits">
        <div class="codex-usage__section-title">
          {{ t('channel.codex.rateLimitWindows') }}
        </div>
        <p class="codex-usage__section-desc">
          {{ t('channel.codex.rateLimitDesc') }}
        </p>

        <!-- 基础限制 -->
        <div class="codex-usage__rate-group">
          <div class="codex-usage__rate-group-title">
            {{ t('channel.codex.baseLimits') }}
          </div>
          <div class="codex-usage__windows-grid">
            <div
              v-for="wd in buildSectionWindows(payload)"
              :key="wd.title"
              class="rate-window"
            >
              <div class="rate-window__header">
                <span class="rate-window__title">{{ wd.title }}</span>
                <StatusBadge
                  :label="`${getWindowBadge(wd.window).percent}%`"
                  :variant="getWindowBadge(wd.window).variant"
                />
              </div>
              <ElProgress
                :percentage="clampPercent(wd.window?.used_percent)"
                :stroke-width="8"
              />
              <div
                v-if="wd.window && Object.keys(wd.window).length > 0"
                class="rate-window__details"
              >
                <div>{{ t('channel.codex.resetAt') }}: {{ formatUnixSeconds(wd.window.reset_at) }}</div>
                <div>{{ t('channel.codex.resetsIn') }}: {{ formatDurationSeconds(wd.window.reset_after_seconds) }}</div>
                <div>{{ t('channel.codex.window') }}: {{ formatDurationSeconds(wd.window.limit_window_seconds) }}</div>
              </div>
              <div
                v-else
                class="rate-window__details"
              >
                -
              </div>
            </div>
          </div>
        </div>

        <!-- 额外计量限制 -->
        <div
          v-if="additionalRateLimits.length > 0"
          class="codex-usage__additional"
        >
          <div class="codex-usage__rate-group-title">
            {{ t('channel.codex.additionalLimits') }}
          </div>
          <p class="codex-usage__section-desc">
            {{ t('channel.codex.additionalLimitsDesc') }}
          </p>
          <div
            v-for="(item, index) in additionalRateLimits"
            :key="`${item.limit_name}-${item.metered_feature ?? ''}-${index}`"
            class="codex-usage__rate-group"
            :class="{ 'codex-usage__rate-group--bordered': index > 0 }"
          >
            <div class="codex-usage__rate-group-title">
              {{ item.limit_name || item.metered_feature || `${t('channel.codex.additionalLimit')} ${index + 1}` }}
            </div>
            <div
              v-if="item.metered_feature"
              class="codex-usage__metered"
            >
              <span class="codex-usage__metered-label">metered_feature</span>
              <code class="codex-usage__metered-value">{{ item.metered_feature }}</code>
            </div>
            <div class="codex-usage__windows-grid">
              <div
                v-for="wd in buildSectionWindows(item)"
                :key="wd.title"
                class="rate-window"
              >
                <div class="rate-window__header">
                  <span class="rate-window__title">{{ wd.title }}</span>
                  <StatusBadge
                    :label="`${getWindowBadge(wd.window).percent}%`"
                    :variant="getWindowBadge(wd.window).variant"
                  />
                </div>
                <ElProgress
                  :percentage="clampPercent(wd.window?.used_percent)"
                  :stroke-width="8"
                />
                <div
                  v-if="wd.window && Object.keys(wd.window).length > 0"
                  class="rate-window__details"
                >
                  <div>{{ t('channel.codex.resetAt') }}: {{ formatUnixSeconds(wd.window.reset_at) }}</div>
                  <div>{{ t('channel.codex.resetsIn') }}: {{ formatDurationSeconds(wd.window.reset_after_seconds) }}</div>
                  <div>{{ t('channel.codex.window') }}: {{ formatDurationSeconds(wd.window.limit_window_seconds) }}</div>
                </div>
                <div
                  v-else
                  class="rate-window__details"
                >
                  -
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Raw JSON 折叠面板 -->
      <ElCollapse v-model="showRawJson">
        <ElCollapseItem
          :title="t('channel.codex.rawJson')"
          name="raw"
        >
          <div class="codex-usage__raw-actions">
            <ElButton
              size="small"
              :disabled="!rawJsonText"
              @click="handleCopy(rawJsonText)"
            >
              <i
                v-if="copiedText === rawJsonText"
                class="i-ep-check mr-1 text-green-600"
              />
              <i
                v-else
                class="i-ep-document-copy mr-1"
              />
              {{ t('common.copy') }}
            </ElButton>
          </div>
          <pre class="codex-usage__raw-json">{{ rawJsonText || '-' }}</pre>
        </ElCollapseItem>
      </ElCollapse>
    </div>

    <template #footer>
      <ElButton @click="$emit('update:modelValue', false)">
        {{ t('common.close') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped lang="scss">
.codex-usage {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);

  &__error {
    padding: var(--ys-spacing-2) var(--ys-spacing-3);
    font-size: var(--ys-font-size-base);
    color: var(--el-color-danger);
    background: var(--el-color-danger-light-9);
    border: 1px solid var(--el-color-danger-light-7);
    border-radius: var(--ys-radius-sm);
  }

  &__account {
    padding: var(--ys-spacing-3);
    border: 1px solid var(--el-border-color);
    border-radius: var(--ys-radius-sm);
  }

  &__account-header {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-2);
    align-items: center;
    justify-content: space-between;
  }

  &__badges {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-1);
  }

  &__identity {
    padding: var(--ys-spacing-2);
    margin-top: var(--ys-spacing-2);
    background: var(--el-fill-color-light);
    border-radius: var(--ys-radius-sm);
  }

  &__identity-row {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
    padding: 2px 0;
  }

  &__identity-label {
    flex-shrink: 0;
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-secondary);
  }

  &__identity-value {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: var(--ys-font-size-sm);
    white-space: nowrap;

    &.mono {
      font-family: var(--el-font-family-mono, monospace);
    }
  }

  &__section-title {
    font-size: var(--ys-font-size-base);
    font-weight: 500;
  }

  &__section-desc {
    margin: 2px 0 var(--ys-spacing-2);
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-secondary);
  }

  &__rate-group {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);

    &--bordered {
      padding-top: var(--ys-spacing-3);
      border-top: 1px solid var(--el-border-color);
    }
  }

  &__rate-group-title {
    font-size: var(--ys-font-size-base);
    font-weight: 500;
  }

  &__windows-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    gap: var(--ys-spacing-3);
  }

  &__metered {
    display: flex;
    gap: 6px;
    align-items: center;
    max-width: 100%;
    padding: 2px 6px;
    background: var(--el-fill-color-light);
    border-radius: var(--ys-radius-sm);
  }

  &__metered-label {
    flex-shrink: 0;
    font-size: 11px;
  }

  &__metered-value {
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: var(--ys-font-size-sm);
    white-space: nowrap;
  }

  &__raw-actions {
    display: flex;
    justify-content: flex-end;
    padding-bottom: 6px;
  }

  &__raw-json {
    max-height: 320px;
    padding: var(--ys-spacing-2);
    margin: 0;
    overflow: auto;
    font-size: var(--ys-font-size-sm);
    word-break: break-all;
    white-space: pre-wrap;
    background: var(--el-fill-color-light);
    border-radius: var(--ys-radius-sm);
  }
}

.rate-window {
  padding: var(--ys-spacing-3);
  border: 1px solid var(--el-border-color);
  border-radius: var(--ys-radius-sm);

  &__header {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
    justify-content: space-between;
    margin-bottom: var(--ys-spacing-2);
  }

  &__title {
    font-size: var(--ys-font-size-base);
    font-weight: 500;
  }

  &__details {
    display: flex;
    flex-direction: column;
    gap: 2px;
    margin-top: 6px;
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-secondary);
  }
}
</style>
