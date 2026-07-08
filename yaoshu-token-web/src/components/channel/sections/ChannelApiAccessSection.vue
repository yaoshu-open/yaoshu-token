<script setup lang="ts">
/**
 * 渠道 API 访问分区：优先级 / 权重 / 测试模型 / 自动禁用 / 其他。
 */
import { useI18n } from 'vue-i18n'
import { ElFormItem, ElInput, ElInputNumber, ElOption, ElSelect } from 'element-plus'
import { useChannelMutateFormContext } from '@/composables/channel/useChannelMutateForm'

const { t } = useI18n()
const { form } = useChannelMutateFormContext()

const autoBanOptions = [
  { value: 0, label: 'channel.edit.apiAccess.autoBanDisabled' },
  { value: 1, label: 'channel.edit.apiAccess.autoBanEnabled' }
]
</script>

<template>
  <div class="channel-section">
    <h3 class="channel-section__title">
      {{ t('channel.edit.apiAccess.title') }}
    </h3>
    <p class="channel-section__subtitle">
      {{ t('channel.edit.apiAccess.subtitle') }}
    </p>

    <div class="channel-section__row">
      <el-form-item
        :label="t('channel.edit.apiAccess.priority')"
        prop="priority"
      >
        <el-input-number
          v-model="form.priority"
          :min="0"
          :max="999"
          controls-position="right"
          class="w-full"
        />
      </el-form-item>

      <el-form-item
        :label="t('channel.edit.apiAccess.weight')"
        prop="weight"
      >
        <el-input-number
          v-model="form.weight"
          :min="0"
          :max="999"
          controls-position="right"
          class="w-full"
        />
      </el-form-item>
    </div>

    <el-form-item
      :label="t('channel.edit.apiAccess.testModel')"
      prop="test_model"
    >
      <el-input
        v-model="form.test_model"
        :placeholder="t('channel.edit.apiAccess.testModelPlaceholder')"
        clearable
      />
    </el-form-item>

    <el-form-item
      :label="t('channel.edit.apiAccess.autoBan')"
      prop="auto_ban"
    >
      <el-select
        v-model="form.auto_ban"
        class="w-full"
      >
        <el-option
          v-for="opt in autoBanOptions"
          :key="opt.value"
          :label="t(opt.label)"
          :value="opt.value"
        />
      </el-select>
    </el-form-item>

    <el-form-item
      :label="t('channel.edit.apiAccess.other')"
      prop="other"
    >
      <el-input
        v-model="form.other"
        :placeholder="t('channel.edit.apiAccess.otherPlaceholder')"
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

.channel-section__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--ys-spacing-4);
}

.w-full {
  width: 100%;
}
</style>
