<template>
  <ElCard shadow="never">
    <template #header>
      <div class="sidebar-card__header">
        <ElIcon :size="18">
          <Menu />
        </ElIcon>
        <span>{{ t('profile.sidebarModules') }}</span>
      </div>
    </template>

    <div class="sidebar-card__body">
      <p class="sidebar-card__desc">
        {{ t('profile.sidebarModulesDesc') }}
      </p>
      <div class="sidebar-card__list">
        <div
          v-for="item in moduleOptions"
          :key="item.key"
          class="sidebar-card__row"
        >
          <div class="sidebar-card__row-info">
            <ElIcon :size="16">
              <component :is="item.icon" />
            </ElIcon>
            <span>{{ item.label }}</span>
          </div>
          <ElSwitch
            v-model="form[item.key]"
            @change="handleSave"
          />
        </div>
      </div>
    </div>
  </ElCard>
</template>

<script setup lang="ts">
import { reactive, watch, markRaw } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Menu, Wallet, Document, Setting, DataLine, ChatDotRound } from '@element-plus/icons-vue'
import { updateUserSettings } from '@/api/profile'
import { parseUserSettings } from '@/utils/profile'
import type { UserProfile, UpdateUserSettingsRequest } from '@/api/profile/types'

const props = defineProps<{
  profile: UserProfile | null
}>()

const { t } = useI18n()

const moduleOptions = [
  { key: 'showWallet', label: t('profile.moduleWallet'), icon: markRaw(Wallet) },
  { key: 'showTokens', label: t('profile.moduleTokens'), icon: markRaw(Document) },
  { key: 'showDashboard', label: t('profile.moduleDashboard'), icon: markRaw(DataLine) },
  { key: 'showProfile', label: t('profile.moduleProfile'), icon: markRaw(Setting) },
  { key: 'showCheckin', label: t('profile.moduleCheckin'), icon: markRaw(ChatDotRound) },
] as const

const form = reactive<Record<string, boolean>>({
  showWallet: true,
  showTokens: true,
  showDashboard: true,
  showProfile: true,
  showCheckin: true,
})

watch(
  () => props.profile,
  (val) => {
    if (!val?.setting) return
    const parsed = parseUserSettings(val.setting)
    form.showWallet = parsed.sidebarModules?.showWallet ?? true
    form.showTokens = parsed.sidebarModules?.showTokens ?? true
    form.showDashboard = parsed.sidebarModules?.showDashboard ?? true
    form.showProfile = parsed.sidebarModules?.showProfile ?? true
    form.showCheckin = parsed.sidebarModules?.showCheckin ?? true
  },
  { immediate: true }
)

let saveTimer: ReturnType<typeof setTimeout> | null = null

function handleSave(): void {
  // 防抖保存
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(async () => {
    try {
      await updateUserSettings({
        sidebarModules: {
          showWallet: form.showWallet,
          showTokens: form.showTokens,
          showDashboard: form.showDashboard,
          showProfile: form.showProfile,
          showCheckin: form.showCheckin,
        },
      } as UpdateUserSettingsRequest)
      ElMessage.success(t('profile.modulesSaved'))
    } catch {
      // 错误由 request 拦截器处理
    }
  }, 500)
}
</script>

<style scoped>
.sidebar-card__header {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
  font-weight: 600;
}

.sidebar-card__body {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);
}

.sidebar-card__desc {
  margin: 0;
  font-size: var(--ys-font-size-base);
  color: var(--el-text-color-secondary);
}

.sidebar-card__list {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-1);
}

.sidebar-card__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--ys-spacing-2) 0;
}

.sidebar-card__row-info {
  display: flex;
  gap: var(--ys-spacing-3);
  align-items: center;
  font-size: var(--ys-font-size-base);
}
</style>
