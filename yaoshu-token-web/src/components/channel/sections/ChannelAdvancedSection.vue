<script setup lang="ts">
/**
 * 渠道高级设置分区：标签 / 备注 / 状态码映射 / 参数覆盖 / 请求头覆盖 / 渠道额外设置 / 类型特定设置 / 上游模型更新。
 *
 * 范围（第二批）：textarea + JSON 校验。参数覆盖可视化编辑器（第三批 O5 接入）。
 */
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ElAlert,
  ElButton,
  ElDivider,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElSwitch
} from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import { MODEL_FETCHABLE_TYPES, UA_OVERRIDE_MODE_OPTIONS } from '@/api/channel/constants'
import { useChannelMutateFormContext } from '@/composables/channel/useChannelMutateForm'
import ParamOverrideEditorDialog from '../dialogs/ParamOverrideEditorDialog.vue'

const { t } = useI18n()
const { form, errors } = useChannelMutateFormContext()

// 参数覆盖可视化编辑器（第三批 O5 接入）
const paramOverrideEditorOpen = ref(false)

// ============================================================================
// JSON 模板
// ============================================================================

const PARAM_OVERRIDE_TEMPLATE = JSON.stringify(
  {
    operations: [
      {
        path: 'temperature',
        mode: 'set',
        value: 0.7,
        conditions: [{ path: 'model', mode: 'prefix', value: 'gpt' }],
        logic: 'AND'
      }
    ]
  },
  null,
  2
)
const HEADER_OVERRIDE_TEMPLATE = JSON.stringify(
  {
    'User-Agent':
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36',
    Authorization: 'Bearer {api_key}'
  },
  null,
  2
)
const STATUS_CODE_MAPPING_TEMPLATE = JSON.stringify({ '404': '500' }, null, 2)

// ============================================================================
// 类型特定设置可见性
// ============================================================================

const showVertexSettings = computed(() => form.type === 41)
const showAzureSettings = computed(() => form.type === 3)
const showOpenRouterSettings = computed(() => form.type === 20)
const showAwsSettings = computed(() => form.type === 33)
const showOpenAIPassthrough = computed(() => form.type === 1)
const showAnthropicPassthrough = computed(() => form.type === 14)
const showServiceTier = computed(() => form.type === 1 || form.type === 14)
const showUpstreamUpdate = computed(() => MODEL_FETCHABLE_TYPES.has(form.type))

// ============================================================================
// 操作
// ============================================================================

function fillTemplate(field: 'model_mapping' | 'param_override' | 'header_override' | 'status_code_mapping', template: string): void {
  form[field] = template
}

function errorText(key: string | undefined): string {
  return key ? t(key) : ''
}
</script>

<template>
  <div class="channel-section">
    <h3 class="channel-section__title">
      {{ t('channel.edit.advanced.title') }}
    </h3>
    <p class="channel-section__subtitle">
      {{ t('channel.edit.advanced.subtitle') }}
    </p>

    <!-- 标签与备注 -->
    <div class="channel-section__row">
      <el-form-item
        :label="t('channel.edit.advanced.tag')"
        prop="tag"
      >
        <el-input
          v-model="form.tag"
          :placeholder="t('channel.edit.advanced.tagPlaceholder')"
          clearable
        />
      </el-form-item>

      <el-form-item
        :label="t('channel.edit.advanced.remark')"
        prop="remark"
      >
        <el-input
          v-model="form.remark"
          :placeholder="t('channel.edit.advanced.remarkPlaceholder')"
          clearable
        />
      </el-form-item>
    </div>

    <!-- 状态码映射 -->
    <el-form-item
      :label="t('channel.edit.advanced.statusCodeMapping')"
      prop="status_code_mapping"
      :error="errorText(errors.status_code_mapping)"
    >
      <el-input
        v-model="form.status_code_mapping"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 6 }"
        :placeholder="t('channel.edit.advanced.statusCodeMappingPlaceholder')"
      />
      <div class="channel-section__hint">
        <span>{{ t('channel.edit.advanced.statusCodeMappingHint') }}</span>
        <el-button
          link
          type="primary"
          size="small"
          @click="fillTemplate('status_code_mapping', STATUS_CODE_MAPPING_TEMPLATE)"
        >
          {{ t('channel.edit.advanced.fillTemplate') }}
        </el-button>
      </div>
    </el-form-item>

    <!-- 参数覆盖 -->
    <el-form-item
      :label="t('channel.edit.advanced.paramOverride')"
      prop="param_override"
      :error="errorText(errors.param_override)"
    >
      <el-input
        v-model="form.param_override"
        type="textarea"
        :autosize="{ minRows: 3, maxRows: 10 }"
        :placeholder="t('channel.edit.advanced.paramOverridePlaceholder')"
      />
      <div class="channel-section__hint">
        <span>{{ t('channel.edit.advanced.paramOverrideHint') }}</span>
        <div class="channel-section__hint-actions">
          <el-button
            link
            type="primary"
            size="small"
            :icon="MagicStick"
            @click="paramOverrideEditorOpen = true"
          >
            {{ t('channel.edit.advanced.visualEdit') }}
          </el-button>
          <el-button
            link
            type="primary"
            size="small"
            @click="fillTemplate('param_override', PARAM_OVERRIDE_TEMPLATE)"
          >
            {{ t('channel.edit.advanced.fillTemplate') }}
          </el-button>
        </div>
      </div>
    </el-form-item>

    <!-- 请求头覆盖 -->
    <el-form-item
      :label="t('channel.edit.advanced.headerOverride')"
      prop="header_override"
      :error="errorText(errors.header_override)"
    >
      <el-input
        v-model="form.header_override"
        type="textarea"
        :autosize="{ minRows: 3, maxRows: 10 }"
        :placeholder="t('channel.edit.advanced.headerOverridePlaceholder')"
      />
      <div class="channel-section__hint">
        <span>{{ t('channel.edit.advanced.headerOverrideHint') }}</span>
        <el-button
          link
          type="primary"
          size="small"
          @click="fillTemplate('header_override', HEADER_OVERRIDE_TEMPLATE)"
        >
          {{ t('channel.edit.advanced.fillTemplate') }}
        </el-button>
      </div>
    </el-form-item>

    <el-divider content-position="left">
      <span class="channel-section__divider">{{ t('channel.edit.advanced.extraSettings') }}</span>
    </el-divider>

    <!-- 渠道额外设置 -->
    <div class="channel-section__row">
      <el-form-item
        :label="t('channel.edit.advanced.forceFormat')"
        prop="force_format"
      >
        <el-switch v-model="form.force_format" />
      </el-form-item>

      <el-form-item
        :label="t('channel.edit.advanced.thinkingToContent')"
        prop="thinking_to_content"
      >
        <el-switch v-model="form.thinking_to_content" />
      </el-form-item>
    </div>

    <el-form-item
      :label="t('channel.edit.advanced.uaOverrideMode')"
      prop="ua_override_mode"
    >
      <el-select
        v-model="form.ua_override_mode"
        class="w-full"
      >
        <el-option
          v-for="opt in UA_OVERRIDE_MODE_OPTIONS"
          :key="opt.value"
          :label="t(opt.label)"
          :value="opt.value"
        />
      </el-select>
      <div class="channel-section__hint">
        <span>{{ t('channel.edit.advanced.uaOverrideHint') }}</span>
      </div>
    </el-form-item>

    <el-form-item
      :label="t('channel.edit.advanced.proxy')"
      prop="proxy"
    >
      <el-input
        v-model="form.proxy"
        :placeholder="t('channel.edit.advanced.proxyPlaceholder')"
        clearable
      />
    </el-form-item>

    <div class="channel-section__row">
      <el-form-item
        :label="t('channel.edit.advanced.passThroughBody')"
        prop="pass_through_body_enabled"
      >
        <el-switch v-model="form.pass_through_body_enabled" />
      </el-form-item>

      <el-form-item
        :label="t('channel.edit.advanced.systemPromptOverride')"
        prop="system_prompt_override"
      >
        <el-switch v-model="form.system_prompt_override" />
      </el-form-item>
    </div>

    <el-form-item
      :label="t('channel.edit.advanced.systemPrompt')"
      prop="system_prompt"
    >
      <el-input
        v-model="form.system_prompt"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 6 }"
        :placeholder="t('channel.edit.advanced.systemPromptPlaceholder')"
      />
    </el-form-item>

    <!-- 类型特定设置 -->
    <template v-if="showVertexSettings || showAzureSettings || showOpenRouterSettings || showAwsSettings">
      <el-divider content-position="left">
        <span class="channel-section__divider">{{ t('channel.edit.advanced.typeSpecific') }}</span>
      </el-divider>

      <el-form-item
        v-if="showVertexSettings"
        :label="t('channel.edit.advanced.vertexKeyType')"
        prop="vertex_key_type"
      >
        <el-select
          v-model="form.vertex_key_type"
          class="w-full"
        >
          <el-option
            label="JSON"
            value="json"
          />
          <el-option
            label="API Key"
            value="api_key"
          />
        </el-select>
      </el-form-item>

      <el-form-item
        v-if="showAzureSettings"
        :label="t('channel.edit.advanced.azureResponsesVersion')"
        prop="azure_responses_version"
      >
        <el-input
          v-model="form.azure_responses_version"
          :placeholder="t('channel.edit.advanced.azureResponsesVersionPlaceholder')"
          clearable
        />
      </el-form-item>

      <el-form-item
        v-if="showOpenRouterSettings"
        :label="t('channel.edit.advanced.enterpriseAccount')"
        prop="is_enterprise_account"
      >
        <el-switch v-model="form.is_enterprise_account" />
      </el-form-item>

      <el-form-item
        v-if="showAwsSettings"
        :label="t('channel.edit.advanced.awsKeyType')"
        prop="aws_key_type"
      >
        <el-select
          v-model="form.aws_key_type"
          class="w-full"
        >
          <el-option
            label="AK/SK"
            value="ak_sk"
          />
          <el-option
            label="API Key"
            value="api_key"
          />
        </el-select>
      </el-form-item>
    </template>

    <!-- 字段透传控制 -->
    <template v-if="showServiceTier || showOpenAIPassthrough || showAnthropicPassthrough">
      <el-divider content-position="left">
        <span class="channel-section__divider">{{ t('channel.edit.advanced.passthrough') }}</span>
      </el-divider>

      <el-form-item
        v-if="showServiceTier"
        :label="t('channel.edit.advanced.allowServiceTier')"
        prop="allow_service_tier"
      >
        <el-switch v-model="form.allow_service_tier" />
      </el-form-item>

      <el-form-item
        v-if="showOpenAIPassthrough"
        :label="t('channel.edit.advanced.disableStore')"
        prop="disable_store"
      >
        <el-switch v-model="form.disable_store" />
      </el-form-item>

      <el-form-item
        v-if="showOpenAIPassthrough"
        :label="t('channel.edit.advanced.allowSafetyIdentifier')"
        prop="allow_safety_identifier"
      >
        <el-switch v-model="form.allow_safety_identifier" />
      </el-form-item>

      <el-form-item
        v-if="showOpenAIPassthrough"
        :label="t('channel.edit.advanced.allowIncludeObfuscation')"
        prop="allow_include_obfuscation"
      >
        <el-switch v-model="form.allow_include_obfuscation" />
      </el-form-item>

      <el-form-item
        v-if="showServiceTier"
        :label="t('channel.edit.advanced.allowInferenceGeo')"
        prop="allow_inference_geo"
      >
        <el-switch v-model="form.allow_inference_geo" />
      </el-form-item>

      <el-form-item
        v-if="showAnthropicPassthrough"
        :label="t('channel.edit.advanced.allowSpeed')"
        prop="allow_speed"
      >
        <el-switch v-model="form.allow_speed" />
      </el-form-item>

      <el-form-item
        v-if="showAnthropicPassthrough"
        :label="t('channel.edit.advanced.claudeBetaQuery')"
        prop="claude_beta_query"
      >
        <el-switch v-model="form.claude_beta_query" />
      </el-form-item>
    </template>

    <!-- 上游模型更新设置 -->
    <template v-if="showUpstreamUpdate">
      <el-divider content-position="left">
        <span class="channel-section__divider">{{ t('channel.edit.advanced.upstreamUpdate') }}</span>
      </el-divider>

      <el-alert
        :title="t('channel.edit.advanced.upstreamUpdateHint')"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />

      <el-form-item
        :label="t('channel.edit.advanced.upstreamCheckEnabled')"
        prop="upstream_model_update_check_enabled"
      >
        <el-switch v-model="form.upstream_model_update_check_enabled" />
      </el-form-item>

      <el-form-item
        v-if="form.upstream_model_update_check_enabled"
        :label="t('channel.edit.advanced.upstreamAutoSyncEnabled')"
        prop="upstream_model_update_auto_sync_enabled"
      >
        <el-switch v-model="form.upstream_model_update_auto_sync_enabled" />
      </el-form-item>

      <el-form-item
        v-if="form.upstream_model_update_check_enabled"
        :label="t('channel.edit.advanced.upstreamIgnoredModels')"
        prop="upstream_model_update_ignored_models"
      >
        <el-input
          v-model="form.upstream_model_update_ignored_models"
          :placeholder="t('channel.edit.advanced.upstreamIgnoredModelsPlaceholder')"
          clearable
        />
      </el-form-item>
    </template>

    <!-- 参数覆盖可视化编辑器（第三批 O5 接入） -->
    <ParamOverrideEditorDialog
      v-model="paramOverrideEditorOpen"
      :value="form.param_override"
      @save="(val: string) => (form.param_override = val)"
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

.channel-section__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--ys-spacing-4);
}

.channel-section__hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-top: 4px;
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
  white-space: pre-line;
}

.channel-section__hint-actions {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
}

.channel-section__divider {
  font-size: var(--ys-font-size-sm);
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.w-full {
  width: 100%;
}
</style>
