<template>
  <ElDialog
    :model-value="modelValue"
    :title="t('profile.bindWeChat')"
    width="440px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="wechat-bind__body">
      <div
        v-if="qrCodeUrl"
        class="wechat-bind__qr"
      >
        <img
          :src="qrCodeUrl"
          alt="WeChat QR Code"
          class="wechat-bind__qr-img"
        >
        <p class="wechat-bind__tip">
          {{ t('profile.weChatScanTip') }}
        </p>
      </div>
      <ElEmpty
        v-else
        :description="t('profile.qrUnavailable')"
      />

      <!-- 开发测试：手动输入 code -->
      <ElDivider>{{ t('profile.manualBind') }}</ElDivider>
      <div class="wechat-bind__manual">
        <ElInput
          v-model="manualCode"
          :placeholder="t('profile.weChatCodePlaceholder')"
        />
        <ElButton
          type="primary"
          :loading="binding"
          :disabled="!manualCode"
          @click="handleBind"
        >
          {{ t('profile.confirmBind') }}
        </ElButton>
      </div>
    </div>

    <template #footer>
      <ElButton @click="$emit('update:modelValue', false)">
        {{ t('common.close') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { bindWeChat } from '@/api/profile'
import { useStatus } from '@/composables/useStatus'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  bound: []
}>()

const { t } = useI18n()
const { status } = useStatus()
const manualCode = ref('')
const binding = ref(false)

const qrCodeUrl = computed(() => status.value?.wechatQrCodeUrl ?? '')

watch(
  () => props.modelValue,
  (val) => {
    if (!val) {
      manualCode.value = ''
    }
  }
)

async function handleBind(): Promise<void> {
  if (!manualCode.value) return
  binding.value = true
  try {
    await bindWeChat(manualCode.value)
    ElMessage.success(t('profile.weChatBound'))
    emit('bound')
    emit('update:modelValue', false)
  } catch {
    // 错误由 request 拦截器处理
  } finally {
    binding.value = false
  }
}
</script>

<style scoped>
.wechat-bind__body {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);
}

.wechat-bind__qr {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-3);
  align-items: center;
}

.wechat-bind__qr-img {
  width: 200px;
  height: 200px;
}

.wechat-bind__tip {
  margin: 0;
  font-size: var(--ys-font-size-base);
  color: var(--el-text-color-secondary);
}

.wechat-bind__manual {
  display: flex;
  gap: var(--ys-spacing-2);
}

.wechat-bind__manual .ElInput {
  flex: 1;
}
</style>
