<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import {
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  type FormInstance,
  type FormRules
} from 'element-plus'
import { ref } from 'vue'
import type { SetupFormData } from '@/views/setup/types'

const props = defineProps<{ formData: SetupFormData }>()
const emit = defineEmits<{ next: []; back: [] }>()
const { t } = useI18n()

const formRef = ref<FormInstance>()

const rules: FormRules = {
  username: [
    { required: true, message: t('setup.admin.usernameRequired'), trigger: 'blur' },
    { max: 12, message: t('setup.admin.usernameMax'), trigger: 'blur' }
  ],
  password: [
    { required: true, message: t('setup.admin.passwordRequired'), trigger: 'blur' },
    { min: 8, message: t('setup.admin.passwordMin'), trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: t('setup.admin.confirmRequired'), trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== props.formData.password) {
          callback(new Error(t('setup.admin.passwordMismatch')))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function handleNext(): Promise<void> {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    emit('next')
  } catch {
    // 校验失败由 ElForm 自身展示错误
  }
}
</script>

<template>
  <div class="setup-step">
    <el-card>
      <h3 class="setup-admin__title">
        {{ t('setup.admin.title') }}
      </h3>
      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-position="top"
      >
        <el-form-item
          :label="t('setup.admin.username')"
          prop="username"
        >
          <el-input
            v-model="formData.username"
            :placeholder="t('setup.admin.usernamePlaceholder')"
          />
        </el-form-item>
        <el-form-item
          :label="t('setup.admin.password')"
          prop="password"
        >
          <el-input
            v-model="formData.password"
            type="password"
            show-password
            :placeholder="t('setup.admin.passwordPlaceholder')"
          />
        </el-form-item>
        <el-form-item
          :label="t('setup.admin.confirmPassword')"
          prop="confirmPassword"
        >
          <el-input
            v-model="formData.confirmPassword"
            type="password"
            show-password
            :placeholder="t('setup.admin.confirmPlaceholder')"
          />
        </el-form-item>
      </el-form>
    </el-card>
    <div class="setup-step__actions">
      <el-button @click="emit('back')">
        {{ t('common.back') }}
      </el-button>
      <el-button
        type="primary"
        @click="handleNext"
      >
        {{ t('common.next') }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.setup-step__actions {
  display: flex;
  justify-content: space-between;
  margin-top: 24px;
}

.setup-admin__title {
  margin: 0 0 var(--ys-spacing-4);
  font-size: var(--ys-font-size-lg);
}
</style>
