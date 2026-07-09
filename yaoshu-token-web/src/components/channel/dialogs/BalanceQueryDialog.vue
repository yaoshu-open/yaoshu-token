<script setup lang="ts">
/**
 * 余额查询对话框。
 * 展示渠道当前余额，支持手动刷新。
 *
 * 注意：Codex 类型（type=57）余额查询走 CodexUsageDialog（第四批），
 * 本对话框仅处理非 Codex 类型，Codex 类型显示提示。
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElButton, ElDialog, ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { updateChannelBalance } from '@/api/channel'
import type { Channel } from '@/api/channel/types'
import { formatBalance, formatTimestamp } from '@/lib/channel/channel-utils'

const CODEX_TYPE = 57

const props = defineProps<{
  modelValue: boolean
  channel: Channel | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success', updatedChannel: Partial<Channel>): void
}>()

const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const isQuerying = ref(false)
const localBalance = ref<number | null>(null)
const localUpdatedTime = ref<number | null>(null)

const isCodex = computed(() => props.channel?.type === CODEX_TYPE)

const displayBalance = computed(() => {
  if (localBalance.value !== null) return localBalance.value
  return props.channel?.balance ?? 0
})

const displayUpdatedTime = computed(() => {
  if (localUpdatedTime.value !== null) return localUpdatedTime.value
  return props.channel?.balanceUpdatedTime ?? 0
})

watch(visible, (v) => {
  if (v) {
    localBalance.value = null
    localUpdatedTime.value = null
  }
})

async function handleQueryBalance(): Promise<void> {
  if (!props.channel) return

  isQuerying.value = true
  try {
    const res = await updateChannelBalance(props.channel.id)
    // 请求未抛异常即为成功
    localBalance.value = res.balance ?? null
    localUpdatedTime.value = Math.floor(Date.now() / 1000)
    ElMessage.success(t('channel.dialog.balance.updateSuccess'))
    emit('success', {
      id: props.channel.id,
      balance: res.balance,
      balanceUpdatedTime: localUpdatedTime.value
    })
  } catch {
    // 错误由请求拦截器统一提示
  } finally {
    isQuerying.value = false
  }
}

function handleClose(): void {
  visible.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="t('channel.dialog.balance.title')"
    width="440px"
    append-to-body
  >
    <template v-if="channel">
      <p class="balance-dialog__desc">
        {{ t('channel.dialog.balance.description') }}
        <strong>{{ channel.name }}</strong>
      </p>

      <!-- Codex 类型提示 -->
      <div
        v-if="isCodex"
        class="balance-dialog__codex-hint"
      >
        <p>{{ t('channel.dialog.balance.codexHint') }}</p>
      </div>

      <!-- 非 Codex：余额展示 -->
      <div
        v-else
        class="balance-dialog__content"
      >
        <div class="balance-dialog__card">
          <span class="balance-dialog__label">
            {{ t('channel.dialog.balance.currentBalance') }}
          </span>
          <span class="balance-dialog__amount">
            {{ formatBalance(displayBalance) }}
          </span>
          <span class="balance-dialog__updated">
            {{ t('channel.dialog.balance.lastUpdated') }}:
            {{ formatTimestamp(displayUpdatedTime) }}
          </span>
        </div>

        <el-button
          type="primary"
          class="balance-dialog__refresh-btn"
          :loading="isQuerying"
          :icon="Refresh"
          @click="handleQueryBalance"
        >
          {{ isQuerying
            ? t('channel.dialog.balance.querying')
            : t('channel.dialog.balance.updateButton')
          }}
        </el-button>
      </div>
    </template>

    <template #footer>
      <el-button
        :disabled="isQuerying"
        @click="handleClose"
      >
        {{ t('common.close') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.balance-dialog__desc {
  margin: 0 0 var(--ys-spacing-4);
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-secondary);
}

.balance-dialog__desc strong {
  margin-left: var(--ys-spacing-1);
  color: var(--el-text-color-primary);
}

.balance-dialog__codex-hint {
  padding: var(--ys-spacing-4);
  text-align: center;
  background-color: var(--el-fill-color-light);
  border-radius: var(--ys-radius-md);
}

.balance-dialog__codex-hint p {
  margin: 0;
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-secondary);
}

.balance-dialog__content {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);
  padding: var(--ys-spacing-2) 0;
}

.balance-dialog__card {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-2);
  padding: var(--ys-spacing-5);
  background-color: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--ys-radius-md);
}

.balance-dialog__label {
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-secondary);
}

.balance-dialog__amount {
  font-size: var(--ys-font-size-3xl);
  font-weight: 700;
  color: var(--el-color-success);
}

.balance-dialog__updated {
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-placeholder);
}

.balance-dialog__refresh-btn {
  width: 100%;
}
</style>
