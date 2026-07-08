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

interface HeaderNavConfig {
  home: boolean
  console: boolean
  pricing: { enabled: boolean }
  rankings: { enabled: boolean }
  docs: boolean
  about: boolean
}

interface SiteForm {
  SystemName: string
  Logo: string
  Footer: string
  About: string
  HomePageContent: string
  ServerAddress: string
  HeaderNavModules: string
  SidebarModulesAdmin: string
}

const defaults: SiteForm = {
  SystemName: '',
  Logo: '',
  Footer: '',
  About: '',
  HomePageContent: '',
  ServerAddress: '',
  HeaderNavModules: '{"home":true,"console":true,"pricing":{"enabled":true},"rankings":{"enabled":true},"docs":true,"about":true}',
  SidebarModulesAdmin: '{}',
}

const form = ref<SiteForm>({ ...defaults })
const initial = ref<SiteForm>({ ...defaults })
const dirty = ref(false)

const headerNav = ref<HeaderNavConfig>({
  home: true,
  console: true,
  pricing: { enabled: true },
  rankings: { enabled: true },
  docs: true,
  about: true,
})

function loadHeaderNav(raw: string) {
  try {
    const nav = JSON.parse(raw || '{}')
    headerNav.value = {
      home: nav.home ?? true,
      console: nav.console ?? true,
      pricing: { enabled: nav.pricing?.enabled ?? true },
      rankings: { enabled: nav.rankings?.enabled ?? true },
      docs: nav.docs ?? true,
      about: nav.about ?? true,
    }
  } catch {
    headerNav.value = {
      home: true,
      console: true,
      pricing: { enabled: true },
      rankings: { enabled: true },
      docs: true,
      about: true,
    }
  }
}

function loadForm() {
  const parsed = getOptionValue(data.value ?? [], defaults)
  form.value = { ...parsed }
  initial.value = { ...parsed }
  loadHeaderNav(parsed.HeaderNavModules)
  dirty.value = false
}

async function handleSave() {
  form.value.HeaderNavModules = JSON.stringify(headerNav.value)
  const keys: Array<keyof SiteForm> = ['SystemName', 'Logo', 'Footer', 'About', 'HomePageContent', 'ServerAddress', 'HeaderNavModules', 'SidebarModulesAdmin']
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
  loadHeaderNav(initial.value.HeaderNavModules)
  dirty.value = false
}

onMounted(async () => {
  await fetchOptions()
  loadForm()
})
</script>

<template>
  <SettingsPageLayout
    :title="t('systemSettings.tabs.site')"
    :loading="loading"
  >
    <SettingsSection
      id="system-info"
      :title="t('systemSettings.site.systemInfo')"
      :default-expanded="true"
      :dirty="dirty"
    >
      <ElForm
        label-width="140px"
        label-position="right"
      >
        <ElFormItem :label="t('systemSettings.site.systemName')">
          <ElInput
            v-model="form.SystemName"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.site.logo')">
          <ElInput
            v-model="form.Logo"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.site.serverAddress')">
          <ElInput
            v-model="form.ServerAddress"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.site.footer')">
          <ElInput
            v-model="form.Footer"
            type="textarea"
            :rows="2"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.site.about')">
          <ElInput
            v-model="form.About"
            type="textarea"
            :rows="3"
            @input="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.site.homePageContent')">
          <ElInput
            v-model="form.HomePageContent"
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
      id="header-navigation"
      :title="t('systemSettings.site.headerNavigation')"
      :dirty="dirty"
    >
      <ElForm label-width="180px">
        <ElFormItem :label="t('systemSettings.site.navHome')">
          <ElSwitch
            v-model="headerNav.home"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.site.navConsole')">
          <ElSwitch
            v-model="headerNav.console"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.site.navPricing')">
          <ElSwitch
            v-model="headerNav.pricing.enabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.site.navRankings')">
          <ElSwitch
            v-model="headerNav.rankings.enabled"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.site.navDocs')">
          <ElSwitch
            v-model="headerNav.docs"
            @change="dirty = true"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.site.navAbout')">
          <ElSwitch
            v-model="headerNav.about"
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
      id="sidebar-modules"
      :title="t('systemSettings.site.sidebarModules')"
      :dirty="dirty"
    >
      <ElForm label-width="180px">
        <ElFormItem :label="t('systemSettings.site.sidebarModulesData')">
          <ElInput
            v-model="form.SidebarModulesAdmin"
            type="textarea"
            :rows="8"
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
