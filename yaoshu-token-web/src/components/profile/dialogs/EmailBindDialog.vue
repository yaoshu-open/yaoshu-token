<template>
  <ElDialog
    :model-value="modelValue"
    :title="t('profile.bindEmail')"
    width="440px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <ElForm
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent
    >
      <ElFormItem
        :label="t('profile.email')"
        prop="email"
      >
        <ElInput
          v-model="form.email"
          type="email"
          :placeholder="t('profile.emailPlaceholder')"
        />
      </ElFormItem>
      <ElFormItem
        :label="t('profile.verificationCode')"
        prop="code"
      >
        <div class="email-bind__code-row">
          <ElInput
            v-model="form.code"
            :placeholder="t('profile.codePlaceholder')"
            maxlength="6"
          />
          <ElButton
            :disabled="countdown > 0 || sendingCode"
            :loading="sendingCode"
            @click="handleSendCode"
          >
            {{ countdown > 0 ? `${countdown}s` : t('profile.sendCode') }}
          </ElButton>
        </div>
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton @click="$emit('update:modelValue', false)">
        {{ t('common.cancel') }}
      </ElButton>
      <ElButton
        type="primary"
        :loading="binding"
        @click="handleBind"
      >
        {{ t('profile.confirmBind') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onUnmounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { sendEmailVerification, bindEmail } from '@/api/profile'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  bound: []
}>()

const { t } = useI18n()
const formRef = ref<FormInstance>()
const sendingCode = ref(false)
const binding = ref(false)
const countdown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const form = reactive({
  email: '',
  code: '',
})

const rules: FormRules = {
  email: [
    { required: true, message: t('profile.enterEmail'), trigger: 'blur' },
    { type: 'email', message: t('profile.invalidEmail'), trigger: 'blur' },
  ],
  code: [{ required: true, message: t('profile.enterCode'), trigger: 'blur' }],
}

watch(
  () => props.modelValue,
  (val) => {
    if (!val) {
      form.email = ''
      form.code = ''
      countdown.value = 0
      if (timer) {
        clearInterval(timer)
        timer = null
      }
      formRef.value?.clearValidate()
    }
  }
)

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

function startCountdown(): void {
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0 && timer) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

async function handleSendCode(): Promise<void> {
  try {
    await formRef.value?.validateField('email')
  } catch {
    return
  }

  sendingCode.value = true
  try {
    await sendEmailVerification(form.email)
    ElMessage.success(t('profile.codeSent'))
    startCountdown()
  } catch {
    // 错误由 request 拦截器处理
  } finally {
    sendingCode.value = false
  }
}

async function handleBind(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  binding.value = true
  try {
    await bindEmail({ email: form.email, code: form.code })
    ElMessage.success(t('profile.emailBound'))
    emit('bound')
    emit('update:modelValue', false)
  } catch {
    // 错误由 request 拦截器处理
  } finally {
    binding.value = false
  }
}
</script>

<style scoped>
.email-bind__code-row {
  display: flex;
  gap: var(--ys-spacing-2);
  width: 100%;
}

.email-bind__code-row .ElInput {
  flex: 1;
}
</style>
