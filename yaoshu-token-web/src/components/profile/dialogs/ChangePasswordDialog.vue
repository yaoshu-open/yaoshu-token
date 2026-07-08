<template>
  <ElDialog
    :model-value="modelValue"
    :title="t('profile.changePassword')"
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
        :label="t('profile.originalPassword')"
        prop="originalPassword"
      >
        <ElInput
          v-model="form.originalPassword"
          type="password"
          show-password
          autocomplete="current-password"
        />
      </ElFormItem>
      <ElFormItem
        :label="t('profile.newPassword')"
        prop="password"
      >
        <ElInput
          v-model="form.password"
          type="password"
          show-password
          autocomplete="new-password"
        />
      </ElFormItem>
      <ElFormItem
        :label="t('profile.confirmPassword')"
        prop="confirmPassword"
      >
        <ElInput
          v-model="form.confirmPassword"
          type="password"
          show-password
          autocomplete="new-password"
          @keyup.enter="handleSubmit"
        />
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton @click="$emit('update:modelValue', false)">
        {{ t('common.cancel') }}
      </ElButton>
      <ElButton
        type="primary"
        :loading="saving"
        @click="handleSubmit"
      >
        {{ t('common.confirm') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { updateUserProfile } from '@/api/profile'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: []
}>()

const { t } = useI18n()
const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive({
  originalPassword: '',
  password: '',
  confirmPassword: '',
})

const rules: FormRules = {
  originalPassword: [{ required: true, message: t('profile.enterOriginalPassword'), trigger: 'blur' }],
  password: [
    { required: true, message: t('profile.enterNewPassword'), trigger: 'blur' },
    { min: 8, message: t('profile.passwordMinLength'), trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: t('profile.confirmNewPassword'), trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error(t('profile.passwordMismatch')))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

watch(
  () => props.modelValue,
  (val) => {
    if (!val) {
      form.originalPassword = ''
      form.password = ''
      form.confirmPassword = ''
      formRef.value?.clearValidate()
    }
  }
)

async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    await updateUserProfile({
      password: form.password,
      originalPassword: form.originalPassword,
    })
    ElMessage.success(t('profile.passwordChanged'))
    emit('changed')
    emit('update:modelValue', false)
  } catch {
    // 错误由 request 拦截器处理
  } finally {
    saving.value = false
  }
}
</script>
