<template>
  <ElDialog
    :model-value="modelValue"
    :title="t('profile.deleteAccount')"
    width="440px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="delete-dialog__body">
      <ElAlert
        :title="t('profile.deleteAccountWarning')"
        type="error"
        :closable="false"
        show-icon
      />
      <p class="delete-dialog__desc">
        {{ t('profile.deleteAccountDesc') }}
      </p>

      <ElForm
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent
      >
        <ElFormItem
          :label="t('profile.enterPasswordToConfirm')"
          prop="password"
        >
          <ElInput
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
          />
        </ElFormItem>
      </ElForm>
    </div>

    <template #footer>
      <ElButton @click="$emit('update:modelValue', false)">
        {{ t('common.cancel') }}
      </ElButton>
      <ElButton
        type="danger"
        :loading="deleting"
        @click="handleDelete"
      >
        {{ t('profile.confirmDelete') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { deleteUserAccount } from '@/api/profile'
import { useAuthStore } from '@/store/modules/auth'
import { useRouter } from 'vue-router'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const deleting = ref(false)

const form = reactive({
  password: '',
})

const rules: FormRules = {
  password: [{ required: true, message: t('profile.enterPassword'), trigger: 'blur' }],
}

watch(
  () => props.modelValue,
  (val) => {
    if (!val) {
      form.password = ''
      formRef.value?.clearValidate()
    }
  }
)

async function handleDelete(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  deleting.value = true
  try {
    await deleteUserAccount({ password: form.password })
    ElMessage.success(t('profile.accountDeleted'))
    authStore.clearAuthToken()
    emit('update:modelValue', false)
    router.push('/sign-in')
  } catch {
    // 错误由 request 拦截器处理
  } finally {
    deleting.value = false
  }
}
</script>

<style scoped>
.delete-dialog__body {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);
}

.delete-dialog__desc {
  margin: 0;
  font-size: var(--ys-font-size-base);
  color: var(--el-text-color-secondary);
}
</style>
