<script setup lang="ts">
/**
 * 令牌表格主体组件。
 * 紧凑模式（T-TK-01）：通过 CSS 类切换行高与 padding。
 */
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElTable, ElTableColumn, ElTag, ElButton, ElDropdown, ElDropdownMenu, ElDropdownItem, ElIcon } from 'element-plus'
import { MoreFilled, View, Hide, CopyDocument } from '@element-plus/icons-vue'
import { TOKEN_STATUS_CONFIG } from '@/api/token/constants'
import { getTokenKey } from '@/api/token'
import { isFeatureHidden } from '@/plugins/spi/registry'
import { formatQuotaWithCurrency } from '@/utils/currency'
import type { Token } from '@/api/token/types'

const { t } = useI18n()

// PD-03：商业版无倍率概念，隐藏 group 列
const groupHidden = isFeatureHidden('group-ratio')

defineProps<{
  tokens: Token[]
  loading?: boolean
  isCompact?: boolean
  selectedIds?: number[]
}>()

const emit = defineEmits<{
  (e: 'selection-change', ids: number[]): void
  (e: 'row-action', action: string, token: Token): void
}>()

function formatTime(timestamp: number): string {
  if (timestamp === -1 || timestamp === 0) return 'Never'
  const date = new Date(timestamp * 1000)
  return date.toLocaleDateString()
}

function maskKey(key: string): string {
  if (!key) return '-'
  return `${key.slice(0, 8)}****${key.slice(-4)}`
}

// 密钥显隐切换：记录当前完整展示密钥的行 ID + 完整 key 缓存（列表 API 返回脱敏 key）
const revealedKeys = ref<Map<number, string>>(new Map())
const revealingIds = ref<Set<number>>(new Set())

function getKeyDisplay(row: Token): string {
  const full = revealedKeys.value.get(row.id)
  return full ?? maskKey(row.key)
}

function isKeyRevealed(id: number): boolean {
  return revealedKeys.value.has(id)
}

async function toggleRevealKey(row: Token): Promise<void> {
  const id = row.id
  if (revealedKeys.value.has(id)) {
    // 切换回隐藏
    const next = new Map(revealedKeys.value)
    next.delete(id)
    revealedKeys.value = next
    return
  }
  // 从后端获取完整 key（CriticalRateLimit 限流）
  if (revealingIds.value.has(id)) return
  const nextLoading = new Set(revealingIds.value)
  nextLoading.add(id)
  revealingIds.value = nextLoading
  try {
    const fullKey = await getTokenKey(id)
    const next = new Map(revealedKeys.value)
    next.set(id, fullKey)
    revealedKeys.value = next
  } catch {
    // 错误由 request 拦截器处理
  } finally {
    const nextDone = new Set(revealingIds.value)
    nextDone.delete(id)
    revealingIds.value = nextDone
  }
}

async function copyKeyToClipboard(row: Token): Promise<void> {
  try {
    // 优先用已缓存的完整 key，否则即时获取
    let fullKey = revealedKeys.value.get(row.id)
    if (!fullKey) {
      fullKey = await getTokenKey(row.id)
    }
    await navigator.clipboard.writeText(fullKey)
    ElMessage.success(t('common.copySuccess'))
  } catch {
    ElMessage.error(t('common.copyFailed'))
  }
}

// 状态标签 i18n 解析
function statusLabel(status: number): string {
  const conf = TOKEN_STATUS_CONFIG[status as 1 | 2 | 3 | 4]
  return conf ? t(conf.labelKey) : t('common.unknown')
}

function statusTagType(status: number): 'success' | 'info' | 'warning' | 'danger' {
  return TOKEN_STATUS_CONFIG[status as 1 | 2 | 3 | 4]?.type ?? 'info'
}

</script>

<template>
  <div
    class="tokens-table"
    :class="{ 'tokens-table--compact': isCompact }"
  >
    <el-table
      :data="tokens"
      :loading="loading"
      row-key="id"
      stripe
      @selection-change="emit('selection-change', $event.map((t: Token) => t.id))"
    >
      <el-table-column
        type="selection"
        width="45"
        fixed="left"
      />
      <el-table-column
        prop="id"
        label="ID"
        width="70"
      />
      <el-table-column
        prop="name"
        :label="t('token.columns.name')"
        min-width="120"
        fixed="left"
      />
      <el-table-column
        :label="t('token.columns.key')"
        min-width="280"
      >
        <template #default="{ row }">
          <div class="tokens-table__key-cell">
            <span class="tokens-table__key">{{ getKeyDisplay(row as Token) }}</span>
            <div class="tokens-table__key-actions">
              <button
                type="button"
                class="tokens-table__icon-btn"
                :title="isKeyRevealed(row.id) ? t('common.hide') : t('common.show')"
                :disabled="revealingIds.has(row.id)"
                @click="toggleRevealKey(row as Token)"
              >
                <el-icon><Hide v-if="isKeyRevealed(row.id)" /><View v-else /></el-icon>
              </button>
              <button
                type="button"
                class="tokens-table__icon-btn"
                :title="t('common.copy')"
                @click="copyKeyToClipboard(row as Token)"
              >
                <el-icon><CopyDocument /></el-icon>
              </button>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('common.status')"
        width="100"
      >
        <template #default="{ row }">
          <el-tag
            :type="statusTagType(row.status)"
            size="small"
          >
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('token.columns.usedQuota')"
        width="120"
      >
        <template #default="{ row }">
          <span class="tokens-table__quota">{{ formatQuotaWithCurrency(row.usedQuota) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('token.columns.remainQuota')"
        width="120"
      >
        <template #default="{ row }">
          <span class="tokens-table__quota">{{ row.unlimitedQuota ? '∞' : formatQuotaWithCurrency(row.remainQuota) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        v-if="!groupHidden"
        prop="group"
        :label="t('token.columns.group')"
        width="100"
      />
      <el-table-column
        :label="t('token.columns.createdTime')"
        width="120"
      >
        <template #default="{ row }">
          <span class="tokens-table__time">{{ formatTime(row.createdTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('token.columns.expireTime')"
        width="120"
      >
        <template #default="{ row }">
          <span class="tokens-table__time">{{ formatTime(row.expiredTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label=""
        width="80"
        fixed="right"
      >
        <template #default="{ row }">
          <el-dropdown
            trigger="click"
            @command="(cmd: string) => emit('row-action', cmd, row as Token)"
          >
            <el-button
              text
              size="small"
              circle
            >
              <el-icon><MoreFilled /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">
                  {{ t('common.edit') }}
                </el-dropdown-item>
                <el-dropdown-item command="copyKey">
                  {{ t('common.copy') }}
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="row.status !== 1"
                  command="enable"
                >
                  {{ t('common.enable') }}
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="row.status === 1"
                  command="disable"
                >
                  {{ t('common.disable') }}
                </el-dropdown-item>
                <el-dropdown-item command="ccSwitch">
                  CC Switch
                </el-dropdown-item>
                <el-dropdown-item
                  command="delete"
                  divided
                >
                  {{ t('common.delete') }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.tokens-table { height: 100%; }
.tokens-table--compact :deep(.el-table__row) { height: 36px; }
.tokens-table--compact :deep(.el-table__cell) { padding: var(--ys-spacing-1) var(--ys-spacing-2); }
.tokens-table__key-cell { display: flex; gap: var(--ys-spacing-2); align-items: center; }
.tokens-table__key-actions { display: flex; gap: 4px; align-items: center; flex-shrink: 0; }
.tokens-table__icon-btn { display: inline-flex; align-items: center; justify-content: center; width: 26px; height: 26px; padding: 0; font-size: 14px; color: var(--el-text-color-secondary); cursor: pointer; background: transparent; border: 0; border-radius: var(--el-border-radius-small); transition: color 0.2s, background-color 0.2s; }
.tokens-table__icon-btn:hover { color: var(--el-color-primary); background: var(--el-fill-color-light); }
.tokens-table__key { flex: 1; min-width: 0; overflow: hidden; font-family: monospace; font-size: var(--ys-font-size-xs); color: var(--el-text-color-secondary); text-overflow: ellipsis; white-space: nowrap; }
.tokens-table__quota { font-family: monospace; font-size: var(--ys-font-size-xs); }
.tokens-table__time { font-size: var(--ys-font-size-xs); color: var(--el-text-color-secondary); }
</style>
