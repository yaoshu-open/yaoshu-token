<script setup lang="ts">
// Chat2Link 跳转页：获取第一个 web 聊天预设 + API Key，跳转到外部聊天客户端
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useChatPresets } from '@/composables/chat/useChatPresets'
import { useActiveChatKey } from '@/composables/chat/useActiveChatKey'
import { resolveChatUrl } from '@/utils/chat-links'

const router = useRouter()
const { t } = useI18n()
const { presets, serverAddress } = useChatPresets()
const { activeKey, loading: keyLoading } = useActiveChatKey()

const loading = ref(true)

onMounted(async () => {
  try {
    // 等待 Key 加载完成
    if (keyLoading.value) {
      await new Promise<void>((resolve) => {
        const unwatch = setInterval(() => {
          if (!keyLoading.value) {
            clearInterval(unwatch)
            resolve()
          }
        }, 100)
      })
    }

    const firstWebPreset = presets.value.find((p) => p.type === 'web')

    if (!firstWebPreset) {
      ElMessage.error(t('chat.noWebPreset'))
      loading.value = false
      return
    }

    if (!activeKey.value) {
      ElMessage.error(t('chat.noApiKey'))
      router.push('/tokens')
      return
    }

    const url = resolveChatUrl({
      template: firstWebPreset.url,
      apiKey: activeKey.value,
      serverAddress: serverAddress.value
    })

    window.location.href = url
  } catch {
    ElMessage.error(t('chat.redirectFailed'))
    loading.value = false
  }
})
</script>

<template>
  <div
    v-loading="loading"
    class="chat2link-page"
  >
    <p>{{ t('chat.redirecting') }}</p>
  </div>
</template>

<style scoped>
.chat2link-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  color: var(--el-text-color-secondary);
}
</style>
