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

interface ModelsForm {
  'global.pass_through_request_enabled': boolean
  'global.thinking_model_blacklist': string
  'general_setting.ping_interval_enabled': boolean
  'general_setting.ping_interval_seconds': number
  'gemini.safety_settings': string
  'gemini.version_settings': string
  'gemini.supported_imagine_models': string
  'gemini.thinking_adapter_enabled': boolean
  'gemini.thinking_adapter_budget_tokens_percentage': number
  'gemini.function_call_thought_signature_enabled': boolean
  'gemini.remove_function_response_id_enabled': boolean
  'claude.model_headers_settings': string
  'claude.default_max_tokens': string
  'claude.thinking_adapter_enabled': boolean
  'claude.thinking_adapter_budget_tokens_percentage': number
  'grok.violation_deduction_enabled': boolean
  'grok.violation_deduction_amount': number
  'channel_affinity_setting.enabled': boolean
  'channel_affinity_setting.switch_on_success': boolean
  'channel_affinity_setting.max_entries': number
  'channel_affinity_setting.default_ttl_seconds': number
  'channel_affinity_setting.keep_on_channel_disabled': boolean
  'channel_affinity_setting.rules': string
  'model_deployment.ionet.enabled': boolean
  'model_deployment.ionet.api_key': string
}

const defaults: ModelsForm = {
  'global.pass_through_request_enabled': false,
  'global.thinking_model_blacklist': '[]',
  'general_setting.ping_interval_enabled': false,
  'general_setting.ping_interval_seconds': 60,
  'gemini.safety_settings': '{}',
  'gemini.version_settings': '{}',
  'gemini.supported_imagine_models': '',
  'gemini.thinking_adapter_enabled': false,
  'gemini.thinking_adapter_budget_tokens_percentage': 0.5,
  'gemini.function_call_thought_signature_enabled': false,
  'gemini.remove_function_response_id_enabled': false,
  'claude.model_headers_settings': '{}',
  'claude.default_max_tokens': '8192',
  'claude.thinking_adapter_enabled': false,
  'claude.thinking_adapter_budget_tokens_percentage': 0.5,
  'grok.violation_deduction_enabled': true,
  'grok.violation_deduction_amount': 0.05,
  'channel_affinity_setting.enabled': false,
  'channel_affinity_setting.switch_on_success': true,
  'channel_affinity_setting.max_entries': 1000,
  'channel_affinity_setting.default_ttl_seconds': 3600,
  'channel_affinity_setting.keep_on_channel_disabled': false,
  'channel_affinity_setting.rules': '[]',
  'model_deployment.ionet.enabled': false,
  'model_deployment.ionet.api_key': '',
}

const form = ref<ModelsForm>({ ...defaults })
const initial = ref<ModelsForm>({ ...defaults })
const dirty = ref(false)

function loadForm() {
  const parsed = getOptionValue(data.value ?? [], defaults)
  form.value = { ...parsed }
  initial.value = { ...parsed }
  dirty.value = false
}

async function handleSave() {
  const keys = Object.keys(form.value) as Array<keyof ModelsForm>
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
    :title="t('systemSettings.tabs.models')"
    :loading="loading"
  >
    <SettingsSection
      id="global"
      :title="t('systemSettings.models.global')"
      :default-expanded="true"
      :dirty="dirty"
    >
      <ElForm label-width="220px">
        <ElFormItem :label="t('systemSettings.models.passThrough')">
          <ElSwitch
            v-model="form['global.pass_through_request_enabled']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.models.thinkingBlacklist')">
          <ElInput
            v-model="form['global.thinking_model_blacklist']"
            type="textarea"
            :rows="3"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.models.pingInterval')">
          <ElSwitch
            v-model="form['general_setting.ping_interval_enabled']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.models.pingSeconds')">
          <ElInputNumber
            v-model="form['general_setting.ping_interval_seconds']"
            :min="10"
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

    <SettingsSection
      id="gemini"
      :title="t('systemSettings.models.gemini')"
      :dirty="dirty"
    >
      <ElForm label-width="220px">
        <ElFormItem :label="t('systemSettings.models.safetySettings')">
          <ElInput
            v-model="form['gemini.safety_settings']"
            type="textarea"
            :rows="4"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.models.versionSettings')">
          <ElInput
            v-model="form['gemini.version_settings']"
            type="textarea"
            :rows="4"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.models.supportedImagineModels')">
          <ElInput
            v-model="form['gemini.supported_imagine_models']"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.models.thinkingAdapter')">
          <ElSwitch
            v-model="form['gemini.thinking_adapter_enabled']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.models.thinkingBudgetPercentage')">
          <ElInputNumber
            v-model="form['gemini.thinking_adapter_budget_tokens_percentage']"
            :min="0"
            :max="1"
            :precision="2"
            :step="0.05"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.models.functionCallThoughtSignature')">
          <ElSwitch
            v-model="form['gemini.function_call_thought_signature_enabled']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.models.removeFunctionResponseId')">
          <ElSwitch
            v-model="form['gemini.remove_function_response_id_enabled']"
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

    <SettingsSection
      id="claude"
      :title="t('systemSettings.models.claude')"
      :dirty="dirty"
    >
      <ElForm label-width="220px">
        <ElFormItem :label="t('systemSettings.models.modelHeaders')">
          <ElInput
            v-model="form['claude.model_headers_settings']"
            type="textarea"
            :rows="4"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.models.defaultMaxTokens')">
          <ElInput
            v-model="form['claude.default_max_tokens']"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.models.thinkingAdapter')">
          <ElSwitch
            v-model="form['claude.thinking_adapter_enabled']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.models.thinkingBudgetPercentage')">
          <ElInputNumber
            v-model="form['claude.thinking_adapter_budget_tokens_percentage']"
            :min="0"
            :max="1"
            :precision="2"
            :step="0.05"
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

    <SettingsSection
      id="grok"
      :title="t('systemSettings.models.grok')"
      :dirty="dirty"
    >
      <ElForm label-width="220px">
        <ElFormItem :label="t('systemSettings.models.violationDeduction')">
          <ElSwitch
            v-model="form['grok.violation_deduction_enabled']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.models.violationAmount')">
          <ElInputNumber
            v-model="form['grok.violation_deduction_amount']"
            :min="0"
            :precision="2"
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

    <SettingsSection
      id="channel-affinity"
      :title="t('systemSettings.models.channelAffinity')"
      :dirty="dirty"
    >
      <ElForm label-width="220px">
        <ElFormItem :label="t('systemSettings.models.affinityEnabled')">
          <ElSwitch
            v-model="form['channel_affinity_setting.enabled']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.models.switchOnSuccess')">
          <ElSwitch
            v-model="form['channel_affinity_setting.switch_on_success']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.models.maxEntries')">
          <ElInputNumber
            v-model="form['channel_affinity_setting.max_entries']"
            :min="100"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.models.defaultTtl')">
          <ElInputNumber
            v-model="form['channel_affinity_setting.default_ttl_seconds']"
            :min="60"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.models.keepOnChannelDisabled')">
          <ElSwitch
            v-model="form['channel_affinity_setting.keep_on_channel_disabled']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.models.affinityRules')">
          <ElInput
            v-model="form['channel_affinity_setting.rules']"
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
      id="ionet"
      :title="t('systemSettings.models.ionet')"
      :dirty="dirty"
    >
      <ElForm label-width="220px">
        <ElFormItem :label="t('systemSettings.models.ionetEnabled')">
          <ElSwitch
            v-model="form['model_deployment.ionet.enabled']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.models.ionetApiKey')">
          <ElInput
            v-model="form['model_deployment.ionet.api_key']"
            type="password"
            show-password
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
  </SettingsPageLayout>
</template>
