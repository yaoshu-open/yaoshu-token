<script setup lang="ts">
// Chat 嵌入页：根据 chatId 参数渲染对应的聊天预设 iframe
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElAlert, ElButton } from 'element-plus'
import { useChatPresets } from '@/composables/chat/useChatPresets'
import { useActiveChatKey } from '@/composables/chat/useActiveChatKey'
import { chatLinkRequiresApiKey, resolveChatUrl } from '@/utils/chat-links'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const { presets, serverAddress } = useChatPresets()
const { activeKey, loading: keyLoading, error: keyError } = useActiveChatKey()

const chatId = computed(() => Number(route.params.chatId))

const preset = computed(() =>
  Number.isInteger(chatId.value) ? presets.value[chatId.value] : undefined
)

const isWebLink = computed(() => preset.value?.type === 'web')

const requiresActiveKey = computed(
  () => isWebLink.value && preset.value ? chatLinkRequiresApiKey(preset.value.url) : false
)

const iframeSrc = computed(() => {
  if (!preset.value || !isWebLink.value) return ''
  if (requiresActiveKey.value && !activeKey.value) return ''

  return resolveChatUrl({
    template: preset.value.url,
    apiKey: activeKey.value ?? undefined,
    serverAddress: serverAddress.value
  })
})
</script>

<template>
  <div class="chat-page">
    <!-- 预设不存在 -->
    <ElAlert
      v-if="!preset"
      :title="t('chat.presetNotFound')"
      type="warning"
      :closable="false"
    >
      <ElButton @click="router.push('/dashboard')">
        {{ t('error.goHome') }}
      </ElButton>
    </ElAlert>

    <!-- 非 web 链接 -->
    <ElAlert
      v-else-if="!isWebLink"
      :title="t('chat.useSidebarShortcut')"
      type="info"
      :closable="false"
    />

    <!-- 正在加载 Key -->
    <div
      v-else-if="requiresActiveKey && keyLoading"
      class="chat-page__loading"
    >
      {{ t('common.loading') }}
    </div>

    <!-- Key 加载失败或无可用 Key -->
    <ElAlert
      v-else-if="requiresActiveKey && (keyError || !activeKey)"
      :title="t('chat.noApiKey')"
      type="error"
      :closable="false"
    >
      <ElButton
        type="primary"
        @click="router.push('/tokens')"
      >
        {{ t('chat.goToTokens') }}
      </ElButton>
    </ElAlert>

    <!-- iframe 渲染 -->
    <iframe
      v-else-if="iframeSrc"
      :key="iframeSrc"
      :src="iframeSrc"
      class="chat-page__iframe"
      allow="camera; microphone"
      frameborder="0"
    />
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.chat-page__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  color: var(--el-text-color-secondary);
}

.chat-page__iframe {
  flex: 1;
  width: 100%;
  border: none;
}
</style>
