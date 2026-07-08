<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import SettingsPageLayout from '../SettingsPageLayout.vue'
import SettingsSection from '../SettingsSection.vue'
import SettingsFormActions from '../SettingsFormActions.vue'
import { useSystemOptions, getOptionValue } from '@/composables/system-settings/useSystemOptions'
import { useUpdateOption } from '@/composables/system-settings/useUpdateOption'
import ChatSettingsEditor from './ChatSettingsEditor.vue'

const { t } = useI18n()
const { data, loading, fetchOptions } = useSystemOptions()
const { saving, save } = useUpdateOption()

interface ContentForm {
  'console_setting.api_info': string
  'console_setting.api_info_enabled': boolean
  'console_setting.announcements': string
  'console_setting.announcements_enabled': boolean
  'console_setting.faq': string
  'console_setting.faq_enabled': boolean
  'console_setting.uptime_kuma_groups': string
  'console_setting.uptime_kuma_enabled': boolean
  DataExportEnabled: boolean
  DataExportDefaultTime: string
  DataExportInterval: number
  Chats: string
  DrawingEnabled: boolean
  MjNotifyEnabled: boolean
  MjAccountFilterEnabled: boolean
  MjForwardUrlEnabled: boolean
}

const defaults: ContentForm = {
  'console_setting.api_info': '[]',
  'console_setting.api_info_enabled': true,
  'console_setting.announcements': '[]',
  'console_setting.announcements_enabled': true,
  'console_setting.faq': '[]',
  'console_setting.faq_enabled': true,
  'console_setting.uptime_kuma_groups': '[]',
  'console_setting.uptime_kuma_enabled': false,
  DataExportEnabled: true,
  DataExportDefaultTime: 'hour',
  DataExportInterval: 10,
  Chats: '[]',
  DrawingEnabled: false,
  MjNotifyEnabled: false,
  MjAccountFilterEnabled: false,
  MjForwardUrlEnabled: false,
}

const form = ref<ContentForm>({ ...defaults })
const initial = ref<ContentForm>({ ...defaults })
const dirty = ref(false)

function loadForm() {
  const parsed = getOptionValue(data.value ?? [], defaults)
  form.value = { ...parsed }
  initial.value = { ...parsed }
  dirty.value = false
}

async function handleSave() {
  const keys = Object.keys(form.value) as Array<keyof ContentForm>
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
    :title="t('systemSettings.tabs.content')"
    :loading="loading"
  >
    <SettingsSection
      id="dashboard"
      :title="t('systemSettings.content.dashboard')"
      :default-expanded="true"
      :dirty="dirty"
    >
      <ElForm label-width="200px">
        <ElFormItem :label="t('systemSettings.content.dataExportEnabled')">
          <ElSwitch
            v-model="form.DataExportEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.content.dataExportInterval')">
          <ElInputNumber
            v-model="form.DataExportInterval"
            :min="1"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.content.dataExportDefaultTime')">
          <ElSelect
            v-model="form.DataExportDefaultTime"
            @change="dirty = true"
          >
            <ElOption
              label="Hour"
              value="hour"
            />
            <ElOption
              label="Day"
              value="day"
            />
            <ElOption
              label="Week"
              value="week"
            />
          </ElSelect>
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
      id="announcements"
      :title="t('systemSettings.content.announcements')"
      :dirty="dirty"
    >
      <ElForm label-width="200px">
        <ElFormItem :label="t('systemSettings.content.announcementsEnabled')">
          <ElSwitch
            v-model="form['console_setting.announcements_enabled']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.content.announcementsData')">
          <ElInput
            v-model="form['console_setting.announcements']"
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
      id="api-info"
      :title="t('systemSettings.content.apiInfo')"
      :dirty="dirty"
    >
      <ElForm label-width="200px">
        <ElFormItem :label="t('systemSettings.content.apiInfoEnabled')">
          <ElSwitch
            v-model="form['console_setting.api_info_enabled']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.content.apiInfoData')">
          <ElInput
            v-model="form['console_setting.api_info']"
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
      id="faq"
      :title="t('systemSettings.content.faq')"
      :dirty="dirty"
    >
      <ElForm label-width="200px">
        <ElFormItem :label="t('systemSettings.content.faqEnabled')">
          <ElSwitch
            v-model="form['console_setting.faq_enabled']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.content.faqData')">
          <ElInput
            v-model="form['console_setting.faq']"
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
      id="chat"
      :title="t('systemSettings.content.chat')"
      :dirty="dirty"
    >
      <ChatSettingsEditor
        v-model="form.Chats"
        @update:model-value="dirty = true"
      />
      <SettingsFormActions
        :saving="saving"
        :dirty="dirty"
        @save="handleSave"
        @reset="handleReset"
      />
    </SettingsSection>

    <SettingsSection
      id="drawing"
      :title="t('systemSettings.content.drawing')"
      :dirty="dirty"
    >
      <ElForm label-width="200px">
        <ElFormItem :label="t('systemSettings.content.drawingEnabled')">
          <ElSwitch
            v-model="form.DrawingEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.content.mjNotify')">
          <ElSwitch
            v-model="form.MjNotifyEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.content.mjAccountFilter')">
          <ElSwitch
            v-model="form.MjAccountFilterEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.content.mjForwardUrl')">
          <ElSwitch
            v-model="form.MjForwardUrlEnabled"
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
      id="uptime-kuma"
      :title="t('systemSettings.content.uptimeKuma')"
      :dirty="dirty"
    >
      <ElForm label-width="180px">
        <ElFormItem :label="t('systemSettings.content.uptimeKumaEnabled')">
          <ElSwitch
            v-model="form['console_setting.uptime_kuma_enabled']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.content.uptimeKumaGroups')">
          <ElInput
            v-model="form['console_setting.uptime_kuma_groups']"
            type="textarea"
            :rows="4"
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
