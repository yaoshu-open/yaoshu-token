<script setup lang="ts">
/**
 * Codex OAuth 授权对话框（三步流：打开授权页 → 粘贴回调 URL → 生成凭证）。
 *
 * 内部自管理三步流状态，生成凭证后 emit keyGenerated 回填到 key 输入框。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { completeCodexOAuth, startCodexOAuth } from '@/api/channel'
import { useCopyToClipboard } from '@/composables/useCopyToClipboard'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'keyGenerated', key: string): void
}>()

const { t } = useI18n()
const { copy, copiedText } = useCopyToClipboard({ notify: false })

const authorizeUrl = ref('')
const callbackUrl = ref('')
const isStarting = ref(false)
const isCompleting = ref(false)

// 对话框关闭时重置全部状态
watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      authorizeUrl.value = ''
      callbackUrl.value = ''
      isStarting.value = false
      isCompleting.value = false
    }
  }
)

const canCopyAuthorizeUrl = computed(
  () => Boolean(authorizeUrl.value && !isStarting.value)
)
const canComplete = computed(
  () => Boolean(callbackUrl.value.trim()) && !isCompleting.value
)

function tryPrettyJson(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

async function handleStart(): Promise<void> {
  isStarting.value = true
  try {
    const res = await startCodexOAuth()
    const url = res.authorizeUrl || ''
    if (!url) {
      throw new Error(t('channel.codex.missingAuthorizeUrl'))
    }
    authorizeUrl.value = url
    try {
      window.open(url, '_blank', 'noopener,noreferrer')
      ElMessage.success(t('channel.codex.openedAuthPage'))
    } catch {
      ElMessage.warning(t('channel.codex.manuallyCopyHint'))
    }
  } catch (e) {
    ElMessage.error((e as Error)?.message || t('channel.codex.oauthStartFailed'))
  } finally {
    isStarting.value = false
  }
}

async function handleComplete(): Promise<void> {
  if (!callbackUrl.value.trim()) return
  isCompleting.value = true
  try {
    const res = await completeCodexOAuth(callbackUrl.value.trim())
    const rawKey = res.key || ''
    if (!rawKey) {
      throw new Error(t('channel.codex.missingKey'))
    }
    emit('keyGenerated', tryPrettyJson(rawKey))
    ElMessage.success(t('channel.codex.credentialGenerated'))
    emit('update:modelValue', false)
  } catch (e) {
    ElMessage.error((e as Error)?.message || t('channel.codex.oauthFailed'))
  } finally {
    isCompleting.value = false
  }
}

async function handleCopyAuthUrl(): Promise<void> {
  if (!authorizeUrl.value) return
  await copy(authorizeUrl.value)
}
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :title="t('channel.codex.oauthTitle')"
    width="700px"
    :close-on-click-modal="false"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="codex-oauth">
      <!-- 操作指引 -->
      <ElAlert
        :title="t('channel.codex.oauthDesc')"
        type="info"
        :closable="false"
        show-icon
      >
        <template #default>
          <p>{{ t('channel.codex.step1') }}</p>
          <p>{{ t('channel.codex.step2') }}</p>
          <p>{{ t('channel.codex.step3') }}</p>
          <p>{{ t('channel.codex.step4') }}</p>
        </template>
      </ElAlert>

      <!-- 授权按钮组 -->
      <div class="codex-oauth__actions">
        <ElButton
          type="primary"
          :loading="isStarting"
          @click="handleStart"
        >
          <i
            v-if="!isStarting"
            class="i-ep-external-link mr-1"
          />
          {{ t('channel.codex.openAuthPage') }}
        </ElButton>
        <ElButton
          :disabled="!canCopyAuthorizeUrl"
          @click="handleCopyAuthUrl"
        >
          <i
            v-if="copiedText === authorizeUrl"
            class="i-ep-check mr-1 text-green-600"
          />
          <i
            v-else
            class="i-ep-document-copy mr-1"
          />
          {{ t('channel.codex.copyAuthLink') }}
        </ElButton>
      </div>

      <!-- 回调 URL 输入 -->
      <div class="codex-oauth__field">
        <label class="codex-oauth__label">
          {{ t('channel.codex.callbackUrl') }}
        </label>
        <ElInput
          v-model="callbackUrl"
          :placeholder="t('channel.codex.callbackPlaceholder')"
          autocomplete="off"
          spellcheck="false"
        />
        <p class="codex-oauth__hint">
          {{ t('channel.codex.callbackHint') }}
        </p>
      </div>
    </div>

    <template #footer>
      <ElButton
        :disabled="isStarting || isCompleting"
        @click="emit('update:modelValue', false)"
      >
        {{ t('common.cancel') }}
      </ElButton>
      <ElButton
        type="primary"
        :loading="isCompleting"
        :disabled="!canComplete"
        @click="handleComplete"
      >
        {{ isCompleting ? t('channel.codex.generating') : t('channel.codex.generateCredential') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped lang="scss">
.codex-oauth {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);

  &__actions {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-2);
  }

  &__field {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-1);
  }

  &__label {
    font-size: var(--el-font-size-base);
    font-weight: 500;
  }

  &__hint {
    margin: 0;
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-secondary);
  }
}
</style>
