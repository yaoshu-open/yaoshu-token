<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import SettingsPageLayout from '../SettingsPageLayout.vue'
import SettingsSection from '../SettingsSection.vue'
import SettingsFormActions from '../SettingsFormActions.vue'
import ModelRatioForm from './ModelRatioForm.vue'
import { useSystemOptions, getOptionValue } from '@/composables/system-settings/useSystemOptions'
import { useUpdateOption } from '@/composables/system-settings/useUpdateOption'
import { confirmPaymentCompliance } from '@/api/system-option'

const { t } = useI18n()
const { data, loading, fetchOptions } = useSystemOptions()
const { saving, save } = useUpdateOption()

interface BillingForm {
  // quota section
  QuotaForNewUser: number
  PreConsumedQuota: number
  QuotaForInviter: number
  QuotaForInvitee: number
  TopUpLink: string
  'general_setting.docs_link': string
  'quota_setting.enable_free_model_pre_consume': boolean
  // currency section
  QuotaPerUnit: number
  USDExchangeRate: number
  DisplayInCurrencyEnabled: boolean
  DisplayTokenStatEnabled: boolean
  'general_setting.quota_display_type': string
  'general_setting.custom_currency_symbol': string
  'general_setting.custom_currency_exchange_rate': number
  // model-pricing section
  ModelRatio: string
  ModelPrice: string
  CompletionRatio: string
  CacheRatio: string
  CreateCacheRatio: string
  ImageRatio: string
  AudioRatio: string
  AudioCompletionRatio: string
  ExposeRatioEnabled: boolean
  'billing_setting.billing_mode': string
  'billing_setting.billing_expr': string
  'tool_price_setting.prices': string
  // group-pricing section
  GroupRatio: string
  UserUsableGroups: string
  TopupGroupRatio: string
  GroupGroupRatio: string
  AutoGroups: string
  DefaultUseAutoGroup: boolean
  'group_ratio_setting.group_special_usable_group': string
  // payment section (Epay)
  PayAddress: string
  EpayId: string
  EpayKey: string
  Price: number
  MinTopUp: number
  CustomCallbackAddress: string
  PayMethods: string
  'payment_setting.amount_options': string
  'payment_setting.amount_discount': string
  'payment_setting.compliance_confirmed': boolean
  // Stripe section
  StripeApiSecret: string
  StripeWebhookSecret: string
  StripePriceId: string
  StripeUnitPrice: number
  StripeMinTopUp: number
  StripePromotionCodesEnabled: boolean
  // Creem section
  CreemApiKey: string
  CreemWebhookSecret: string
  CreemTestMode: boolean
  CreemProducts: string
  // Waffo section
  WaffoEnabled: boolean
  WaffoApiKey: string
  WaffoPrivateKey: string
  WaffoPublicCert: string
  WaffoSandboxPublicCert: string
  WaffoSandboxApiKey: string
  WaffoSandboxPrivateKey: string
  WaffoSandbox: boolean
  WaffoMerchantId: string
  WaffoCurrency: string
  WaffoUnitPrice: number
  WaffoMinTopUp: number
  WaffoNotifyUrl: string
  WaffoReturnUrl: string
  WaffoPayMethods: string
  // Waffo Pancake section
  WaffoPancakeMerchantID: string
  WaffoPancakePrivateKey: string
  WaffoPancakeReturnURL: string
  WaffoPancakeStoreID: string
  WaffoPancakeProductID: string
  // checkin section
  'checkin_setting.enabled': boolean
  'checkin_setting.min_quota': number
  'checkin_setting.max_quota': number
}

const defaults: BillingForm = {
  // quota section
  QuotaForNewUser: 0,
  PreConsumedQuota: 0,
  QuotaForInviter: 0,
  QuotaForInvitee: 0,
  TopUpLink: '',
  'general_setting.docs_link': '',
  'quota_setting.enable_free_model_pre_consume': false,
  // currency section
  QuotaPerUnit: 500000,
  USDExchangeRate: 7,
  DisplayInCurrencyEnabled: false,
  DisplayTokenStatEnabled: true,
  'general_setting.quota_display_type': '',
  'general_setting.custom_currency_symbol': '¤',
  'general_setting.custom_currency_exchange_rate': 1,
  // model-pricing section
  ModelRatio: '{}',
  ModelPrice: '{}',
  CompletionRatio: '{}',
  CacheRatio: '{}',
  CreateCacheRatio: '{}',
  ImageRatio: '{}',
  AudioRatio: '{}',
  AudioCompletionRatio: '{}',
  ExposeRatioEnabled: false,
  'billing_setting.billing_mode': '',
  'billing_setting.billing_expr': '',
  'tool_price_setting.prices': '{}',
  // group-pricing section
  GroupRatio: '{}',
  UserUsableGroups: '{}',
  TopupGroupRatio: '{}',
  GroupGroupRatio: '{}',
  AutoGroups: '{}',
  DefaultUseAutoGroup: false,
  'group_ratio_setting.group_special_usable_group': '{}',
  // payment section (Epay)
  PayAddress: '',
  EpayId: '',
  EpayKey: '',
  Price: 1,
  MinTopUp: 1,
  CustomCallbackAddress: '',
  PayMethods: '[]',
  'payment_setting.amount_options': '[]',
  'payment_setting.amount_discount': '[]',
  'payment_setting.compliance_confirmed': false,
  // Stripe section
  StripeApiSecret: '',
  StripeWebhookSecret: '',
  StripePriceId: '',
  StripeUnitPrice: 1,
  StripeMinTopUp: 1,
  StripePromotionCodesEnabled: false,
  // Creem section
  CreemApiKey: '',
  CreemWebhookSecret: '',
  CreemTestMode: false,
  CreemProducts: '[]',
  // Waffo section
  WaffoEnabled: false,
  WaffoApiKey: '',
  WaffoPrivateKey: '',
  WaffoPublicCert: '',
  WaffoSandboxPublicCert: '',
  WaffoSandboxApiKey: '',
  WaffoSandboxPrivateKey: '',
  WaffoSandbox: false,
  WaffoMerchantId: '',
  WaffoCurrency: 'USD',
  WaffoUnitPrice: 1,
  WaffoMinTopUp: 1,
  WaffoNotifyUrl: '',
  WaffoReturnUrl: '',
  WaffoPayMethods: '[]',
  // Waffo Pancake section
  WaffoPancakeMerchantID: '',
  WaffoPancakePrivateKey: '',
  WaffoPancakeReturnURL: '',
  WaffoPancakeStoreID: '',
  WaffoPancakeProductID: '',
  // checkin section
  'checkin_setting.enabled': false,
  'checkin_setting.min_quota': 0,
  'checkin_setting.max_quota': 0,
}

const form = ref<BillingForm>({ ...defaults })
const initial = ref<BillingForm>({ ...defaults })
const dirty = ref(false)
const complianceChecking = ref(false)

async function handleComplianceConfirm() {
  complianceChecking.value = true
  try {
    const res = await confirmPaymentCompliance()
    if (res?.confirmed) {
      form.value['payment_setting.compliance_confirmed'] = true
      dirty.value = true
    }
  } finally {
    complianceChecking.value = false
  }
}

function loadForm() {
  const parsed = getOptionValue(data.value ?? [], defaults)
  form.value = { ...parsed }
  initial.value = { ...parsed }
  dirty.value = false
}

async function handleSave() {
  const keys: Array<keyof BillingForm> = Object.keys(form.value) as Array<keyof BillingForm>
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
    :title="t('systemSettings.tabs.billing')"
    :loading="loading"
  >
    <SettingsSection
      id="quota"
      :title="t('systemSettings.billing.quota')"
      :default-expanded="true"
      :dirty="dirty"
    >
      <ElForm label-width="180px">
        <ElFormItem :label="t('systemSettings.billing.quotaForNewUser')">
          <ElInputNumber
            v-model="form.QuotaForNewUser"
            :min="0"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.preConsumedQuota')">
          <ElInputNumber
            v-model="form.PreConsumedQuota"
            :min="0"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.quotaForInviter')">
          <ElInputNumber
            v-model="form.QuotaForInviter"
            :min="0"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.quotaForInvitee')">
          <ElInputNumber
            v-model="form.QuotaForInvitee"
            :min="0"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.topUpLink')">
          <ElInput
            v-model="form.TopUpLink"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.docsLink')">
          <ElInput
            v-model="form['general_setting.docs_link']"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.freeModelPreConsume')">
          <ElSwitch
            v-model="form['quota_setting.enable_free_model_pre_consume']"
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
      id="currency"
      :title="t('systemSettings.billing.currency')"
      :dirty="dirty"
    >
      <ElForm label-width="180px">
        <ElFormItem :label="t('systemSettings.billing.quotaPerUnit')">
          <ElInputNumber
            v-model="form.QuotaPerUnit"
            :min="1"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.usdExchangeRate')">
          <ElInputNumber
            v-model="form.USDExchangeRate"
            :min="0.0001"
            :precision="4"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.displayInCurrency')">
          <ElSwitch
            v-model="form.DisplayInCurrencyEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.displayTokenStat')">
          <ElSwitch
            v-model="form.DisplayTokenStatEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.quotaDisplayType')">
          <ElSelect
            v-model="form['general_setting.quota_display_type']"
            placeholder="US$"
            style="width: 200px"
            @change="dirty = true"
          >
            <ElOption
              label="US$ ($)"
              value=""
            />
            <ElOption
              label="¥ 人民币 (CNY)"
              value="CNY"
            />
            <ElOption
              label="NPO ($N)"
              value="NPO"
            />
            <ElOption
              label="NPG (₦)"
              value="NPG"
            />
            <ElOption
              label="Custom (¤)"
              value="Custom"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.customCurrencySymbol')">
          <ElInput
            v-model="form['general_setting.custom_currency_symbol']"
            style="width: 200px"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.customCurrencyExchangeRate')">
          <ElInputNumber
            v-model="form['general_setting.custom_currency_exchange_rate']"
            :min="0"
            :precision="4"
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
      id="model-pricing"
      :title="t('systemSettings.billing.modelPricing')"
      :dirty="dirty"
    >
      <ModelRatioForm
        :model-ratio="form.ModelRatio"
        :model-price="form.ModelPrice"
        :completion-ratio="form.CompletionRatio"
        :cache-ratio="form.CacheRatio"
        :create-cache-ratio="form.CreateCacheRatio"
        :image-ratio="form.ImageRatio"
        :audio-ratio="form.AudioRatio"
        :audio-completion-ratio="form.AudioCompletionRatio"
        @update="(payload) => { Object.assign(form, payload); dirty = true }"
      />
      <ElForm
        label-width="180px"
        class="mt-12px"
      >
        <ElFormItem :label="t('systemSettings.billing.exposeRatioEnabled')">
          <ElSwitch
            v-model="form.ExposeRatioEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.billingMode')">
          <ElSelect
            v-model="form['billing_setting.billing_mode']"
            style="width: 240px"
            @change="dirty = true"
          >
            <ElOption
              :label="t('systemSettings.billing.billingModeDefault')"
              value=""
            />
            <ElOption
              :label="t('systemSettings.billing.billingModeTiered')"
              value="tiered_expr"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem
          v-if="form['billing_setting.billing_mode'] === 'tiered_expr'"
          :label="t('systemSettings.billing.billingExpr')"
        >
          <ElInput
            v-model="form['billing_setting.billing_expr']"
            type="textarea"
            :rows="4"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.toolPrices')">
          <ElInput
            v-model="form['tool_price_setting.prices']"
            type="textarea"
            :rows="3"
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
      id="group-pricing"
      :title="t('systemSettings.billing.groupPricing')"
      :dirty="dirty"
    >
      <ElForm label-width="200px">
        <ElFormItem :label="t('systemSettings.billing.groupRatio')">
          <ElInput
            v-model="form.GroupRatio"
            type="textarea"
            :rows="4"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.topupGroupRatio')">
          <ElInput
            v-model="form.TopupGroupRatio"
            type="textarea"
            :rows="4"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.userUsableGroups')">
          <ElInput
            v-model="form.UserUsableGroups"
            type="textarea"
            :rows="3"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.groupGroupRatio')">
          <ElInput
            v-model="form.GroupGroupRatio"
            type="textarea"
            :rows="4"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.autoGroups')">
          <ElInput
            v-model="form.AutoGroups"
            type="textarea"
            :rows="3"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.defaultUseAutoGroup')">
          <ElSwitch
            v-model="form.DefaultUseAutoGroup"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.groupSpecialUsableGroup')">
          <ElInput
            v-model="form['group_ratio_setting.group_special_usable_group']"
            type="textarea"
            :rows="3"
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
      id="payment"
      :title="t('systemSettings.billing.payment')"
      :dirty="dirty"
    >
      <ElAlert
        v-if="!form['payment_setting.compliance_confirmed']"
        :title="t('systemSettings.billing.complianceTitle')"
        type="warning"
        show-icon
        :closable="false"
        class="mb-16px"
      >
        <template #default>
          <p>{{ t('systemSettings.billing.complianceDesc') }}</p>
          <ElButton
            type="warning"
            size="small"
            :loading="complianceChecking"
            class="mt-8px"
            @click="handleComplianceConfirm"
          >
            {{ t('systemSettings.billing.complianceConfirm') }}
          </ElButton>
        </template>
      </ElAlert>
      <ElAlert
        v-else
        :title="t('systemSettings.billing.complianceConfirmed')"
        type="success"
        show-icon
        :closable="false"
        class="mb-16px"
      />
      <ElForm label-width="180px">
        <ElFormItem :label="t('systemSettings.billing.payAddress')">
          <ElInput
            v-model="form.PayAddress"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.epayId')">
          <ElInput
            v-model="form.EpayId"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.epayKey')">
          <ElInput
            v-model="form.EpayKey"
            type="password"
            show-password
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.price')">
          <ElInputNumber
            v-model="form.Price"
            :min="0"
            :precision="2"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.minTopUp')">
          <ElInputNumber
            v-model="form.MinTopUp"
            :min="0"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.customCallbackAddress')">
          <ElInput
            v-model="form.CustomCallbackAddress"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.payMethods')">
          <ElInput
            v-model="form.PayMethods"
            type="textarea"
            :rows="3"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.amountOptions')">
          <ElInput
            v-model="form['payment_setting.amount_options']"
            type="textarea"
            :rows="3"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.amountDiscount')">
          <ElInput
            v-model="form['payment_setting.amount_discount']"
            type="textarea"
            :rows="3"
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
      id="stripe"
      :title="t('systemSettings.billing.stripe')"
      :dirty="dirty"
    >
      <ElForm label-width="180px">
        <ElFormItem :label="t('systemSettings.billing.stripeApiSecret')">
          <ElInput
            v-model="form.StripeApiSecret"
            type="password"
            show-password
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.stripeWebhookSecret')">
          <ElInput
            v-model="form.StripeWebhookSecret"
            type="password"
            show-password
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.stripePriceId')">
          <ElInput
            v-model="form.StripePriceId"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.stripeUnitPrice')">
          <ElInputNumber
            v-model="form.StripeUnitPrice"
            :min="0"
            :precision="2"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.stripeMinTopUp')">
          <ElInputNumber
            v-model="form.StripeMinTopUp"
            :min="0"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.stripePromotionCodes')">
          <ElSwitch
            v-model="form.StripePromotionCodesEnabled"
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
      id="creem"
      :title="t('systemSettings.billing.creem')"
      :dirty="dirty"
    >
      <ElForm label-width="180px">
        <ElFormItem :label="t('systemSettings.billing.creemApiKey')">
          <ElInput
            v-model="form.CreemApiKey"
            type="password"
            show-password
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.creemWebhookSecret')">
          <ElInput
            v-model="form.CreemWebhookSecret"
            type="password"
            show-password
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.creemTestMode')">
          <ElSwitch
            v-model="form.CreemTestMode"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.creemProducts')">
          <ElInput
            v-model="form.CreemProducts"
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

    <SettingsSection
      id="waffo"
      :title="t('systemSettings.billing.waffo')"
      :dirty="dirty"
    >
      <ElForm label-width="200px">
        <ElFormItem :label="t('systemSettings.billing.waffoEnabled')">
          <ElSwitch
            v-model="form.WaffoEnabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.waffoSandbox')">
          <ElSwitch
            v-model="form.WaffoSandbox"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.waffoApiKey')">
          <ElInput
            v-model="form.WaffoApiKey"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.waffoPrivateKey')">
          <ElInput
            v-model="form.WaffoPrivateKey"
            type="password"
            show-password
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.waffoPublicCert')">
          <ElInput
            v-model="form.WaffoPublicCert"
            type="textarea"
            :rows="3"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.waffoSandboxApiKey')">
          <ElInput
            v-model="form.WaffoSandboxApiKey"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.waffoSandboxPrivateKey')">
          <ElInput
            v-model="form.WaffoSandboxPrivateKey"
            type="password"
            show-password
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.waffoSandboxPublicCert')">
          <ElInput
            v-model="form.WaffoSandboxPublicCert"
            type="textarea"
            :rows="3"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.waffoMerchantId')">
          <ElInput
            v-model="form.WaffoMerchantId"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.waffoCurrency')">
          <ElInput
            v-model="form.WaffoCurrency"
            style="width: 120px"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.waffoUnitPrice')">
          <ElInputNumber
            v-model="form.WaffoUnitPrice"
            :min="0"
            :precision="2"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.waffoMinTopUp')">
          <ElInputNumber
            v-model="form.WaffoMinTopUp"
            :min="0"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.waffoNotifyUrl')">
          <ElInput
            v-model="form.WaffoNotifyUrl"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.waffoReturnUrl')">
          <ElInput
            v-model="form.WaffoReturnUrl"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.waffoPayMethods')">
          <ElInput
            v-model="form.WaffoPayMethods"
            type="textarea"
            :rows="3"
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
      id="waffo-pancake"
      :title="t('systemSettings.billing.waffoPancake')"
      :dirty="dirty"
    >
      <ElForm label-width="180px">
        <ElFormItem :label="t('systemSettings.billing.waffoPancakeMerchantId')">
          <ElInput
            v-model="form.WaffoPancakeMerchantID"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.waffoPancakePrivateKey')">
          <ElInput
            v-model="form.WaffoPancakePrivateKey"
            type="password"
            show-password
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.waffoPancakeReturnUrl')">
          <ElInput
            v-model="form.WaffoPancakeReturnURL"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.waffoPancakeStoreId')">
          <ElInput
            v-model="form.WaffoPancakeStoreID"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.waffoPancakeProductId')">
          <ElInput
            v-model="form.WaffoPancakeProductID"
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
      id="checkin"
      :title="t('systemSettings.billing.checkin')"
      :dirty="dirty"
    >
      <ElForm label-width="180px">
        <ElFormItem :label="t('systemSettings.billing.checkinEnabled')">
          <ElSwitch
            v-model="form['checkin_setting.enabled']"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.checkinMinQuota')">
          <ElInputNumber
            v-model="form['checkin_setting.min_quota']"
            :min="0"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.checkinMaxQuota')">
          <ElInputNumber
            v-model="form['checkin_setting.max_quota']"
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
  </SettingsPageLayout>
</template>
