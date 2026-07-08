<script setup lang="ts">
/**
 * 渠道密钥查看对话框。
 * 通过安全验证（Passkey/2FA）后展示渠道密钥，支持脱敏切换与复制。
 *
 * 复用项目预置的 useSecureVerification + SecureVerificationDialog 基础设施：
 * withVerification 包装 getChannelKey —— 直接成功则展示，需验证则自动弹验证 Dialog。
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { CopyDocument, View, Hide } from '@element-plus/icons-vue'
import { getChannelKey } from '@/api/channel'
import type { Channel } from '@/api/channel/types'
import { useSecureVerification } from '@/composables/auth/useSecureVerification'
import SecureVerificationDialog from '@/components/auth/SecureVerificationDialog.vue'

const props = defineProps<{
  modelValue: boolean
  channel: Channel | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const keyValue = ref('')
const loading = ref(false)
const revealed = ref(false)
const copied = ref(false)

// 脱敏展示：非换行字符替换为 •，保留多行 key 结构
const maskedKey = computed(() => keyValue.value.replace(/[^\n]/g, '•'))

// 安全验证：onSuccess 拿到验证后回放的 apiCall 结果
const verification = useSecureVerification({
  onSuccess: (result) => applyKey(result),
  onError: () => {
    loading.value = false
  }
})

function applyKey(result: unknown): void {
  keyValue.value = (result as { key?: string } | null)?.key ?? ''
  loading.value = false
  if (keyValue.value) {
    ElMessage.success(t('channel.actions.viewKeySuccess'))
  }
}

async function fetchKey(): Promise<void> {
  if (!props.channel) return
  loading.value = true
  keyValue.value = ''
  revealed.value = false
  try {
    const result = await verification.withVerification(
      () => getChannelKey(props.channel!.id),
      {
        title: t('channel.keyDialog.title'),
        description: t('channel.keyDialog.verifyHint')
      }
    )
    // 直接成功（无需验证）：withVerification 返回结果；需验证时返回 null，走 onSuccess
    if (result !== null && result !== undefined) {
      applyKey(result)
    }
  } catch {
    loading.value = false
    // 业务错误已由请求拦截器/verification 统一提示
  }
}

// 验证 Dialog 关闭且未拿到密钥 → 视为取消，关闭主弹窗
watch(
  () => verification.open.value,
  (isOpen, wasOpen) => {
    if (wasOpen && !isOpen && !keyValue.value && visible.value) {
      visible.value = false
    }
  }
)

watch(visible, (open) => {
  if (open) {
    fetchKey()
  } else {
    // 关闭时重置敏感数据
    keyValue.value = ''
    revealed.value = false
    loading.value = false
  }
})

async function handleCopy(): Promise<void> {
  if (!keyValue.value) return
  try {
    await navigator.clipboard.writeText(keyValue.value)
    copied.value = true
    ElMessage.success(t('channel.keyDialog.copySuccess'))
    setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch {
    ElMessage.error(t('channel.keyDialog.copyFailed'))
  }
}

function toggleReveal(): void {
  revealed.value = !revealed.value
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="t('channel.keyDialog.title')"
    width="520px"
    append-to-body
    :close-on-click-modal="false"
  >
    <template v-if="channel">
      <p class="channel-key-dialog__desc">
        {{ t('channel.keyDialog.description') }}
        <strong>{{ channel.name }}</strong>
      </p>

      <div
        v-loading="loading"
        class="channel-key-dialog__content"
      >
        <template v-if="keyValue">
          <div class="channel-key-dialog__field">
            <el-input
              :model-value="revealed ? keyValue : maskedKey"
              readonly
              :rows="3"
              type="textarea"
              resize="none"
              class="channel-key-dialog__input"
            />
            <div class="channel-key-dialog__actions">
              <el-button
                :icon="revealed ? Hide : View"
                size="small"
                @click="toggleReveal"
              >
                {{ revealed ? t('channel.keyDialog.hide') : t('channel.keyDialog.show') }}
              </el-button>
              <el-button
                :icon="CopyDocument"
                size="small"
                type="primary"
                :class="{ 'is-copied': copied }"
                @click="handleCopy"
              >
                {{ t('channel.keyDialog.copy') }}
              </el-button>
            </div>
          </div>
          <p class="channel-key-dialog__warning">
            <i class="i-ep-warning-filled" />
            {{ t('channel.keyDialog.securityWarning') }}
          </p>
        </template>

        <el-empty
          v-else-if="!loading"
          :description="t('channel.keyDialog.empty')"
          :image-size="64"
        />
      </div>
    </template>

    <template #footer>
      <el-button
        :disabled="loading"
        @click="visible = false"
      >
        {{ t('common.close') }}
      </el-button>
    </template>

    <!-- 安全验证 Dialog（需要二次验证时由 useSecureVerification 控制） -->
    <SecureVerificationDialog
      :model-value="verification.open.value"
      :methods="verification.methods.value"
      :state="verification.state.value"
      @verify="(method: '2fa' | 'passkey', code?: string) => verification.executeVerification(method, code)"
      @cancel="verification.cancel()"
      @code-change="(code: string) => verification.setCode(code)"
      @method-change="(method: '2fa' | 'passkey') => verification.switchMethod(method)"
      @update:model-value="(v: boolean) => { if (!v) verification.cancel() }"
    />
  </el-dialog>
</template>

<style scoped>
.channel-key-dialog__desc {
  margin: 0 0 var(--ys-spacing-4);
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-secondary);
}

.channel-key-dialog__desc strong {
  margin-left: 4px;
  color: var(--el-text-color-primary);
}

.channel-key-dialog__content {
  min-height: 120px;
}

.channel-key-dialog__field {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-3);
}

.channel-key-dialog__input :deep(.el-textarea__inner) {
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  font-size: var(--ys-font-size-sm);
  word-break: break-all;
}

.channel-key-dialog__actions {
  display: flex;
  gap: var(--ys-spacing-2);
}

.channel-key-dialog__warning {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 10px var(--ys-spacing-3);
  margin: var(--ys-spacing-4) 0 0;
  font-size: var(--ys-font-size-xs);
  color: var(--el-color-warning);
  background-color: var(--el-color-warning-light-9);
  border-radius: var(--ys-radius-base);
}

.channel-key-dialog__warning i {
  flex-shrink: 0;
  font-size: var(--ys-font-size-base);
}
</style>
