<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import SettingsSection from '../SettingsSection.vue'
import {
  AUTH_STYLE_OPTIONS,
  OAUTH_PRESETS,
  createCustomOAuthProvider,
  deleteCustomOAuthProvider,
  discoverOidcEndpoints,
  getCustomOAuthProviders,
  updateCustomOAuthProvider,
} from '@/api/custom-oauth'
import type { CustomOAuthProvider } from '@/api/custom-oauth/types'

const { t } = useI18n()

const listLoading = ref(false)
const providers = ref<CustomOAuthProvider[]>([])

function createEmptyForm(): Omit<CustomOAuthProvider, 'id'> {
  return {
    name: '',
    slug: '',
    icon: '',
    enabled: false,
    clientId: '',
    clientSecret: '',
    authorizationEndpoint: '',
    tokenEndpoint: '',
    userInfoEndpoint: '',
    scopes: '',
    userIdField: '',
    usernameField: '',
    displayNameField: '',
    emailField: '',
    wellKnown: '',
    authStyle: 0,
    accessPolicy: '',
    accessDeniedMessage: '',
  }
}

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const form = reactive<Omit<CustomOAuthProvider, 'id'>>(createEmptyForm())
const submitting = ref(false)

const selectedPreset = ref<string>('')
const baseUrl = ref('')
const discovering = ref(false)

async function fetchList() {
  listLoading.value = true
  try {
    providers.value = await getCustomOAuthProviders()
  } catch {
    ElMessage.error(t('systemSettings.auth.customOAuth.loadFailed'))
  } finally {
    listLoading.value = false
  }
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  Object.assign(form, createEmptyForm())
  selectedPreset.value = ''
  baseUrl.value = ''
  dialogVisible.value = true
}

function openEdit(row: CustomOAuthProvider) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  const { id: _id, ...rest } = row
  void _id
  Object.assign(form, rest)
  selectedPreset.value = ''
  baseUrl.value = ''
  dialogVisible.value = true
}

async function handleToggleEnabled(row: CustomOAuthProvider) {
  try {
    const { id, ...rest } = row
    await updateCustomOAuthProvider(id, rest)
  } catch {
    row.enabled = !row.enabled
  }
}

function applyPreset(presetKey: string) {
  const preset = OAUTH_PRESETS.find((p) => p.key === presetKey)
  if (!preset) return
  const trimSlash = (s: string) => s.replace(/\/+$/, '')
  const joinUrl = (base: string, path: string) => {
    if (!base) return path
    return `${trimSlash(base)}${path.startsWith('/') ? path : `/${path}`}`
  }
  form.icon = preset.icon
  form.scopes = preset.scopes
  form.userIdField = preset.userIdField
  form.usernameField = preset.usernameField
  form.displayNameField = preset.displayNameField
  form.emailField = preset.emailField
  if (preset.needsBaseUrl && baseUrl.value) {
    form.authorizationEndpoint = joinUrl(baseUrl.value, preset.authorizationEndpoint)
    form.tokenEndpoint = joinUrl(baseUrl.value, preset.tokenEndpoint)
    form.userInfoEndpoint = joinUrl(baseUrl.value, preset.userInfoEndpoint)
  } else {
    form.authorizationEndpoint = preset.authorizationEndpoint
    form.tokenEndpoint = preset.tokenEndpoint
    form.userInfoEndpoint = preset.userInfoEndpoint
  }
  if (!form.name) form.name = preset.name
}

async function handleDiscovery() {
  const url = form.wellKnown.trim()
  if (!url) {
    ElMessage.warning(t('systemSettings.auth.customOAuth.discoveryNeedUrl'))
    return
  }
  if (!/^https?:\/\//i.test(url)) {
    ElMessage.warning(t('systemSettings.auth.customOAuth.discoveryInvalidUrl'))
    return
  }
  discovering.value = true
  try {
    const res = await discoverOidcEndpoints(url)
    const discovery = res.data?.discovery
    if (discovery) {
      if (discovery.authorizationEndpoint) form.authorizationEndpoint = discovery.authorizationEndpoint
      if (discovery.tokenEndpoint) form.tokenEndpoint = discovery.tokenEndpoint
      if (discovery.userInfoEndpoint) form.userInfoEndpoint = discovery.userInfoEndpoint
      if (discovery.scopesSupported?.length) form.scopes = discovery.scopesSupported.join(' ')
      ElMessage.success(t('systemSettings.auth.customOAuth.discoverySuccess'))
    } else {
      ElMessage.error(t('systemSettings.auth.customOAuth.discoveryFailed'))
    }
  } catch {
    ElMessage.error(t('systemSettings.auth.customOAuth.discoveryFailed'))
  } finally {
    discovering.value = false
  }
}

async function handleSubmit() {
  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createCustomOAuthProvider({ ...form })
    } else if (editingId.value !== null) {
      await updateCustomOAuthProvider(editingId.value, { ...form })
    }
    ElMessage.success(t('systemSettings.auth.customOAuth.saveSuccess'))
    dialogVisible.value = false
    await fetchList()
  } catch {
    // 错误提示由拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: CustomOAuthProvider) {
  try {
    await ElMessageBox.confirm(
      t('systemSettings.auth.customOAuth.deleteConfirm', { name: row.name }),
      t('common.confirm'),
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await deleteCustomOAuthProvider(row.id)
    ElMessage.success(t('systemSettings.auth.customOAuth.deleteSuccess'))
    await fetchList()
  } catch {
    // 错误提示由拦截器统一处理
  }
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <SettingsSection
    id="custom-oauth"
    :title="t('systemSettings.auth.customOAuth.title')"
  >
    <p class="custom-oauth__desc">
      {{ t('systemSettings.auth.customOAuth.description') }}
    </p>
    <div class="custom-oauth__toolbar">
      <ElButton
        type="primary"
        @click="openCreate"
      >
        {{ t('systemSettings.auth.customOAuth.addProvider') }}
      </ElButton>
    </div>
    <ElTable
      v-loading="listLoading"
      :data="providers"
      stripe
    >
      <ElTableColumn
        :label="t('systemSettings.auth.customOAuth.providerName')"
        prop="name"
      />
      <ElTableColumn
        :label="t('systemSettings.auth.customOAuth.providerSlug')"
        prop="slug"
      />
      <ElTableColumn
        :label="t('systemSettings.auth.customOAuth.providerEnabled')"
        width="100"
      >
        <template #default="{ row }">
          <ElSwitch
            v-model="row.enabled"
            @change="handleToggleEnabled(row as CustomOAuthProvider)"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn
        :label="t('systemSettings.auth.customOAuth.clientId')"
        prop="clientId"
      />
      <ElTableColumn
        :label="t('common.action')"
        width="160"
      >
        <template #default="{ row }">
          <ElButton
            link
            type="primary"
            @click="openEdit(row as CustomOAuthProvider)"
          >
            {{ t('systemSettings.auth.customOAuth.editProvider') }}
          </ElButton>
          <ElButton
            link
            type="danger"
            @click="handleDelete(row as CustomOAuthProvider)"
          >
            {{ t('common.delete') }}
          </ElButton>
        </template>
      </ElTableColumn>
      <template #empty>
        {{ t('systemSettings.auth.customOAuth.empty') }}
      </template>
    </ElTable>

    <ElDialog
      v-model="dialogVisible"
      :title="dialogMode === 'create'
        ? t('systemSettings.auth.customOAuth.createProvider')
        : t('systemSettings.auth.customOAuth.editProvider')"
      width="680px"
      :close-on-click-modal="false"
    >
      <ElForm label-width="170px">
        <!-- 快速设置（仅新增时） -->
        <template v-if="dialogMode === 'create'">
          <ElDivider content-position="left">
            {{ t('systemSettings.auth.customOAuth.quickSetup') }}
          </ElDivider>
          <ElFormItem :label="t('systemSettings.auth.customOAuth.presetTemplate')">
            <ElSelect
              v-model="selectedPreset"
              :placeholder="t('systemSettings.auth.customOAuth.selectPreset')"
              clearable
              style="width: 100%"
              @change="applyPreset"
            >
              <ElOption
                v-for="preset in OAUTH_PRESETS"
                :key="preset.key"
                :label="preset.name"
                :value="preset.key"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="Base URL">
            <ElInput
              v-model="baseUrl"
              placeholder="https://git.example.com"
            />
          </ElFormItem>
        </template>

        <!-- 基础信息 -->
        <ElDivider content-position="left">
          {{ t('systemSettings.auth.customOAuth.basicInfo') }}
        </ElDivider>
        <ElFormItem :label="t('systemSettings.auth.customOAuth.providerEnabled')">
          <ElSwitch v-model="form.enabled" />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.customOAuth.providerName')">
          <ElInput v-model="form.name" />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.customOAuth.providerSlug')">
          <ElInput v-model="form.slug" />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.customOAuth.providerIcon')">
          <ElInput v-model="form.icon" />
        </ElFormItem>

        <!-- 凭证 -->
        <ElDivider content-position="left">
          {{ t('systemSettings.auth.customOAuth.credentials') }}
        </ElDivider>
        <ElFormItem :label="t('systemSettings.auth.customOAuth.clientId')">
          <ElInput v-model="form.clientId" />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.customOAuth.clientSecret')">
          <ElInput
            v-model="form.clientSecret"
            type="password"
            show-password
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.customOAuth.authStyle')">
          <ElSelect
            v-model="form.authStyle"
            style="width: 100%"
          >
            <ElOption
              v-for="opt in AUTH_STYLE_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>

        <!-- 端点 -->
        <ElDivider content-position="left">
          {{ t('systemSettings.auth.customOAuth.endpoints') }}
        </ElDivider>
        <ElFormItem :label="t('systemSettings.auth.customOAuth.wellKnown')">
          <div class="custom-oauth__discovery-row">
            <ElInput v-model="form.wellKnown" />
            <ElButton
              :loading="discovering"
              @click="handleDiscovery"
            >
              {{ discovering
                ? t('systemSettings.auth.customOAuth.discovering')
                : t('systemSettings.auth.customOAuth.autoDiscover') }}
            </ElButton>
          </div>
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.customOAuth.authorizationEndpoint')">
          <ElInput v-model="form.authorizationEndpoint" />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.customOAuth.tokenEndpoint')">
          <ElInput v-model="form.tokenEndpoint" />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.customOAuth.userInfoEndpoint')">
          <ElInput v-model="form.userInfoEndpoint" />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.customOAuth.scopes')">
          <ElInput v-model="form.scopes" />
        </ElFormItem>

        <!-- 字段映射 -->
        <ElDivider content-position="left">
          {{ t('systemSettings.auth.customOAuth.fieldMapping') }}
        </ElDivider>
        <ElFormItem :label="t('systemSettings.auth.customOAuth.userIdField')">
          <ElInput v-model="form.userIdField" />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.customOAuth.usernameField')">
          <ElInput v-model="form.usernameField" />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.customOAuth.displayNameField')">
          <ElInput v-model="form.displayNameField" />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.customOAuth.emailField')">
          <ElInput v-model="form.emailField" />
        </ElFormItem>

        <!-- 高级 -->
        <ElDivider content-position="left">
          {{ t('systemSettings.auth.customOAuth.advanced') }}
        </ElDivider>
        <ElFormItem :label="t('systemSettings.auth.customOAuth.accessPolicy')">
          <ElInput
            v-model="form.accessPolicy"
            type="textarea"
            :rows="3"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.customOAuth.accessDeniedMessage')">
          <ElInput v-model="form.accessDeniedMessage" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">
          {{ t('common.cancel') }}
        </ElButton>
        <ElButton
          type="primary"
          :loading="submitting"
          @click="handleSubmit"
        >
          {{ t('common.confirm') }}
        </ElButton>
      </template>
    </ElDialog>
  </SettingsSection>
</template>

<style scoped lang="scss">
.custom-oauth__desc {
  margin: 0 0 var(--ys-spacing-3);
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-secondary);
}

.custom-oauth__toolbar {
  margin-bottom: var(--ys-spacing-3);
}

.custom-oauth__discovery-row {
  display: flex;
  gap: var(--ys-spacing-2);
  width: 100%;
}
</style>
