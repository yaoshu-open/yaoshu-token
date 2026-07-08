<script setup lang="ts">
// 包含代码示例生成 + 鉴权 + 支持参数表 + 速率限制。Provider 信息已移至 Overview tab。
import { computed, ref } from 'vue'
import { Key } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useStatus } from '@/composables/useStatus'
import CodeBlock from './CodeBlock.vue'
import GroupBadge from '@/components/common/GroupBadge.vue'
import { replaceModelInPath } from '@/views/pricing/lib/model-helpers'
import {
  buildSupportedParameters,
  buildRateLimits,
  formatRateLimit
} from '@/views/pricing/lib/mock-stats'
import type { PricingModel, EndpointInfo } from '@/api/pricing/types'

const props = defineProps<{
  model: PricingModel
  endpointMap: Record<string, EndpointInfo>
}>()

const { t } = useI18n()
const { status } = useStatus()

type Lang = 'curl' | 'python' | 'typescript' | 'javascript'
const LANG_LABELS: Record<Lang, string> = {
  curl: 'cURL', python: 'Python', typescript: 'TypeScript', javascript: 'JavaScript'
}

const baseUrl = computed(() => {
  const candidate =
    status.value?.serverAddress ??
    typeof window !== 'undefined' ? window.location.origin : 'https://api.example.com'
  if (candidate && typeof candidate === 'string') return candidate.replace(/\/$/, '')
  return 'https://api.example.com'
})

const endpoints = computed(() => {
  const types = props.model.supportedEndpointTypes || []
  return types
    .map((type) => {
      const info = props.endpointMap[type]
      const rawPath = info?.path || ''
      const path = rawPath && rawPath.includes('{model}')
        ? replaceModelInPath(rawPath, props.model.modelName || '')
        : rawPath
      return { type, path, method: info?.method || 'POST' }
    })
    .filter((e) => Boolean(e.path))
})

const activeEndpointType = ref('')
const activeLang = ref<Lang>('curl')

const activeEndpoint = computed(() =>
  endpoints.value.find((e) => e.type === activeEndpointType.value) ?? endpoints.value[0]
)

// 代码示例生成
function buildChatSample(lang: Lang, ctx: { baseUrl: string; apiKeyEnv: string; modelName: string; endpointPath: string }): string {
  const url = `${ctx.baseUrl}${ctx.endpointPath}`
  const body = JSON.stringify({
    model: ctx.modelName,
    messages: [{ role: 'user', content: 'Explain quantum entanglement in one paragraph.' }]
  }, null, 2)
  if (lang === 'curl') {
    return `curl ${url} \\\n  -H "Authorization: Bearer $${ctx.apiKeyEnv}" \\\n  -H "Content-Type: application/json" \\\n  -d '${body.replace(/\n/g, '\n     ')}'`
  }
  if (lang === 'python') {
    return `from openai import OpenAI\n\nclient = OpenAI(\n    base_url="${ctx.baseUrl}/v1",\n    api_key="<YOUR_API_KEY>",\n)\n\ncompletion = client.chat.completions.create(\n    model="${ctx.modelName}",\n    messages=[\n        {"role": "user", "content": "Explain quantum entanglement in one paragraph."}\n    ],\n)\n\nprint(completion.choices[0].message.content)`
  }
  if (lang === 'typescript') {
    return `import OpenAI from 'openai'\n\nconst client = new OpenAI({\n  baseURL: '${ctx.baseUrl}/v1',\n  apiKey: process.env.${ctx.apiKeyEnv},\n})\n\nconst completion = await client.chat.completions.create({\n  model: '${ctx.modelName}',\n  messages: [{ role: 'user', content: 'Explain quantum entanglement in one paragraph.' }],\n})\n\nconsole.log(completion.choices[0].message.content)`
  }
  return `const response = await fetch('${url}', {\n  method: 'POST',\n  headers: {\n    Authorization: \`Bearer \${process.env.${ctx.apiKeyEnv}}\`,\n    'Content-Type': 'application/json',\n  },\n  body: JSON.stringify(${body}),\n})\n\nconst data = await response.json()\nconsole.log(data)`
}

function buildSample(lang: Lang, endpointType: string, ctx: { baseUrl: string; apiKeyEnv: string; modelName: string; endpointPath: string }): string {
  if (endpointType === 'anthropic') {
    const url = `${ctx.baseUrl}${ctx.endpointPath}`
    const body = JSON.stringify({ model: ctx.modelName, max_tokens: 1024, messages: [{ role: 'user', content: 'Explain quantum entanglement.' }] }, null, 2)
    if (lang === 'curl') return `curl ${url} \\\n  -H "x-api-key: $${ctx.apiKeyEnv}" \\\n  -H "anthropic-version: 2023-06-01" \\\n  -H "Content-Type: application/json" \\\n  -d '${body.replace(/\n/g, '\n     ')}'`
    if (lang === 'python') return `import anthropic\n\nclient = anthropic.Anthropic(\n    base_url="${ctx.baseUrl}",\n    api_key="<YOUR_API_KEY>",\n)\n\nmessage = client.messages.create(\n    model="${ctx.modelName}",\n    max_tokens=1024,\n    messages=[{"role": "user", "content": "Explain quantum entanglement."}],\n)\n\nprint(message.content[0].text)`
    if (lang === 'typescript') return `import Anthropic from '@anthropic-ai/sdk'\n\nconst client = new Anthropic({\n  baseURL: '${ctx.baseUrl}',\n  apiKey: process.env.${ctx.apiKeyEnv},\n})\n\nconst message = await client.messages.create({\n  model: '${ctx.modelName}',\n  max_tokens: 1024,\n  messages: [{ role: 'user', content: 'Explain quantum entanglement.' }],\n})\n\nconsole.log(message.content[0].text)`
    return `const response = await fetch('${url}', {\n  method: 'POST',\n  headers: {\n    'x-api-key': process.env.${ctx.apiKeyEnv},\n    'anthropic-version': '2023-06-01',\n    'Content-Type': 'application/json',\n  },\n  body: JSON.stringify(${body}),\n})\n\nconst data = await response.json()\nconsole.log(data.content[0].text)`
  }
  if (endpointType === 'gemini') {
    const url = `${ctx.baseUrl}${ctx.endpointPath}?key=$${ctx.apiKeyEnv}`
    const body = JSON.stringify({ contents: [{ parts: [{ text: 'Explain quantum entanglement.' }] }] }, null, 2)
    if (lang === 'curl') return `curl '${url}' \\\n  -H 'Content-Type: application/json' \\\n  -d '${body.replace(/\n/g, '\n     ')}'`
    if (lang === 'python') return `import google.generativeai as genai\n\ngenai.configure(api_key="<YOUR_API_KEY>")\n\nmodel = genai.GenerativeModel("${ctx.modelName}")\nresponse = model.generate_content("Explain quantum entanglement.")\n\nprint(response.text)`
    if (lang === 'typescript') return `import { GoogleGenerativeAI } from '@google/generative-ai'\n\nconst genAI = new GoogleGenerativeAI(process.env.${ctx.apiKeyEnv}!)\nconst model = genAI.getGenerativeModel({ model: '${ctx.modelName}' })\n\nconst result = await model.generateContent('Explain quantum entanglement.')\nconsole.log(result.response.text())`
    return `const response = await fetch('${url}', {\n  method: 'POST',\n  headers: { 'Content-Type': 'application/json' },\n  body: JSON.stringify(${body}),\n})\n\nconst data = await response.json()\nconsole.log(data.candidates[0].content.parts[0].text)`
  }
  if (endpointType === 'embeddings' || endpointType === 'jina-rerank') {
    return buildChatSample(lang, { ...ctx, endpointPath: ctx.endpointPath })
  }
  if (endpointType === 'image-generation') {
    const url = `${ctx.baseUrl}${ctx.endpointPath}`
    const body = JSON.stringify({ model: ctx.modelName, prompt: 'A serene koi pond at sunset, ukiyo-e style.', size: '1024x1024', n: 1 }, null, 2)
    if (lang === 'curl') return `curl ${url} \\\n  -H "Authorization: Bearer $${ctx.apiKeyEnv}" \\\n  -H "Content-Type: application/json" \\\n  -d '${body.replace(/\n/g, '\n     ')}'`
    if (lang === 'python') return `from openai import OpenAI\n\nclient = OpenAI(base_url="${ctx.baseUrl}/v1", api_key="<YOUR_API_KEY>")\n\nresponse = client.images.generate(\n    model="${ctx.modelName}",\n    prompt="A serene koi pond at sunset, ukiyo-e style.",\n    size="1024x1024",\n    n=1,\n)\n\nprint(response.data[0].url)`
    if (lang === 'typescript') return `import OpenAI from 'openai'\n\nconst client = new OpenAI({\n  baseURL: '${ctx.baseUrl}/v1',\n  apiKey: process.env.${ctx.apiKeyEnv},\n})\n\nconst response = await client.images.generate({\n  model: '${ctx.modelName}',\n  prompt: 'A serene koi pond at sunset, ukiyo-e style.',\n  size: '1024x1024',\n  n: 1,\n})\n\nconsole.log(response.data[0].url)`
    return `const response = await fetch('${url}', {\n  method: 'POST',\n  headers: {\n    Authorization: \`Bearer \${process.env.${ctx.apiKeyEnv}}\`,\n    'Content-Type': 'application/json',\n  },\n  body: JSON.stringify(${body}),\n})\n\nconst data = await response.json()\nconsole.log(data.data[0].url)`
  }
  return buildChatSample(lang, ctx)
}

const code = computed(() => {
  if (!activeEndpoint.value) return ''
  return buildSample(activeLang.value, activeEndpoint.value.type, {
    baseUrl: baseUrl.value,
    apiKeyEnv: 'NEW_API_KEY',
    modelName: props.model.modelName || '',
    endpointPath: activeEndpoint.value.path
  })
})

const params = computed(() => buildSupportedParameters(props.model))
const rateLimits = computed(() => buildRateLimits(props.model))

// 初始化 activeEndpointType
if (endpoints.value.length > 0) {
  activeEndpointType.value = endpoints.value[0].type
}
</script>

<template>
  <div
    v-if="endpoints.length > 0"
    class="model-api"
  >
    <!-- 代码示例 -->
    <section class="model-api__section">
      <h3 class="model-api__title">
        {{ t('pricing.codeSamples') }}
      </h3>
      <div class="model-api__tabs">
        <el-tabs
          v-if="endpoints.length > 1"
          v-model="activeEndpointType"
          type="card"
        >
          <el-tab-pane
            v-for="ep in endpoints"
            :key="ep.type"
            :label="ep.type"
            :name="ep.type"
          />
        </el-tabs>
        <el-radio-group
          v-model="activeLang"
          size="small"
        >
          <el-radio-button
            v-for="(label, lang) in LANG_LABELS"
            :key="lang"
            :value="lang"
          >
            {{ label }}
          </el-radio-button>
        </el-radio-group>
      </div>
      <CodeBlock
        :code="code"
        :language="activeLang === 'curl' ? 'bash' : activeLang"
      />
      <p class="model-api__hint">
        {{ t('pricing.replaceApiKey') }}
        <code>&lt;YOUR_API_KEY&gt;</code>
      </p>
    </section>

    <!-- 鉴权 -->
    <section class="model-api__section">
      <h3 class="model-api__title">
        <el-icon><Key /></el-icon>
        {{ t('pricing.authentication') }}
      </h3>
      <div class="model-api__auth">
        <p>{{ t('pricing.authDesc') }}</p>
        <p class="model-api__auth-hint">{{ t('pricing.authGenerateToken') }}</p>
      </div>
    </section>

    <!-- 支持参数表 -->
    <section
      v-if="params.length > 0"
      class="model-api__section"
    >
      <h3 class="model-api__title">
        {{ t('pricing.supportedParameters') }}
      </h3>
      <el-table
        :data="params"
        size="small"
      >
        <el-table-column
          :label="t('pricing.parameter')"
          min-width="140"
        >
          <template #default="{ row }">
            <code class="model-api__param-name">{{ row.name }}</code>
            <el-tag
              v-if="row.required"
              size="small"
              type="danger"
            >
              {{ t('pricing.required') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('pricing.type')"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              size="small"
              type="info"
            >
              {{ row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('pricing.defaultRange')"
          width="140"
        >
          <template #default="{ row }">
            <span v-if="row.defaultValue !== undefined">
              = <code>{{ row.defaultValue }}</code>
              <span
                v-if="row.range"
                class="model-api__range"
              >{{ row.range }}</span>
            </span>
            <span v-else-if="row.range">{{ row.range }}</span>
            <span v-else-if="row.enumValues">
              <code
                v-for="v in row.enumValues"
                :key="v"
                class="model-api__enum"
              >{{ v }}</code>
            </span>
          </template>
        </el-table-column>
        <el-table-column :label="t('pricing.description')">
          <template #default="{ row }">
            {{ t(row.descriptionKey) }}
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 速率限制 -->
    <section
      v-if="rateLimits.length > 0"
      class="model-api__section"
    >
      <h3 class="model-api__title">
        {{ t('pricing.rateLimits') }}
      </h3>
      <el-table
        :data="rateLimits"
        size="small"
      >
        <el-table-column :label="t('pricing.group')">
          <template #default="{ row }">
            <GroupBadge
              :group="row.group"
              size="sm"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="RPM"
          align="right"
        >
          <template #default="{ row }">
            {{ formatRateLimit(row.rpm) }}
          </template>
        </el-table-column>
        <el-table-column
          label="TPM"
          align="right"
        >
          <template #default="{ row }">
            {{ formatRateLimit(row.tpm) }}
          </template>
        </el-table-column>
        <el-table-column
          label="RPD"
          align="right"
        >
          <template #default="{ row }">
            {{ formatRateLimit(row.rpd) }}
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<style scoped lang="scss">
.model-api {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-6);

  &__section {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
  }

  &__title {
    display: inline-flex;
    gap: var(--ys-spacing-1);
    align-items: center;
    margin: 0;
    font-size: var(--ys-font-size-xs);
    font-weight: 600;
    color: var(--el-text-color-secondary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  &__tabs {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-2);
    align-items: center;
    justify-content: space-between;
  }

  &__hint {
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);

    code {
      padding: 1px var(--ys-spacing-1);
      font-family: monospace;
      font-size: 11px;
      background: var(--el-fill-color);
      border-radius: 3px;
    }
  }

  &__param-name {
    margin-right: 4px;
    font-family: monospace;
    font-weight: 500;
  }

  &__range { margin-left: 4px; font-size: var(--ys-font-size-xs); color: var(--el-text-color-secondary); }
  &__enum { padding: 1px var(--ys-spacing-1); margin-right: 2px; font-family: monospace; font-size: var(--ys-font-size-xs); background: var(--el-fill-color); border-radius: 3px; }

  &__auth {
    padding: var(--ys-spacing-3);
    background: var(--el-fill-color-lighter);
    border: 1px solid var(--el-border-color);
    border-radius: var(--ys-radius-md);

    p {
      margin: 0;
      font-size: var(--ys-font-size-sm);
      line-height: 1.5;
    }

    &-hint {
      margin-top: 6px;
      font-size: var(--ys-font-size-xs);
      color: var(--el-text-color-secondary);
    }
  }
}
</style>
