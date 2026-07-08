<script setup lang="ts">
/**
 * 渠道基本信息分区：名称 / 类型 / Base URL / 状态。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElAlert, ElFormItem, ElInput, ElOption, ElSelect } from 'element-plus'
import { CHANNEL_TYPE_OPTIONS, CHANNEL_TYPE_WARNINGS, TYPE_TO_KEY_PROMPT } from '@/api/channel/constants'
import { useChannelMutateFormContext } from '@/composables/channel/useChannelMutateForm'

const { t } = useI18n()
const { form, errors } = useChannelMutateFormContext()

const typeWarning = computed(() => {
  const key = CHANNEL_TYPE_WARNINGS[form.type]
  return key ? t(key) : ''
})

const keyPrompt = computed(() => {
  const key = TYPE_TO_KEY_PROMPT[form.type]
  return key ? t(key) : ''
})

const statusOptions = [
  { value: 1, label: 'channel.status.enabled' },
  { value: 2, label: 'channel.status.disabled' }
]

function errorText(key: string | undefined): string {
  return key ? t(key) : ''
}
</script>

<template>
  <div class="channel-section">
    <h3 class="channel-section__title">
      {{ t('channel.edit.basic.title') }}
    </h3>
    <p class="channel-section__subtitle">
      {{ t('channel.edit.basic.subtitle') }}
    </p>

    <el-form-item
      :label="t('channel.edit.basic.name')"
      prop="name"
      :error="errorText(errors.name)"
      required
    >
      <el-input
        v-model="form.name"
        :placeholder="t('channel.edit.basic.namePlaceholder')"
        maxlength="100"
        show-word-limit
        clearable
      />
    </el-form-item>

    <el-form-item
      :label="t('channel.edit.basic.type')"
      prop="type"
      :error="errorText(errors.type)"
      required
    >
      <el-select
        v-model="form.type"
        :placeholder="t('channel.edit.basic.typePlaceholder')"
        filterable
        class="w-full"
      >
        <el-option
          v-for="opt in CHANNEL_TYPE_OPTIONS"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </el-form-item>

    <el-alert
      v-if="typeWarning"
      :title="typeWarning"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <el-form-item
      :label="t('channel.edit.basic.baseUrl')"
      prop="base_url"
      :error="errorText(errors.base_url)"
    >
      <el-input
        v-model="form.base_url"
        :placeholder="t('channel.edit.basic.baseUrlPlaceholder')"
        clearable
      />
    </el-form-item>

    <el-form-item
      :label="t('channel.edit.basic.status')"
      prop="status"
    >
      <el-select
        v-model="form.status"
        class="w-full"
      >
        <el-option
          v-for="opt in statusOptions"
          :key="opt.value"
          :label="t(opt.label)"
          :value="opt.value"
        />
      </el-select>
    </el-form-item>

    <el-alert
      v-if="keyPrompt"
      :title="keyPrompt"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />
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

.w-full {
  width: 100%;
}
</style>
