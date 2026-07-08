<template>
  <ElDialog
    :model-value="modelValue"
    :title="t('profile.accessTokenManagement')"
    width="520px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="token-dialog__body">
      <p class="token-dialog__desc">
        {{ t('profile.accessTokenDesc') }}
      </p>

      <div class="token-dialog__field">
        <label class="token-dialog__label">{{ t('profile.currentToken') }}</label>
        <div class="token-dialog__value-row">
          <ElInput
            :model-value="displayToken"
            readonly
            :type="showToken ? 'text' : 'password'"
          >
            <template #append>
              <ElButton @click="showToken = !showToken">
                <ElIcon><View v-if="!showToken" /><Hide v-else /></ElIcon>
              </ElButton>
              <ElButton
                :disabled="!profile?.accessToken"
                @click="copyToken"
              >
                <ElIcon><CopyDocument /></ElIcon>
              </ElButton>
            </template>
          </ElInput>
        </div>
      </div>

      <ElAlert
        :title="t('profile.regenerateWarning')"
        type="warning"
        :closable="false"
        show-icon
        class="token-dialog__alert"
      />
    </div>

    <template #footer>
      <ElButton @click="$emit('update:modelValue', false)">
        {{ t('common.close') }}
      </ElButton>
      <ElButton
        type="danger"
        :loading="regenerating"
        @click="handleRegenerate"
      >
        {{ t('profile.regenerate') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { View, Hide, CopyDocument } from '@element-plus/icons-vue'
import { generateAccessToken } from '@/api/profile'
import type { UserProfile } from '@/api/profile/types'

const props = defineProps<{
  modelValue: boolean
  profile: UserProfile | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: []
}>()

const { t } = useI18n()
const showToken = ref(false)
const regenerating = ref(false)
const newToken = ref('')

const displayToken = computed(() => {
  if (newToken.value) return newToken.value
  return props.profile?.accessToken ?? ''
})

watch(
  () => props.modelValue,
  (val) => {
    if (!val) {
      showToken.value = false
      newToken.value = ''
    }
  }
)

async function copyToken(): Promise<void> {
  if (!displayToken.value) return
  await navigator.clipboard.writeText(displayToken.value)
  ElMessage.success(t('profile.tokenCopied'))
}

async function handleRegenerate(): Promise<void> {
  regenerating.value = true
  try {
    const token = await generateAccessToken()
    newToken.value = token
    showToken.value = true
    ElMessage.success(t('profile.tokenRegenerated'))
    emit('changed')
  } catch {
    // 错误由 request 拦截器处理
  } finally {
    regenerating.value = false
  }
}
</script>

<style scoped>
.token-dialog__body {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);
}

.token-dialog__desc {
  margin: 0;
  font-size: var(--ys-font-size-base);
  color: var(--el-text-color-secondary);
}

.token-dialog__field {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-2);
}

.token-dialog__label {
  font-size: var(--ys-font-size-base);
  font-weight: 500;
}

.token-dialog__alert {
  margin-top: var(--ys-spacing-1);
}
</style>
