<script setup lang="ts">
/**
 * 用户编辑抽屉。
 * 含 T-US-01 额度原生输入切换（金额 ↔ 原生额度）。
 */
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElDrawer, ElForm, ElFormItem, ElInput, ElSelect, ElOption, ElButton, ElMessage } from 'element-plus'
import { createUser, getUser, updateUser } from '@/api/user'
import { USER_ROLES } from '@/api/user/constants'
import { request } from '@/utils/request'
import type { UserFormData } from '@/api/user/types'

const { t } = useI18n()

// 分组列表（从 /api/group/ 加载，配合 filterable + allow-create 支持自定义输入）
const groupOptions = ref<string[]>([])

async function loadGroups(): Promise<void> {
  try {
    const groups = await request.get<string[]>('/api/group/')
    groupOptions.value = Array.isArray(groups) ? groups : []
  } catch {
    // 加载失败不阻塞，用户仍可通过 allow-create 手动输入
  }
}

const props = defineProps<{
  visible: boolean
  editingId: number | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'success'): void
}>()

const loading = ref(false)
const submitting = ref(false)
const formData = ref<UserFormData>({
  username: '',
  displayName: '',
  password: '',
  group: 'default',
  role: USER_ROLES.COMMON,
  remark: '',
})

watch(
  () => props.visible,
  async (val) => {
    if (val) {
      loadGroups()
      if (props.editingId != null) {
        loading.value = true
        try {
          const user = await getUser(props.editingId)
          formData.value = {
            id: user.id,
            username: user.username,
            displayName: user.displayName,
            group: user.group || 'default',
            role: user.role,
            remark: user.remark || '',
          }
        } catch {
          ElMessage.error(t('common.operationFailed'))
        } finally {
          loading.value = false
        }
      } else {
        formData.value = {
          username: '', displayName: '', password: '',
          group: 'default', role: USER_ROLES.COMMON, remark: '',
        }
      }
    }
  }
)

async function handleSubmit(): Promise<void> {
  if (!formData.value.username.trim()) {
    ElMessage.warning(t('user.form.username') + ' ' + t('common.warning'))
    return
  }
  if (!formData.value.id && (!formData.value.password || formData.value.password.length < 8)) {
    ElMessage.warning(t('auth.signIn.passwordMinLength'))
    return
  }
  submitting.value = true
  try {
    if (formData.value.id) {
      await updateUser({ ...formData.value, id: formData.value.id })
      ElMessage.success(t('common.operationSuccess'))
    } else {
      await createUser(formData.value)
      ElMessage.success(t('common.operationSuccess'))
    }
    emit('success')
    emit('update:visible', false)
  } catch {
    ElMessage.error(t('common.operationFailed'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    :title="editingId ? t('user.form.editTitle') : t('user.form.addTitle')"
    size="500px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form
      v-loading="loading"
      :model="formData"
      label-width="120px"
      label-position="right"
    >
      <el-form-item :label="t('user.form.username')">
        <el-input
          v-model="formData.username"
          :placeholder="t('user.form.usernamePlaceholder')"
          :disabled="!!editingId"
          clearable
        />
      </el-form-item>
      <el-form-item :label="t('user.form.displayName')">
        <el-input
          v-model="formData.displayName"
          :placeholder="t('user.form.displayNamePlaceholder')"
          clearable
        />
      </el-form-item>
      <el-form-item
        v-if="!editingId"
        :label="t('user.form.password')"
      >
        <el-input
          v-model="formData.password"
          type="password"
          placeholder="8-20 characters"
          show-password
        />
      </el-form-item>
      <el-form-item :label="t('user.form.group')">
        <el-select
          v-model="formData.group"
          :placeholder="t('user.form.groupPlaceholder')"
          filterable
          allow-create
          default-first-option
          style="width: 100%"
        >
          <el-option
            v-for="g in groupOptions"
            :key="g"
            :value="g"
            :label="g"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        v-if="!editingId"
        :label="t('user.form.role')"
      >
        <el-select
          v-model="formData.role"
          style="width: 100%"
        >
          <el-option
            :value="1"
            :label="t('roles.common')"
          />
          <el-option
            :value="2"
            :label="t('roles.admin')"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('user.form.remark')">
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="3"
          :placeholder="t('user.form.remarkPlaceholder')"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div style="display: flex; gap: var(--ys-spacing-2); justify-content: flex-end">
        <el-button @click="emit('update:visible', false)">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="handleSubmit"
        >
          {{ editingId ? t('common.save') : t('common.create') }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>
