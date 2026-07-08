<template>
  <div class="bindings-tab">
    <!-- 内置绑定列表 -->
    <div
      v-for="item in builtinBindings"
      :key="item.key"
      class="bindings-tab__row"
    >
      <div class="bindings-tab__row-info">
        <ElIcon
          :size="20"
          class="bindings-tab__row-icon"
        >
          <component :is="item.icon" />
        </ElIcon>
        <div class="bindings-tab__row-text">
          <span class="bindings-tab__row-name">{{ item.label }}</span>
          <span class="bindings-tab__row-status">
            {{ item.bound ? t('profile.bound') : t('profile.notBound') }}
          </span>
        </div>
      </div>
      <div class="bindings-tab__row-action">
        <ElButton
          v-if="!item.bound"
          size="small"
          :disabled="!item.enabled"
          @click="item.onBind"
        >
          {{ t('profile.bind') }}
        </ElButton>
        <ElButton
          v-else-if="item.canUnbind"
          size="small"
          type="danger"
          plain
          @click="item.onUnbind"
        >
          {{ t('profile.unbind') }}
        </ElButton>
        <ElTag
          v-else
          type="success"
          size="small"
          effect="plain"
        >
          {{ t('profile.bound') }}
        </ElTag>
      </div>
    </div>

    <!-- 自定义 OAuth 列表 -->
    <template v-if="customProviders.length > 0">
      <ElDivider content-position="left">
        {{ t('profile.customOAuth') }}
      </ElDivider>
      <div
        v-for="provider in customProviders"
        :key="provider.id"
        class="bindings-tab__row"
      >
        <div class="bindings-tab__row-info">
          <ElIcon
            :size="20"
            class="bindings-tab__row-icon"
          >
            <Link />
          </ElIcon>
          <div class="bindings-tab__row-text">
            <span class="bindings-tab__row-name">{{ provider.name }}</span>
            <span class="bindings-tab__row-status">
              {{ isCustomBound(provider) ? t('profile.bound') : t('profile.notBound') }}
            </span>
          </div>
        </div>
        <div class="bindings-tab__row-action">
          <ElButton
            v-if="!isCustomBound(provider)"
            size="small"
            @click="handleCustomBind(provider)"
          >
            {{ t('profile.bind') }}
          </ElButton>
          <ElButton
            v-else
            size="small"
            type="danger"
            plain
            :loading="unbinding"
            @click="handleCustomUnbind(provider)"
          >
            {{ t('profile.unbind') }}
          </ElButton>
        </div>
      </div>
    </template>

    <!-- Dialogs -->
    <EmailBindDialog
      v-model="emailDialogOpen"
      @bound="handleBound"
    />
    <EmailUnbindDialog
      v-model="emailUnbindDialogOpen"
      :email="props.profile?.email || ''"
      @unbound="handleBound"
    />
    <WeChatBindDialog
      v-model="weChatDialogOpen"
      @bound="handleBound"
    />
    <TelegramBindDialog
      v-model="telegramDialogOpen"
      @bound="handleBound"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, markRaw, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Message,
  ChatDotSquare,
  Link,
  Promotion,
  Connection,
} from '@element-plus/icons-vue'
import { useStatus } from '@/composables/useStatus'
import { useOAuthBindings } from '@/composables/profile/useOAuthBindings'
import { useOAuthLogin } from '@/composables/auth/useOAuthLogin'
import EmailBindDialog from '../dialogs/EmailBindDialog.vue'
import EmailUnbindDialog from '../dialogs/EmailUnbindDialog.vue'
import WeChatBindDialog from '../dialogs/WeChatBindDialog.vue'
import TelegramBindDialog from '../dialogs/TelegramBindDialog.vue'
import type { UserProfile } from '@/api/profile/types'
import type { CustomOAuthProviderInfo } from '@/api/system/types'

const props = defineProps<{
  profile: UserProfile | null
}>()

const emit = defineEmits<{
  update: []
}>()

const { t } = useI18n()
const { status } = useStatus()
const { bindings, fetchBindings, unbind, unbinding } = useOAuthBindings()
const oauth = useOAuthLogin(status.value)

const emailDialogOpen = ref(false)
const emailUnbindDialogOpen = ref(false)
const weChatDialogOpen = ref(false)
const telegramDialogOpen = ref(false)

const customProviders = computed<CustomOAuthProviderInfo[]>(
  () => status.value?.customOauthProviders ?? []
)

function isCustomBound(provider: CustomOAuthProviderInfo): boolean {
  return bindings.value.some(
    (b) => b.providerId === String(provider.id) || b.providerId === provider.slug
  )
}

function handleBound(): void {
  emit('update')
}

// 邮箱解绑：打开验证码确认对话框（与绑定流程对称）
function unbindEmail(): void {
  emailUnbindDialogOpen.value = true
}

// OAuth 绑定（github/discord/oidc/linuxdo）
function initiateOAuthBind(
  provider: 'github' | 'discord' | 'oidc' | 'linuxdo'
): void {
  // 设置 bind 标记，回调时后端执行绑定而非登录
  sessionStorage.setItem('oauth_action', 'bind')
  switch (provider) {
    case 'github':
      oauth.handleGitHubLogin()
      break
    case 'discord':
      oauth.handleDiscordLogin()
      break
    case 'oidc':
      oauth.handleOIDCLogin()
      break
    case 'linuxdo':
      oauth.handleLinuxDOLogin()
      break
  }
}

function handleCustomBind(provider: CustomOAuthProviderInfo): void {
  sessionStorage.setItem('oauth_action', 'bind')
  oauth.handleCustomOAuthLogin(provider)
}

async function handleCustomUnbind(
  provider: CustomOAuthProviderInfo
): Promise<void> {
  const target = bindings.value.find(
    (b) => b.providerId === String(provider.id) || b.providerId === provider.slug
  )
  if (!target) return
  await unbind(target)
  emit('update')
}

const builtinBindings = computed(() => {
  if (!props.profile) return []
  const p = props.profile
  const s = status.value
  return [
    {
      key: 'email',
      icon: markRaw(Message),
      label: t('profile.email'),
      bound: Boolean(p.email),
      enabled: true,
      canUnbind: Boolean(p.email),
      onBind: () => { emailDialogOpen.value = true },
      onUnbind: unbindEmail,
    },
    {
      key: 'wechat',
      icon: markRaw(ChatDotSquare),
      label: t('profile.wechat'),
      bound: Boolean(p.wechatId),
      enabled: s?.wechatLogin === true,
      canUnbind: Boolean(p.wechatId),
      onBind: () => { weChatDialogOpen.value = true },
      onUnbind: () => emit('update'),
    },
    {
      key: 'github',
      icon: markRaw(Link),
      label: 'GitHub',
      bound: Boolean(p.githubId),
      enabled: s?.githubOauth === true,
      canUnbind: Boolean(p.githubId),
      onBind: () => initiateOAuthBind('github'),
      onUnbind: () => emit('update'),
    },
    {
      key: 'discord',
      icon: markRaw(Connection),
      label: 'Discord',
      bound: Boolean(p.discordId),
      enabled: s?.discordOauth === true,
      canUnbind: Boolean(p.discordId),
      onBind: () => initiateOAuthBind('discord'),
      onUnbind: () => emit('update'),
    },
    {
      key: 'oidc',
      icon: markRaw(Connection),
      label: 'OIDC',
      bound: Boolean(p.oidcId),
      enabled: s?.oidcEnabled === true,
      canUnbind: Boolean(p.oidcId),
      onBind: () => initiateOAuthBind('oidc'),
      onUnbind: () => emit('update'),
    },
    {
      key: 'telegram',
      icon: markRaw(Promotion),
      label: 'Telegram',
      bound: Boolean(p.telegramId),
      enabled: s?.telegramOauth === true,
      canUnbind: Boolean(p.telegramId),
      onBind: () => { telegramDialogOpen.value = true },
      onUnbind: () => emit('update'),
    },
    {
      key: 'linuxdo',
      icon: markRaw(Link),
      label: 'LinuxDo',
      bound: Boolean(p.linuxDoId),
      enabled: s?.linuxdoOauth === true,
      canUnbind: Boolean(p.linuxDoId),
      onBind: () => initiateOAuthBind('linuxdo'),
      onUnbind: () => emit('update'),
    },
  ]
})

onMounted(() => {
  if (customProviders.value.length > 0) {
    fetchBindings()
  }
})
</script>

<style scoped>
.bindings-tab__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--ys-spacing-3) 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.bindings-tab__row:last-child {
  border-bottom: none;
}

.bindings-tab__row-info {
  display: flex;
  gap: var(--ys-spacing-3);
  align-items: center;
}

.bindings-tab__row-icon {
  color: var(--el-text-color-secondary);
}

.bindings-tab__row-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.bindings-tab__row-name {
  font-size: var(--ys-font-size-base);
  font-weight: 500;
}

.bindings-tab__row-status {
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}
</style>
