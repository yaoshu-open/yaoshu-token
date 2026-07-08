<template>
  <ElCard shadow="never">
    <template #header>
      <div class="lang-card__header">
        <ElIcon :size="18">
          <ChatDotRound />
        </ElIcon>
        <span>{{ t('profile.languagePreferences') }}</span>
      </div>
    </template>
    <div class="lang-card__body">
      <p class="lang-card__desc">
        {{ t('profile.languageDesc') }}
      </p>
      <ElSelect
        v-model="selectedLang"
        :loading="saving"
        style="width: 240px;"
      >
        <ElOption
          v-for="lang in languageOptions"
          :key="lang.value"
          :label="lang.label"
          :value="lang.value"
        />
      </ElSelect>
      <ElButton
        type="primary"
        :loading="saving"
        :disabled="!hasChanged"
        @click="handleSave"
      >
        {{ t('common.save') }}
      </ElButton>
    </div>
  </ElCard>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { ChatDotRound } from '@element-plus/icons-vue'
import { updateUserProfile } from '@/api/profile'
import { parseUserSettings } from '@/utils/profile'
import type { UserProfile } from '@/api/profile/types'

const props = defineProps<{
  profile: UserProfile | null
}>()

const emit = defineEmits<{
  profileUpdate: []
}>()

const { t } = useI18n()
const selectedLang = ref('zh-CN')
const saving = ref(false)

const languageOptions = [
  { value: 'zh-CN', label: t('profile.langZhCN') },
  { value: 'en', label: t('profile.langEn') },
  { value: 'ja', label: t('profile.langJa') },
]

const currentLang = computed(() => {
  if (!props.profile) return 'zh-CN'
  const settings = parseUserSettings(props.profile.setting)
  return settings.language || 'zh-CN'
})

const hasChanged = computed(() => selectedLang.value !== currentLang.value)

watch(
  () => props.profile,
  (val) => {
    if (val) {
      selectedLang.value = currentLang.value
    }
  },
  { immediate: true }
)

async function handleSave(): Promise<void> {
  saving.value = true
  try {
    await updateUserProfile({ language: selectedLang.value })
    ElMessage.success(t('profile.languageSaved'))
    emit('profileUpdate')
  } catch {
    // 错误由 request 拦截器处理
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.lang-card__header {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
  font-weight: 600;
}

.lang-card__body {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);
}

.lang-card__desc {
  margin: 0;
  font-size: var(--ys-font-size-base);
  color: var(--el-text-color-secondary);
}
</style>
