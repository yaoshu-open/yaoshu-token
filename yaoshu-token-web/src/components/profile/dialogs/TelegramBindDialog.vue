<template>
  <ElDialog
    :model-value="modelValue"
    :title="t('profile.bindTelegram')"
    width="440px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="telegram-bind__body">
      <p class="telegram-bind__hint">
        {{ t('profile.telegramWidgetHint') }}
      </p>

      <p
        v-if="botName"
        class="telegram-bind__bot-name"
      >
        Bot: @{{ botName }}
      </p>

      <!-- Telegram Login Widget 容器（授权后自动 GET 跳转后端 /api/oauth/telegram/bind） -->
      <div
        v-if="botName"
        ref="widgetContainer"
        class="telegram-bind__widget"
      />

      <!-- 未配置 Telegram Bot -->
      <ElAlert
        v-else
        :title="t('profile.telegramNotConfigured')"
        type="warning"
        :closable="false"
        show-icon
      />

      <p
        v-if="botName"
        class="telegram-bind__redirect-hint"
      >
        <i class="i-ep-info-filled" />
        {{ t('profile.telegramBoundHint') }}
      </p>
    </div>

    <template #footer>
      <ElButton @click="$emit('update:modelValue', false)">
        {{ t('common.close') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
/**
 * Telegram 绑定对话框。
 * 嵌入官方 Telegram Login Widget，用户授权后由 Telegram 自动 GET 跳转至
 * 后端 /api/oauth/telegram/bind（带 id/hash/auth_date 等签名参数），
 * 后端校验签名并绑定 telegram_id，绑定成功后跳转回 personal 页。
 *
 * 后端契约：ai-docs/后端设计/API_Contract/契约_公共与系统.md §/api/oauth/telegram/bind。
 *
 * 注意：绑定流程为整页跳转，bound 事件保留以兼容消费方契约，
 * 实际绑定状态在回跳后页面重新加载时自然反映。
 */
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useStatus } from '@/composables/useStatus'

const props = defineProps<{
  modelValue: boolean
}>()

defineEmits<{
  'update:modelValue': [value: boolean]
  bound: []
}>()

const { t } = useI18n()
const { status } = useStatus()

const botName = computed(() => status.value?.telegramBotName ?? '')
const widgetContainer = ref<HTMLDivElement | null>(null)

// 注入 Telegram 官方 Login Widget
function injectWidget(): void {
  const container = widgetContainer.value
  if (!container || !botName.value) return
  // 清理旧 widget，避免重复渲染
  container.innerHTML = ''
  const script = document.createElement('script')
  script.async = true
  script.src = 'https://telegram.org/js/telegram-widget.js?22'
  script.setAttribute('data-telegram-login', botName.value)
  script.setAttribute('data-size', 'large')
  script.setAttribute('data-auth-url', '/api/oauth/telegram/bind')
  script.setAttribute('data-request-access', 'write')
  container.appendChild(script)
}

// dialog 打开或 botName 就绪时注入 widget
watch(
  [() => props.modelValue, botName],
  async ([open, name]) => {
    if (open && name) {
      await nextTick()
      injectWidget()
    }
  }
)
</script>

<style scoped>
.telegram-bind__body {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);
  align-items: center;
}

.telegram-bind__hint {
  margin: 0;
  font-size: var(--ys-font-size-base);
  color: var(--el-text-color-secondary);
  text-align: center;
}

.telegram-bind__bot-name {
  margin: 0;
  font-size: var(--ys-font-size-base);
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.telegram-bind__widget {
  display: flex;
  justify-content: center;
  min-height: 50px;
}

.telegram-bind__redirect-hint {
  display: flex;
  gap: 6px;
  align-items: center;
  margin: 0;
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}

.telegram-bind__redirect-hint i {
  font-size: var(--ys-font-size-base);
}
</style>
