<template>
  <ElDialog
    :model-value="modelValue"
    :title="t('profile.unbind') + ' ' + t('profile.email')"
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
        :label="t('profile.verificationCode')"
        prop="code"
      >
        <div class="email-unbind__code-row">
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
        type="danger"
        :loading="unbinding"
        @click="handleUnbind"
      >
        {{ t('profile.unbind') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onUnmounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { sendEmailVerification, unbindEmail } from '@/api/profile'

const props = defineProps<{
  modelValue: boolean
  email: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  unbound: []
}>()

const { t } = useI18n()
const formRef = ref<FormInstance>()
const sendingCode = ref(false)
const unbinding = ref(false)
const countdown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const form = reactive({
  code: '',
})

const rules: FormRules = {
  code: [{ required: true, message: t('profile.enterCode'), trigger: 'blur' }],
}

watch(
  () => props.modelValue,
  (val) => {
    if (!val) {
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
  sendingCode.value = true
  try {
    await sendEmailVerification(props.email)
    ElMessage.success(t('profile.codeSent'))
    startCountdown()
  } catch {
    // 错误由 request 拦截器处理
  } finally {
    sendingCode.value = false
  }
}

async function handleUnbind(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  unbinding.value = true
  try {
    await unbindEmail({ code: form.code })
    ElMessage.success(t('profile.unboundEmail'))
    emit('unbound')
    emit('update:modelValue', false)
  } catch {
    // 错误由 request 拦截器处理
  } finally {
    unbinding.value = false
  }
}
</script>

<style scoped>
.email-unbind__code-row {
  display: flex;
  gap: var(--ys-spacing-2);
  width: 100%;
}

.email-unbind__code-row .ElInput {
  flex: 1;
}
</style>
