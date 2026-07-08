<script setup lang="ts">
/**
 * 渠道认证分区：密钥 / OpenAI Organization。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElAlert, ElButton, ElFormItem, ElInput } from 'element-plus'
import { useChannelMutateFormContext } from '@/composables/channel/useChannelMutateForm'
import { deduplicateKeys } from '@/lib/channel/channel-form'

const { t } = useI18n()
const { form, errors, isEditing } = useChannelMutateFormContext()

const keyPlaceholder = computed(() => {
  if (isEditing.value) {
    return t('channel.edit.auth.keyPlaceholderEdit')
  }
  return t('channel.edit.auth.keyPlaceholder')
})

function handleDeduplicate(): void {
  if (!form.key) return
  const result = deduplicateKeys(form.key)
  form.key = result.deduplicatedText
  if (result.removedCount > 0) {
    // 静默去重，不弹消息（保持表单操作轻量）
  }
}

function errorText(key: string | undefined): string {
  return key ? t(key) : ''
}
</script>

<template>
  <div class="channel-section">
    <h3 class="channel-section__title">
      {{ t('channel.edit.auth.title') }}
    </h3>
    <p class="channel-section__subtitle">
      {{ t('channel.edit.auth.subtitle') }}
    </p>

    <el-form-item
      :label="t('channel.edit.auth.key')"
      prop="key"
      :error="errorText(errors.key)"
      :required="!isEditing"
    >
      <el-input
        v-model="form.key"
        type="password"
        show-password
        :placeholder="keyPlaceholder"
      />
      <div class="channel-section__hint">
        <span>{{ t('channel.edit.auth.keyHint') }}</span>
        <el-button
          v-if="form.key"
          link
          type="primary"
          size="small"
          @click="handleDeduplicate"
        >
          {{ t('channel.edit.auth.deduplicate') }}
        </el-button>
      </div>
    </el-form-item>

    <el-alert
      v-if="isEditing"
      :title="t('channel.edit.auth.keyEditNotice')"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <el-form-item
      :label="t('channel.edit.auth.openaiOrganization')"
      prop="openai_organization"
    >
      <el-input
        v-model="form.openai_organization"
        :placeholder="t('channel.edit.auth.openaiOrganizationPlaceholder')"
        clearable
      />
    </el-form-item>
  </div>
</template>

<style scoped>
.channel-section__title {
  margin: 0 0 var(--ys-spacing-1);
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.channel-section__subtitle {
  margin: 0 0 var(--ys-spacing-4);
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}

.channel-section__hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-top: 4px;
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}
</style>
