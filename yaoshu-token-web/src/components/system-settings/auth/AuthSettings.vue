<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import SettingsPageLayout from '../SettingsPageLayout.vue'
import SettingsSection from '../SettingsSection.vue'
import SettingsFormActions from '../SettingsFormActions.vue'
import { useSystemOptions, getOptionValue } from '@/composables/system-settings/useSystemOptions'
import { useUpdateOption } from '@/composables/system-settings/useUpdateOption'
import CustomOAuthSection from './CustomOAuthSection.vue'

const { t } = useI18n()
const { data, loading, fetchOptions } = useSystemOptions()
const { saving, save } = useUpdateOption()

interface AuthForm {
  PasswordLoginEnabled: boolean
  PasswordRegisterEnabled: boolean
  EmailVerificationEnabled: boolean
  RegisterEnabled: boolean
  EmailDomainRestrictionEnabled: boolean
  EmailDomainWhitelist: string
  GitHubOAuthEnabled: boolean
  GitHubClientId: string
  GitHubClientSecret: string
  'discord.enabled': boolean
  'discord.client_id': string
  'discord.client_secret': string
  'oidc.enabled': boolean
  'oidc.client_id': string
  'oidc.client_secret': string
  'oidc.well_known': string
  'oidc.authorization_endpoint': string
  'oidc.token_endpoint': string
  'oidc.user_info_endpoint': string
  TelegramOAuthEnabled: boolean
  TelegramBotToken: string
  TelegramBotName: string
  LinuxDOOAuthEnabled: boolean
  LinuxDOClientId: string
  LinuxDOClientSecret: string
  LinuxDOMinimumTrustLevel: string
  WeChatAuthEnabled: boolean
  WeChatServerAddress: string
  WeChatServerToken: string
  WeChatAccountQRCodeImageURL: string
  TurnstileCheckEnabled: boolean
  TurnstileSiteKey: string
  TurnstileSecretKey: string
  'passkey.enabled': boolean
  'passkey.rp_display_name': string
  'passkey.rp_id': string
  'passkey.origins': string
  'passkey.allow_insecure_origin': boolean
  'passkey.user_verification': string
  'passkey.attachment_preference': string
}

const defaults: AuthForm = {
  PasswordLoginEnabled: true,
  PasswordRegisterEnabled: true,
  EmailVerificationEnabled: false,
  RegisterEnabled: true,
  EmailDomainRestrictionEnabled: false,
  EmailDomainWhitelist: '',
  GitHubOAuthEnabled: false,
  GitHubClientId: '',
  GitHubClientSecret: '',
  'discord.enabled': false,
  'discord.client_id': '',
  'discord.client_secret': '',
  'oidc.enabled': false,
  'oidc.client_id': '',
  'oidc.client_secret': '',
  'oidc.well_known': '',
  'oidc.authorization_endpoint': '',
  'oidc.token_endpoint': '',
  'oidc.user_info_endpoint': '',
  TelegramOAuthEnabled: false,
  TelegramBotToken: '',
  TelegramBotName: '',
  LinuxDOOAuthEnabled: false,
  LinuxDOClientId: '',
  LinuxDOClientSecret: '',
  LinuxDOMinimumTrustLevel: '',
  WeChatAuthEnabled: false,
  WeChatServerAddress: '',
  WeChatServerToken: '',
  WeChatAccountQRCodeImageURL: '',
  TurnstileCheckEnabled: false,
  TurnstileSiteKey: '',
  TurnstileSecretKey: '',
  'passkey.enabled': false,
  'passkey.rp_display_name': '',
  'passkey.rp_id': '',
  'passkey.origins': '',
  'passkey.allow_insecure_origin': false,
  'passkey.user_verification': '',
  'passkey.attachment_preference': '',
}

const form = ref<AuthForm>({ ...defaults })
const initial = ref<AuthForm>({ ...defaults })
const dirty = ref(false)

function loadForm() {
  const parsed = getOptionValue(data.value ?? [], defaults)
  form.value = { ...parsed }
  initial.value = { ...parsed }
  dirty.value = false
}

async function handleSave() {
  const keys: Array<keyof AuthForm> = Object.keys(form.value) as Array<keyof AuthForm>
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
    :title="t('systemSettings.tabs.auth')"
    :loading="loading"
  >
    <SettingsSection
      id="basic-auth"
      :title="t('systemSettings.auth.basicAuth')"
      :default-expanded="true"
      :dirty="dirty"
    >
      <ElForm label-width="180px">
        <ElFormItem :label="t('systemSettings.auth.passwordLogin')">
          <ElSwitch
            v-model="form.PasswordLoginEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.passwordRegister')">
          <ElSwitch
            v-model="form.PasswordRegisterEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.emailVerification')">
          <ElSwitch
            v-model="form.EmailVerificationEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.registerEnabled')">
          <ElSwitch
            v-model="form.RegisterEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.domainRestriction')">
          <ElSwitch
            v-model="form.EmailDomainRestrictionEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.domainWhitelist')">
          <ElInput
            v-model="form.EmailDomainWhitelist"
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
      id="oauth"
      :title="t('systemSettings.auth.oauth')"
      :dirty="dirty"
    >
      <ElForm label-width="180px">
        <ElFormItem :label="t('systemSettings.auth.githubEnabled')">
          <ElSwitch
            v-model="form.GitHubOAuthEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.githubClientId')">
          <ElInput
            v-model="form.GitHubClientId"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.githubClientSecret')">
          <ElInput
            v-model="form.GitHubClientSecret"
            type="password"
            show-password
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.discordEnabled')">
          <ElSwitch
            v-model="form['discord.enabled']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.discordClientId')">
          <ElInput
            v-model="form['discord.client_id']"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.discordClientSecret')">
          <ElInput
            v-model="form['discord.client_secret']"
            type="password"
            show-password
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.oidcEnabled')">
          <ElSwitch
            v-model="form['oidc.enabled']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.oidcClientId')">
          <ElInput
            v-model="form['oidc.client_id']"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.oidcClientSecret')">
          <ElInput
            v-model="form['oidc.client_secret']"
            type="password"
            show-password
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.oidcWellKnown')">
          <ElInput
            v-model="form['oidc.well_known']"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.oidcAuthorizationEndpoint')">
          <ElInput
            v-model="form['oidc.authorization_endpoint']"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.oidcTokenEndpoint')">
          <ElInput
            v-model="form['oidc.token_endpoint']"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.oidcUserInfoEndpoint')">
          <ElInput
            v-model="form['oidc.user_info_endpoint']"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.telegramOAuthEnabled')">
          <ElSwitch
            v-model="form.TelegramOAuthEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.telegramBotToken')">
          <ElInput
            v-model="form.TelegramBotToken"
            type="password"
            show-password
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.telegramBotName')">
          <ElInput
            v-model="form.TelegramBotName"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.linuxDOEnabled')">
          <ElSwitch
            v-model="form.LinuxDOOAuthEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.linuxDOClientId')">
          <ElInput
            v-model="form.LinuxDOClientId"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.linuxDOClientSecret')">
          <ElInput
            v-model="form.LinuxDOClientSecret"
            type="password"
            show-password
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.linuxDOMinimumTrustLevel')">
          <ElInput
            v-model="form.LinuxDOMinimumTrustLevel"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.weChatEnabled')">
          <ElSwitch
            v-model="form.WeChatAuthEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.weChatServerAddress')">
          <ElInput
            v-model="form.WeChatServerAddress"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.weChatServerToken')">
          <ElInput
            v-model="form.WeChatServerToken"
            type="password"
            show-password
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.weChatAccountQRCodeImageURL')">
          <ElInput
            v-model="form.WeChatAccountQRCodeImageURL"
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
      id="passkey"
      :title="t('systemSettings.auth.passkey')"
      :dirty="dirty"
    >
      <ElForm label-width="180px">
        <ElFormItem :label="t('systemSettings.auth.passkeyEnabled')">
          <ElSwitch
            v-model="form['passkey.enabled']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.passkeyRpDisplayName')">
          <ElInput
            v-model="form['passkey.rp_display_name']"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.passkeyRpId')">
          <ElInput
            v-model="form['passkey.rp_id']"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.passkeyOrigins')">
          <ElInput
            v-model="form['passkey.origins']"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.passkeyAllowInsecureOrigin')">
          <ElSwitch
            v-model="form['passkey.allow_insecure_origin']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.passkeyUserVerification')">
          <ElSelect
            v-model="form['passkey.user_verification']"
            @change="dirty = true"
          >
            <ElOption
              label="Required"
              value="required"
            />
            <ElOption
              label="Preferred"
              value="preferred"
            />
            <ElOption
              label="Discouraged"
              value="discouraged"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.passkeyAttachmentPreference')">
          <ElSelect
            v-model="form['passkey.attachment_preference']"
            @change="dirty = true"
          >
            <ElOption
              label=""
              value=""
            />
            <ElOption
              label="Platform"
              value="platform"
            />
            <ElOption
              label="Cross-platform"
              value="cross-platform"
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

    <CustomOAuthSection />

    <SettingsSection
      id="bot-protection"
      :title="t('systemSettings.auth.botProtection')"
      :dirty="dirty"
    >
      <ElForm label-width="180px">
        <ElFormItem :label="t('systemSettings.auth.turnstileCheck')">
          <ElSwitch
            v-model="form.TurnstileCheckEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.turnstileSiteKey')">
          <ElInput
            v-model="form.TurnstileSiteKey"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.auth.turnstileSecretKey')">
          <ElInput
            v-model="form.TurnstileSecretKey"
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
