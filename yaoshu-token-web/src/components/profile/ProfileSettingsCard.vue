<template>
  <ElCard shadow="never">
    <template #header>
      <div class="settings-card__header">
        <ElIcon :size="18">
          <Setting />
        </ElIcon>
        <span>{{ t('profile.settingsAndBindings') }}</span>
      </div>
    </template>
    <ElTabs
      v-model="activeTab"
      class="settings-card__tabs"
    >
      <ElTabPane
        :label="t('profile.accountBindings')"
        name="bindings"
      >
        <AccountBindingsTab
          :profile="profile"
          @update="handleUpdate"
        />
      </ElTabPane>
      <ElTabPane
        :label="t('profile.notificationSettings')"
        name="notifications"
      >
        <NotificationTab
          :profile="profile"
          @update="handleUpdate"
        />
      </ElTabPane>
    </ElTabs>
  </ElCard>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Setting } from '@element-plus/icons-vue'
import AccountBindingsTab from './tabs/AccountBindingsTab.vue'
import NotificationTab from './tabs/NotificationTab.vue'
import type { UserProfile } from '@/api/profile/types'

defineProps<{
  profile: UserProfile | null
  loading: boolean
}>()

const emit = defineEmits<{
  profileUpdate: []
}>()

const { t } = useI18n()
const activeTab = ref('bindings')

function handleUpdate(): void {
  emit('profileUpdate')
}
</script>

<style scoped>
.settings-card__header {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
  font-weight: 600;
}

.settings-card__tabs {
  margin-top: var(--ys-spacing-1);
}
</style>
