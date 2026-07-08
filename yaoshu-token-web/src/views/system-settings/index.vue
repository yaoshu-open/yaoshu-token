<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  Setting, Lock, CreditCard, Box, WarningFilled, Files, Tools,
} from '@element-plus/icons-vue'
import SiteSettings from '@/components/system-settings/site/SiteSettings.vue'
import AuthSettings from '@/components/system-settings/auth/AuthSettings.vue'
import BillingSettings from '@/components/system-settings/billing/BillingSettings.vue'
import ModelsSettings from '@/components/system-settings/models/ModelsSettings.vue'
import SecuritySettings from '@/components/system-settings/security/SecuritySettings.vue'
import ContentSettings from '@/components/system-settings/content/ContentSettings.vue'
import OperationsSettings from '@/components/system-settings/operations/OperationsSettings.vue'

type SettingsTab = 'site' | 'auth' | 'billing' | 'models' | 'security' | 'content' | 'operations'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const currentTab = computed<SettingsTab>(() => {
  const tab = route.params.tab as string
  const valid: SettingsTab[] = ['site', 'auth', 'billing', 'models', 'security', 'content', 'operations']
  return valid.includes(tab as SettingsTab) ? (tab as SettingsTab) : 'site'
})

watch(
  () => route.params.tab,
  (tab) => {
    if (!tab) router.replace('/system-settings/site')
  },
  { immediate: true },
)

const tabGroups = computed(() => [
  { key: 'site' as SettingsTab, label: t('systemSettings.tabs.site'), icon: Setting },
  { key: 'auth' as SettingsTab, label: t('systemSettings.tabs.auth'), icon: Lock },
  { key: 'billing' as SettingsTab, label: t('systemSettings.tabs.billing'), icon: CreditCard },
  { key: 'models' as SettingsTab, label: t('systemSettings.tabs.models'), icon: Box },
  { key: 'security' as SettingsTab, label: t('systemSettings.tabs.security'), icon: WarningFilled },
  { key: 'content' as SettingsTab, label: t('systemSettings.tabs.content'), icon: Files },
  { key: 'operations' as SettingsTab, label: t('systemSettings.tabs.operations'), icon: Tools },
])

function switchTab(key: SettingsTab) {
  router.push(`/system-settings/${key}`)
}
</script>

<template>
  <div class="system-settings-page">
    <div class="system-settings-page__sidebar">
      <div
        v-for="group in tabGroups"
        :key="group.key"
        class="system-settings-page__nav-item"
        :class="{ 'is-active': currentTab === group.key }"
        @click="switchTab(group.key)"
      >
        <ElIcon class="system-settings-page__nav-icon">
          <component :is="group.icon" />
        </ElIcon>
        <span class="system-settings-page__nav-label">{{ group.label }}</span>
      </div>
    </div>
    <div class="system-settings-page__content">
      <SiteSettings v-if="currentTab === 'site'" />
      <AuthSettings v-else-if="currentTab === 'auth'" />
      <BillingSettings v-else-if="currentTab === 'billing'" />
      <ModelsSettings v-else-if="currentTab === 'models'" />
      <SecuritySettings v-else-if="currentTab === 'security'" />
      <ContentSettings v-else-if="currentTab === 'content'" />
      <OperationsSettings v-else-if="currentTab === 'operations'" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.system-settings-page {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: var(--ys-spacing-4);
  min-height: calc(100vh - 60px);
  padding: var(--ys-spacing-4);

  @media (width <= 1024px) {
    grid-template-columns: 1fr;
  }

  &__sidebar {
    position: sticky;
    top: 0;
    align-self: start;
    max-height: calc(100vh - 60px);
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-1);
    padding: var(--ys-spacing-3);
    background: var(--el-fill-color-blank);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--ys-radius-md);
  }

  &__nav-item {
    display: flex;
    gap: 10px;
    align-items: center;
    padding: 10px var(--ys-spacing-3);
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-regular);
    cursor: pointer;
    border-radius: var(--ys-radius-base);
    transition: all 0.2s;

    &:hover {
      background: var(--el-fill-color-light);
    }

    &.is-active {
      font-weight: 600;
      color: var(--el-color-primary);
      background: var(--el-color-primary-light-9);
    }
  }

  &__nav-icon {
    font-size: var(--ys-font-size-lg);
  }

  &__content {
    min-width: 0;
  }
}
</style>
