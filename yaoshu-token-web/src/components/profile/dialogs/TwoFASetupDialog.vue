<template>
  <ElDialog
    :model-value="modelValue"
    :title="t('profile.enableTwofa')"
    width="480px"
    :close-on-click-modal="false"
    @update:model-value="handleToggle"
  >
    <div
      v-loading="setupLoading"
      class="twofa-setup__body"
    >
      <!-- Step 1: QR 码 + Secret -->
      <template v-if="step === 'qr'">
        <p class="twofa-setup__desc">
          {{ t('profile.scanQrDesc') }}
        </p>
        <div class="twofa-setup__qr">
          <img
            v-if="isDataUrl"
            :src="setupData?.qrCodeData"
            alt="2FA QR Code"
            class="twofa-setup__qr-img"
          >
          <div
            v-else
            class="twofa-setup__qr-fallback"
          >
            <ElIcon :size="48">
              <Picture />
            </ElIcon>
            <p>{{ t('profile.qrUnavailable') }}</p>
          </div>
        </div>
        <div class="twofa-setup__secret">
          <label class="twofa-setup__label">{{ t('profile.manualEntry') }}</label>
          <ElInput
            :model-value="setupData?.secret"
            readonly
          >
            <template #append>
              <ElButton @click="copySecret">
                <ElIcon><CopyDocument /></ElIcon>
              </ElButton>
            </template>
          </ElInput>
        </div>
      </template>

      <!-- Step 2: 验证码输入 -->
      <div class="twofa-setup__verify">
        <label class="twofa-setup__label">{{ t('profile.enterVerificationCode') }}</label>
        <ElInput
          v-model="verifyCode"
          :placeholder="t('profile.verificationCodePlaceholder')"
          maxlength="6"
          @keyup.enter="handleVerify"
        />
      </div>

      <!-- Step 3: 备份码（验证成功后） -->
      <template v-if="step === 'backup'">
        <ElAlert
          :title="t('profile.backupCodesWarning')"
          type="warning"
          :closable="false"
          show-icon
        />
        <div class="twofa-setup__backup-codes">
          <div
            v-for="code in backupCodes"
            :key="code"
            class="twofa-setup__backup-code"
          >
            {{ code }}
          </div>
        </div>
        <ElButton @click="downloadBackupCodes">
          <ElIcon><Download /></ElIcon>
          {{ t('profile.downloadBackupCodes') }}
        </ElButton>
      </template>
    </div>

    <template #footer>
      <ElButton @click="handleToggle(false)">
        {{ t('common.close') }}
      </ElButton>
      <ElButton
        v-if="step === 'qr'"
        type="primary"
        :loading="verifying"
        :disabled="verifyCode.length !== 6"
        @click="handleVerify"
      >
        {{ t('profile.verify') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Picture, CopyDocument, Download } from '@element-plus/icons-vue'
import { useTwoFA } from '@/composables/profile/useTwoFA'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  enabled: []
}>()

const { t } = useI18n()
const { setupData, setupLoading, verifying, startSetup, verify, fetchBackupCodes, backupCodes } =
  useTwoFA(true)

const verifyCode = ref('')
const step = ref<'qr' | 'backup'>('qr')

const isDataUrl = computed(() =>
  setupData.value?.qrCodeData?.startsWith('data:image')
)

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      step.value = 'qr'
      verifyCode.value = ''
      startSetup()
    }
  }
)

async function copySecret(): Promise<void> {
  if (!setupData.value?.secret) return
  await navigator.clipboard.writeText(setupData.value.secret)
  ElMessage.success(t('profile.secretCopied'))
}

async function handleVerify(): Promise<void> {
  if (!setupData.value?.secret || verifyCode.value.length !== 6) return
  await verify(verifyCode.value)
  // 验证成功，获取备份码
  await fetchBackupCodes()
  step.value = 'backup'
  ElMessage.success(t('profile.twofaEnabled'))
}

function downloadBackupCodes(): void {
  const content = backupCodes.value.join('\n')
  const blob = new Blob([content], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '2fa-backup-codes.txt'
  a.click()
  URL.revokeObjectURL(url)
}

function handleToggle(val: boolean): void {
  if (!val && step.value === 'backup') {
    emit('enabled')
  }
  emit('update:modelValue', val)
}
</script>

<style scoped>
.twofa-setup__body {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);
}

.twofa-setup__desc {
  margin: 0;
  font-size: var(--ys-font-size-base);
  color: var(--el-text-color-secondary);
}

.twofa-setup__qr {
  display: flex;
  justify-content: center;
  padding: var(--ys-spacing-4);
}

.twofa-setup__qr-img {
  width: 200px;
  height: 200px;
}

.twofa-setup__qr-fallback {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-2);
  align-items: center;
  color: var(--el-text-color-secondary);
}

.twofa-setup__label {
  display: block;
  margin-bottom: var(--ys-spacing-2);
  font-size: var(--ys-font-size-base);
  font-weight: 500;
}

.twofa-setup__verify {
  margin-top: var(--ys-spacing-1);
}

.twofa-setup__backup-codes {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--ys-spacing-2);
  padding: var(--ys-spacing-3);
  font-family: monospace;
  font-size: var(--ys-font-size-base);
  background: var(--el-fill-color-light);
  border-radius: var(--ys-radius-md);
}

.twofa-setup__backup-code {
  text-align: center;
  letter-spacing: 1px;
}
</style>
