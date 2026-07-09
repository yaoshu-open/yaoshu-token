<template>
  <ElCard shadow="never">
    <template #header>
      <div class="sidebar-card__header">
        <i class="i-ep-menu sidebar-card__header-icon" />
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
            <i :class="item.icon" />
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
import { reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { updateUserSettings } from '@/api/profile'
import { parseUserSettings } from '@/utils/profile'
import { useAuthStore } from '@/store/modules/auth'
import type { UserProfile, UpdateUserSettingsRequest } from '@/api/profile/types'

const props = defineProps<{
  profile: UserProfile | null
}>()

const { t } = useI18n()
const authStore = useAuthStore()

// 模块选项：key 使用 "section.module" 格式，映射到 useSidebarConfig 的 section/module 结构
// 排除 personal.personal（个人资料自身，防止关闭后死锁无法再打开）
// 排除 admin section（由角色权限控制，不应让普通用户控制）
// 排除 chat.chat（由 SPI feature flag 控制）
// 命名与侧边栏导航一致，图标使用 UnoCSS 类名（与 SIDEBAR_ICONS 一致）
const moduleOptions = [
  { key: 'chat.playground', label: t('nav.playground'), icon: 'i-ep-magic-stick' },
  { key: 'console.detail', label: t('nav.dashboard'), icon: 'i-ep-menu' },
  { key: 'console.token', label: t('nav.apiKeys'), icon: 'i-ep-key' },
  { key: 'console.analytics', label: t('nav.analytics'), icon: 'i-ep-trend-charts' },
  { key: 'console.log', label: t('nav.usageLogs'), icon: 'i-ep-document' },
  { key: 'personal.topup', label: t('nav.wallet'), icon: 'i-ep-wallet' },
] as const

const form = reactive<Record<string, boolean>>(
  Object.fromEntries(moduleOptions.map((opt) => [opt.key, true]))
)

watch(
  () => props.profile,
  (val) => {
    if (!val?.setting) return
    const parsed = parseUserSettings(val.setting)
    const sm = parsed.sidebarModules
    if (sm && typeof sm === 'object') {
      for (const opt of moduleOptions) {
        const [section, module] = opt.key.split('.')
        form[opt.key] = (sm as Record<string, Record<string, boolean>>)[section]?.[module] ?? true
      }
    }
  },
  { immediate: true }
)

let saveTimer: ReturnType<typeof setTimeout> | null = null

function handleSave(): void {
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(async () => {
    try {
      const sidebarModules: Record<string, Record<string, boolean>> = {}
      for (const opt of moduleOptions) {
        const [section, module] = opt.key.split('.')
        if (!sidebarModules[section]) {
          sidebarModules[section] = { enabled: true }
        }
        sidebarModules[section][module] = form[opt.key] ?? true
      }
      await updateUserSettings({
        sidebarModules,
      } as UpdateUserSettingsRequest)
      ElMessage.success(t('profile.modulesSaved'))
      // 刷新 auth store 的 userInfo，使侧边栏立即应用新设置
      await authStore.fetchUserInfo()
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

.sidebar-card__header-icon {
  font-size: 18px;
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

.sidebar-card__row-info i {
  font-size: 16px;
}
</style>
