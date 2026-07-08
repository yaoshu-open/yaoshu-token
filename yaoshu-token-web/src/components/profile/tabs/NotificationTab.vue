<template>
  <div class="notif-tab">
    <!-- 通知方式 -->
    <div class="notif-tab__section">
      <label class="notif-tab__label">{{ t('profile.notificationMethod') }}</label>
      <ElRadioGroup
        v-model="form.notifyType"
        class="notif-tab__methods"
      >
        <ElRadioButton
          v-for="method in NOTIFICATION_METHODS"
          :key="method.value"
          :value="method.value"
        >
          {{ t(`profile.notifyMethod.${method.value}`) }}
        </ElRadioButton>
      </ElRadioGroup>
    </div>

    <!-- 预警阈值 -->
    <div class="notif-tab__section">
      <label class="notif-tab__label">{{ t('profile.quotaWarningThreshold') }}</label>
      <ElInputNumber
        v-model="form.quotaWarningThreshold"
        :min="0"
        :step="1000"
        controls-position="right"
        style="width: 240px;"
      />
      <p class="notif-tab__hint">
        {{ t('profile.thresholdHint') }}
      </p>
    </div>

    <!-- Email 配置 -->
    <div
      v-if="form.notifyType === 'email'"
      class="notif-tab__section"
    >
      <label class="notif-tab__label">{{ t('profile.notificationEmail') }}</label>
      <ElInput
        v-model="form.notificationEmail"
        type="email"
        :placeholder="t('profile.notificationEmailPlaceholder')"
        style="max-width: 400px;"
      />
    </div>

    <!-- Webhook 配置 -->
    <template v-if="form.notifyType === 'webhook'">
      <div class="notif-tab__section">
        <label class="notif-tab__label">{{ t('profile.webhookUrl') }}</label>
        <ElInput
          v-model="form.webhookUrl"
          type="url"
          placeholder="https://example.com/webhook"
          style="max-width: 400px;"
        />
      </div>
      <div class="notif-tab__section">
        <label class="notif-tab__label">{{ t('profile.webhookSecret') }}</label>
        <ElInput
          v-model="form.webhookSecret"
          type="password"
          show-password
          :placeholder="t('profile.webhookSecretPlaceholder')"
          style="max-width: 400px;"
        />
      </div>
    </template>

    <!-- Bark 配置 -->
    <div
      v-if="form.notifyType === 'bark'"
      class="notif-tab__section"
    >
      <label class="notif-tab__label">{{ t('profile.barkUrl') }}</label>
      <ElInput
        v-model="form.barkUrl"
        type="url"
        placeholder="https://api.day.app/yourkey/{{title}}/{{content}}"
        style="max-width: 400px;"
      />
      <p class="notif-tab__hint">
        {{ t('profile.barkUrlHint') }}
      </p>
    </div>

    <!-- Gotify 配置 -->
    <template v-if="form.notifyType === 'gotify'">
      <div class="notif-tab__section">
        <label class="notif-tab__label">{{ t('profile.gotifyUrl') }}</label>
        <ElInput
          v-model="form.gotifyUrl"
          type="url"
          placeholder="https://gotify.example.com"
          style="max-width: 400px;"
        />
      </div>
      <div class="notif-tab__section">
        <label class="notif-tab__label">{{ t('profile.gotifyToken') }}</label>
        <ElInput
          v-model="form.gotifyToken"
          type="password"
          show-password
          :placeholder="t('profile.gotifyTokenPlaceholder')"
          style="max-width: 400px;"
        />
      </div>
      <div class="notif-tab__section">
        <label class="notif-tab__label">{{ t('profile.gotifyPriority') }}</label>
        <ElInputNumber
          v-model="form.gotifyPriority"
          :min="0"
          :max="10"
          controls-position="right"
          style="width: 240px;"
        />
        <p class="notif-tab__hint">
          {{ t('profile.gotifyPriorityHint') }}
        </p>
      </div>
    </template>

    <ElDivider />

    <!-- 偏好设置 -->
    <div class="notif-tab__section">
      <h4 class="notif-tab__title">
        {{ t('profile.preferences') }}
      </h4>
      <p class="notif-tab__hint">
        {{ t('profile.preferencesDesc') }}
      </p>
    </div>

    <!-- 上游模型更新通知（仅管理员） -->
    <div
      v-if="isAdmin"
      class="notif-tab__switch-row"
    >
      <div class="notif-tab__switch-info">
        <label class="notif-tab__switch-label">{{ t('profile.upstreamModelNotify') }}</label>
        <p class="notif-tab__hint">
          {{ t('profile.upstreamModelNotifyDesc') }}
        </p>
      </div>
      <ElSwitch v-model="form.upstreamModelUpdateNotifyEnabled" />
    </div>

    <!-- 接受未定价模型 -->
    <div class="notif-tab__switch-row">
      <div class="notif-tab__switch-info">
        <label class="notif-tab__switch-label">{{ t('profile.acceptUnsetPrice') }}</label>
        <p class="notif-tab__hint">
          {{ t('profile.acceptUnsetPriceDesc') }}
        </p>
      </div>
      <ElSwitch v-model="form.acceptUnsetModelRatioModel" />
    </div>

    <!-- 记录 IP 日志 -->
    <div class="notif-tab__switch-row">
      <div class="notif-tab__switch-info">
        <label class="notif-tab__switch-label">{{ t('profile.recordIpLog') }}</label>
        <p class="notif-tab__hint">
          {{ t('profile.recordIpLogDesc') }}
        </p>
      </div>
      <ElSwitch v-model="form.recordIpLog" />
    </div>

    <!-- 保存按钮 -->
    <div class="notif-tab__footer">
      <ElButton
        type="primary"
        :loading="saving"
        @click="handleSave"
      >
        {{ saving ? t('common.saving') : t('common.save') }}
      </ElButton>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { updateUserSettings } from '@/api/profile'
import { DEFAULT_QUOTA_WARNING_THRESHOLD, NOTIFICATION_METHODS } from '@/api/profile/constants'
import { parseUserSettings } from '@/utils/profile'
import { ROLE } from '@/utils/roles'
import type { UserProfile, UpdateUserSettingsRequest } from '@/api/profile/types'

const props = defineProps<{
  profile: UserProfile | null
}>()

const emit = defineEmits<{
  update: []
}>()

const { t } = useI18n()
const saving = ref(false)

const isAdmin = computed(
  () => (props.profile?.role ?? 0) >= ROLE.ADMIN
)

const form = reactive<UpdateUserSettingsRequest>({
  notifyType: 'email',
  quotaWarningThreshold: DEFAULT_QUOTA_WARNING_THRESHOLD,
  notificationEmail: '',
  webhookUrl: '',
  webhookSecret: '',
  barkUrl: '',
  gotifyUrl: '',
  gotifyToken: '',
  gotifyPriority: 5,
  acceptUnsetModelRatioModel: false,
  recordIpLog: false,
  upstreamModelUpdateNotifyEnabled: false,
})

watch(
  () => props.profile,
  (val) => {
    if (!val?.setting) return
    const parsed = parseUserSettings(val.setting)
    form.notifyType = parsed.notifyType ?? 'email'
    form.quotaWarningThreshold =
      parsed.quotaWarningThreshold ?? DEFAULT_QUOTA_WARNING_THRESHOLD
    form.notificationEmail = parsed.notificationEmail ?? ''
    form.webhookUrl = parsed.webhookUrl ?? ''
    form.webhookSecret = parsed.webhookSecret ?? ''
    form.barkUrl = parsed.barkUrl ?? ''
    form.gotifyUrl = parsed.gotifyUrl ?? ''
    form.gotifyToken = parsed.gotifyToken ?? ''
    form.gotifyPriority = parsed.gotifyPriority ?? 5
    form.acceptUnsetModelRatioModel =
      parsed.acceptUnsetModelRatioModel ?? false
    form.recordIpLog = parsed.recordIpLog ?? false
    form.upstreamModelUpdateNotifyEnabled =
      parsed.upstreamModelUpdateNotifyEnabled ?? false
  },
  { immediate: true }
)

async function handleSave(): Promise<void> {
  saving.value = true
  try {
    await updateUserSettings(form)
    ElMessage.success(t('profile.settingsSaved'))
    emit('update')
  } catch {
    // 错误由 request 拦截器处理
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.notif-tab__section {
  margin-bottom: var(--ys-spacing-5);
}

.notif-tab__label {
  display: block;
  margin-bottom: var(--ys-spacing-2);
  font-size: var(--ys-font-size-base);
  font-weight: 500;
}

.notif-tab__hint {
  margin-top: var(--ys-spacing-1);
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}

.notif-tab__methods {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ys-spacing-2);
}

.notif-tab__title {
  margin: 0 0 var(--ys-spacing-1);
  font-size: var(--ys-font-size-base);
  font-weight: 600;
}

.notif-tab__switch-row {
  display: flex;
  gap: var(--ys-spacing-4);
  align-items: flex-start;
  justify-content: space-between;
  padding: var(--ys-spacing-3) var(--ys-spacing-4);
  margin-bottom: var(--ys-spacing-3);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--ys-radius-md);
}

.notif-tab__switch-info {
  flex: 1;
}

.notif-tab__switch-label {
  display: block;
  font-size: var(--ys-font-size-base);
  font-weight: 500;
}

.notif-tab__footer {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--ys-spacing-6);
}
</style>
