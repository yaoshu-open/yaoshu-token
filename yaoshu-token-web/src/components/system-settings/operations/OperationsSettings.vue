<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import SettingsPageLayout from '../SettingsPageLayout.vue'
import SettingsSection from '../SettingsSection.vue'
import SettingsFormActions from '../SettingsFormActions.vue'
import { useSystemOptions, getOptionValue } from '@/composables/system-settings/useSystemOptions'
import { useUpdateOption } from '@/composables/system-settings/useUpdateOption'
import { getStatus } from '@/api/system'

const { t } = useI18n()
const { data, loading, fetchOptions } = useSystemOptions()
const { saving, save } = useUpdateOption()

interface OperationsForm {
  RetryTimes: number
  DefaultCollapseSidebar: boolean
  DemoSiteEnabled: boolean
  SelfUseModeEnabled: boolean
  ChannelDisableThreshold: string
  QuotaRemindThreshold: string
  AutomaticDisableChannelEnabled: boolean
  AutomaticEnableChannelEnabled: boolean
  AutomaticDisableKeywords: string
  AutomaticDisableStatusCodes: string
  AutomaticRetryStatusCodes: string
  'monitor_setting.auto_test_channel_enabled': boolean
  'monitor_setting.auto_test_channel_minutes': number
  SMTPServer: string
  SMTPPort: string
  SMTPAccount: string
  SMTPFrom: string
  SMTPToken: string
  SMTPSSLEnabled: boolean
  SMTPForceAuthLogin: boolean
  WorkerUrl: string
  WorkerValidKey: string
  WorkerAllowHttpImageRequestEnabled: boolean
  LogConsumeEnabled: boolean
  'performance_setting.disk_cache_enabled': boolean
  'performance_setting.monitor_enabled': boolean
  'performance_setting.disk_cache_threshold_mb': number
  'performance_setting.disk_cache_max_size_mb': number
  'performance_setting.disk_cache_path': string
  'performance_setting.monitor_cpu_threshold': number
  'performance_setting.monitor_memory_threshold': number
  'performance_setting.monitor_disk_threshold': number
  'perf_metrics_setting.enabled': boolean
  'perf_metrics_setting.flush_interval': number
  'perf_metrics_setting.bucket_time': string
  'perf_metrics_setting.retention_days': number
}

const defaults: OperationsForm = {
  RetryTimes: 3,
  DefaultCollapseSidebar: false,
  DemoSiteEnabled: false,
  SelfUseModeEnabled: false,
  ChannelDisableThreshold: '',
  QuotaRemindThreshold: '',
  AutomaticDisableChannelEnabled: false,
  AutomaticEnableChannelEnabled: false,
  AutomaticDisableKeywords: '',
  AutomaticDisableStatusCodes: '',
  AutomaticRetryStatusCodes: '',
  'monitor_setting.auto_test_channel_enabled': false,
  'monitor_setting.auto_test_channel_minutes': 60,
  SMTPServer: '',
  SMTPPort: '465',
  SMTPAccount: '',
  SMTPFrom: '',
  SMTPToken: '',
  SMTPSSLEnabled: true,
  SMTPForceAuthLogin: false,
  WorkerUrl: '',
  WorkerValidKey: '',
  WorkerAllowHttpImageRequestEnabled: false,
  LogConsumeEnabled: true,
  'performance_setting.disk_cache_enabled': false,
  'performance_setting.monitor_enabled': false,
  'performance_setting.disk_cache_threshold_mb': 10,
  'performance_setting.disk_cache_max_size_mb': 1024,
  'performance_setting.disk_cache_path': '',
  'performance_setting.monitor_cpu_threshold': 90,
  'performance_setting.monitor_memory_threshold': 90,
  'performance_setting.monitor_disk_threshold': 95,
  'perf_metrics_setting.enabled': true,
  'perf_metrics_setting.flush_interval': 5,
  'perf_metrics_setting.bucket_time': 'hour',
  'perf_metrics_setting.retention_days': 0,
}

const form = ref<OperationsForm>({ ...defaults })
const initial = ref<OperationsForm>({ ...defaults })
const dirty = ref(false)
const currentVersion = ref('')
const startTime = ref(0)

function loadForm() {
  const parsed = getOptionValue(data.value ?? [], defaults)
  form.value = { ...parsed }
  initial.value = { ...parsed }
  dirty.value = false
}

async function handleSave() {
  const keys = Object.keys(form.value) as Array<keyof OperationsForm>
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
  try {
    const status = await getStatus()
    currentVersion.value = status.version ?? ''
    startTime.value = status.startTime ?? 0
  } catch {
    // ignore status fetch errors
  }
})
</script>

<template>
  <SettingsPageLayout
    :title="t('systemSettings.tabs.operations')"
    :loading="loading"
  >
    <SettingsSection
      id="behavior"
      :title="t('systemSettings.operations.behavior')"
      :default-expanded="true"
      :dirty="dirty"
    >
      <ElForm label-width="220px">
        <ElFormItem :label="t('systemSettings.operations.retryTimes')">
          <ElInputNumber
            v-model="form.RetryTimes"
            :min="0"
            :max="10"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.collapseSidebar')">
          <ElSwitch
            v-model="form.DefaultCollapseSidebar"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.demoSite')">
          <ElSwitch
            v-model="form.DemoSiteEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.selfUseMode')">
          <ElSwitch
            v-model="form.SelfUseModeEnabled"
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
      id="monitoring"
      :title="t('systemSettings.operations.monitoring')"
      :dirty="dirty"
    >
      <ElForm label-width="220px">
        <ElFormItem :label="t('systemSettings.operations.channelDisableThreshold')">
          <ElInput
            v-model="form.ChannelDisableThreshold"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.quotaRemindThreshold')">
          <ElInput
            v-model="form.QuotaRemindThreshold"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.autoDisableChannel')">
          <ElSwitch
            v-model="form.AutomaticDisableChannelEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.autoEnableChannel')">
          <ElSwitch
            v-model="form.AutomaticEnableChannelEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.disableKeywords')">
          <ElInput
            v-model="form.AutomaticDisableKeywords"
            type="textarea"
            :rows="3"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.disableStatusCodes')">
          <ElInput
            v-model="form.AutomaticDisableStatusCodes"
            type="textarea"
            :rows="3"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.retryStatusCodes')">
          <ElInput
            v-model="form.AutomaticRetryStatusCodes"
            type="textarea"
            :rows="3"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.autoTestChannel')">
          <ElSwitch
            v-model="form['monitor_setting.auto_test_channel_enabled']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.autoTestChannelMinutes')">
          <ElInputNumber
            v-model="form['monitor_setting.auto_test_channel_minutes']"
            :min="1"
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
      id="email"
      :title="t('systemSettings.operations.email')"
      :dirty="dirty"
    >
      <ElForm label-width="220px">
        <ElFormItem :label="t('systemSettings.operations.smtpServer')">
          <ElInput
            v-model="form.SMTPServer"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.smtpPort')">
          <ElInput
            v-model="form.SMTPPort"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.smtpAccount')">
          <ElInput
            v-model="form.SMTPAccount"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.smtpFrom')">
          <ElInput
            v-model="form.SMTPFrom"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.smtpToken')">
          <ElInput
            v-model="form.SMTPToken"
            type="password"
            show-password
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.smtpSsl')">
          <ElSwitch
            v-model="form.SMTPSSLEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.smtpForceAuthLogin')">
          <ElSwitch
            v-model="form.SMTPForceAuthLogin"
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
      id="worker"
      :title="t('systemSettings.operations.worker')"
      :dirty="dirty"
    >
      <ElForm label-width="220px">
        <ElFormItem :label="t('systemSettings.operations.workerUrl')">
          <ElInput
            v-model="form.WorkerUrl"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.workerValidKey')">
          <ElInput
            v-model="form.WorkerValidKey"
            type="password"
            show-password
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.workerAllowHttpImage')">
          <ElSwitch
            v-model="form.WorkerAllowHttpImageRequestEnabled"
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
      id="logs"
      :title="t('systemSettings.operations.logs')"
      :dirty="dirty"
    >
      <ElForm label-width="220px">
        <ElFormItem :label="t('systemSettings.operations.logConsume')">
          <ElSwitch
            v-model="form.LogConsumeEnabled"
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
      id="performance"
      :title="t('systemSettings.operations.performance')"
      :dirty="dirty"
    >
      <ElForm label-width="220px">
        <ElFormItem :label="t('systemSettings.operations.diskCache')">
          <ElSwitch
            v-model="form['performance_setting.disk_cache_enabled']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.perfMonitor')">
          <ElSwitch
            v-model="form['performance_setting.monitor_enabled']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.perfMetrics')">
          <ElSwitch
            v-model="form['perf_metrics_setting.enabled']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.flushInterval')">
          <ElInputNumber
            v-model="form['perf_metrics_setting.flush_interval']"
            :min="1"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.diskCacheThreshold')">
          <ElInputNumber
            v-model="form['performance_setting.disk_cache_threshold_mb']"
            :min="0"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.diskCacheMaxSize')">
          <ElInputNumber
            v-model="form['performance_setting.disk_cache_max_size_mb']"
            :min="0"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.diskCachePath')">
          <ElInput
            v-model="form['performance_setting.disk_cache_path']"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.monitorCpuThreshold')">
          <ElInputNumber
            v-model="form['performance_setting.monitor_cpu_threshold']"
            :min="0"
            :max="100"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.monitorMemoryThreshold')">
          <ElInputNumber
            v-model="form['performance_setting.monitor_memory_threshold']"
            :min="0"
            :max="100"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.monitorDiskThreshold')">
          <ElInputNumber
            v-model="form['performance_setting.monitor_disk_threshold']"
            :min="0"
            :max="100"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.bucketTime')">
          <ElSelect
            v-model="form['perf_metrics_setting.bucket_time']"
            @change="dirty = true"
          >
            <ElOption
              label="Hour"
              value="hour"
            />
            <ElOption
              label="Minute"
              value="minute"
            />
            <ElOption
              label="5 Minutes"
              value="5min"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.operations.retentionDays')">
          <ElInputNumber
            v-model="form['perf_metrics_setting.retention_days']"
            :min="0"
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
      id="update-checker"
      :title="t('systemSettings.operations.updateChecker')"
      :dirty="false"
    >
      <ElDescriptions
        :column="1"
        border
      >
        <ElDescriptionsItem :label="t('systemSettings.operations.currentVersion')">
          {{ currentVersion || '-' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem :label="t('systemSettings.operations.startTime')">
          {{ startTime ? new Date(startTime * 1000).toLocaleString() : '-' }}
        </ElDescriptionsItem>
      </ElDescriptions>
    </SettingsSection>
  </SettingsPageLayout>
</template>
