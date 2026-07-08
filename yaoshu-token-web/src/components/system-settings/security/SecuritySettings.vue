<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import SettingsPageLayout from '../SettingsPageLayout.vue'
import SettingsSection from '../SettingsSection.vue'
import SettingsFormActions from '../SettingsFormActions.vue'
import { useSystemOptions, getOptionValue } from '@/composables/system-settings/useSystemOptions'
import { useUpdateOption } from '@/composables/system-settings/useUpdateOption'

const { t } = useI18n()
const { data, loading, fetchOptions } = useSystemOptions()
const { saving, save } = useUpdateOption()

interface SecurityForm {
  ModelRequestRateLimitEnabled: boolean
  ModelRequestRateLimitCount: number
  ModelRequestRateLimitSuccessCount: number
  ModelRequestRateLimitDurationMinutes: number
  ModelRequestRateLimitGroup: string
  CheckSensitiveEnabled: boolean
  CheckSensitiveOnPromptEnabled: boolean
  SensitiveWords: string
  'fetch_setting.enable_ssrf_protection': boolean
  'fetch_setting.allow_private_ip': boolean
  'fetch_setting.domain_list': string
  'fetch_setting.ip_list': string
  'fetch_setting.domain_filter_mode': boolean
  'fetch_setting.ip_filter_mode': boolean
  'fetch_setting.allowed_ports': string
  'fetch_setting.apply_ip_filter_for_domain': boolean
}

const defaults: SecurityForm = {
  ModelRequestRateLimitEnabled: false,
  ModelRequestRateLimitCount: 60,
  ModelRequestRateLimitSuccessCount: 60,
  ModelRequestRateLimitDurationMinutes: 3,
  ModelRequestRateLimitGroup: 'default',
  CheckSensitiveEnabled: false,
  CheckSensitiveOnPromptEnabled: false,
  SensitiveWords: '',
  'fetch_setting.enable_ssrf_protection': false,
  'fetch_setting.allow_private_ip': false,
  'fetch_setting.domain_list': '',
  'fetch_setting.ip_list': '',
  'fetch_setting.domain_filter_mode': true,
  'fetch_setting.ip_filter_mode': true,
  'fetch_setting.allowed_ports': '',
  'fetch_setting.apply_ip_filter_for_domain': false,
}

const form = ref<SecurityForm>({ ...defaults })
const initial = ref<SecurityForm>({ ...defaults })
const dirty = ref(false)

function loadForm() {
  const parsed = getOptionValue(data.value ?? [], defaults)
  form.value = { ...parsed }
  initial.value = { ...parsed }
  dirty.value = false
}

async function handleSave() {
  const keys = Object.keys(form.value) as Array<keyof SecurityForm>
  for (const key of keys) {
    if (form.value[key] !== initial.value[key]) {
      const ok = await save({ key: key as string, value: form.value[key] })
      if (!ok) return
    }
  }
  await fetchOptions()
  loadForm()
}

function handleReset() {
  form.value = { ...initial.value }
  dirty.value = false
}

onMounted(async () => {
  await fetchOptions()
  loadForm()
})
</script>

<template>
  <SettingsPageLayout
    :title="t('systemSettings.tabs.security')"
    :loading="loading"
  >
    <SettingsSection
      id="rate-limit"
      :title="t('systemSettings.security.rateLimit')"
      :default-expanded="true"
      :dirty="dirty"
    >
      <ElForm label-width="220px">
        <ElFormItem :label="t('systemSettings.security.rateLimitEnabled')">
          <ElSwitch
            v-model="form.ModelRequestRateLimitEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.security.rateLimitCount')">
          <ElInputNumber
            v-model="form.ModelRequestRateLimitCount"
            :min="1"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.security.rateLimitSuccessCount')">
          <ElInputNumber
            v-model="form.ModelRequestRateLimitSuccessCount"
            :min="1"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.security.rateLimitDuration')">
          <ElInputNumber
            v-model="form.ModelRequestRateLimitDurationMinutes"
            :min="1"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.security.rateLimitGroup')">
          <ElInput
            v-model="form.ModelRequestRateLimitGroup"
            @input="dirty = true"
          />
        </ElFormItem>
      </ElForm>
      <SettingsFormActions
        :saving="saving"
        :dirty="dirty"
        @save="handleSave"
        @reset="handleReset"
      />
    </SettingsSection>

    <SettingsSection
      id="sensitive-words"
      :title="t('systemSettings.security.sensitiveWords')"
      :dirty="dirty"
    >
      <ElForm label-width="220px">
        <ElFormItem :label="t('systemSettings.security.checkSensitive')">
          <ElSwitch
            v-model="form.CheckSensitiveEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.security.checkOnPrompt')">
          <ElSwitch
            v-model="form.CheckSensitiveOnPromptEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.security.sensitiveWordsList')">
          <ElInput
            v-model="form.SensitiveWords"
            type="textarea"
            :rows="6"
            @input="dirty = true"
          />
        </ElFormItem>
      </ElForm>
      <SettingsFormActions
        :saving="saving"
        :dirty="dirty"
        @save="handleSave"
        @reset="handleReset"
      />
    </SettingsSection>

    <SettingsSection
      id="ssrf"
      :title="t('systemSettings.security.ssrf')"
      :dirty="dirty"
    >
      <ElForm label-width="220px">
        <ElFormItem :label="t('systemSettings.security.ssrfProtection')">
          <ElSwitch
            v-model="form['fetch_setting.enable_ssrf_protection']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.security.allowPrivateIp')">
          <ElSwitch
            v-model="form['fetch_setting.allow_private_ip']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.security.domainList')">
          <ElInput
            v-model="form['fetch_setting.domain_list']"
            type="textarea"
            :rows="4"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.security.ipList')">
          <ElInput
            v-model="form['fetch_setting.ip_list']"
            type="textarea"
            :rows="4"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.security.domainFilterMode')">
          <ElSwitch
            v-model="form['fetch_setting.domain_filter_mode']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.security.ipFilterMode')">
          <ElSwitch
            v-model="form['fetch_setting.ip_filter_mode']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.security.allowedPorts')">
          <ElInput
            v-model="form['fetch_setting.allowed_ports']"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.security.applyIpFilterForDomain')">
          <ElSwitch
            v-model="form['fetch_setting.apply_ip_filter_for_domain']"
            @change="dirty = true"
          />
        </ElFormItem>
      </ElForm>
      <SettingsFormActions
        :saving="saving"
        :dirty="dirty"
        @save="handleSave"
        @reset="handleReset"
      />
    </SettingsSection>
  </SettingsPageLayout>
</template>
