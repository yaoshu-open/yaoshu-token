<script setup lang="ts">
/**
 * 部署重命名 Dialog (MD-C4 rename)。
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { renameDeployment } from '@/api/deployment'
import type { DeploymentListItem } from '@/api/deployment/types'

const props = defineProps<{
  modelValue: boolean
  deployment: DeploymentListItem | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'renamed'): void
}>()

const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = ref({ name: '' })

const rules = computed<FormRules>(() => ({
  name: [
    { required: true, message: t('deployment.rename.nameRequired'), trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9-_\u4e00-\u9fa5]+$/,
      message: t('deployment.rename.namePattern'),
      trigger: 'blur'
    }
  ]
}))

// 弹窗打开时用当前部署名初始化
watch(visible, (v) => {
  if (v && props.deployment) {
    form.value.name = props.deployment.deployment_name || props.deployment.container_name || ''
  }
})

async function handleSubmit(): Promise<void> {
  if (!props.deployment || !formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await renameDeployment(props.deployment.id, form.value.name)
    ElMessage.success(t('deployment.rename.success'))
    emit('renamed')
    visible.value = false
  } catch {
    ElMessage.error(t('deployment.rename.failed'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="t('deployment.rename.title')"
    width="480px"
    :close-on-click-modal="false"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
    >
      <el-form-item
        :label="t('deployment.rename.label')"
        prop="name"
      >
        <el-input
          v-model="form.name"
          :placeholder="t('deployment.rename.placeholder')"
          maxlength="60"
          clearable
        />
      </el-form-item>
      <div
        v-if="deployment"
        class="rename-dialog__info"
      >
        <span>{{ t('deployment.list.id') }}:</span>
        <code>{{ deployment.id }}</code>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="submitting"
        @click="handleSubmit"
      >
        {{ t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.rename-dialog__info {
  margin-top: 8px;
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);

  code {
    margin-left: 4px;
  }
}
</style>
