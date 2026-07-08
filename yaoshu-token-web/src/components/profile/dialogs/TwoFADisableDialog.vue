<template>
  <ElDialog
    :model-value="modelValue"
    :title="t('profile.disableTwofa')"
    width="440px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <ElAlert
      :title="t('profile.disableTwofaWarning')"
      type="warning"
      :closable="false"
      show-icon
      class="twofa-disable__alert"
    />
    <ElForm
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent
    >
      <ElFormItem
        :label="t('profile.enterPassword')"
        prop="password"
      >
        <ElInput
          v-model="form.password"
          type="password"
          show-password
          autocomplete="current-password"
          @keyup.enter="handleDisable"
        />
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton @click="$emit('update:modelValue', false)">
        {{ t('common.cancel') }}
      </ElButton>
      <ElButton
        type="danger"
        :loading="disabling"
        @click="handleDisable"
      >
        {{ t('profile.confirmDisable') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useTwoFA } from '@/composables/profile/useTwoFA'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  disabled: []
}>()

const { t } = useI18n()
const { disabling, disable } = useTwoFA(true)
const formRef = ref<FormInstance>()

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

async function handleDisable(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  await disable(form.password)
  ElMessage.success(t('profile.twofaDisabled'))
  emit('disabled')
  emit('update:modelValue', false)
}
</script>

<style scoped>
.twofa-disable__alert {
  margin-bottom: var(--ys-spacing-4);
}
</style>
