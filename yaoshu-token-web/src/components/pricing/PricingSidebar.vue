<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import LobeIcon from '@/components/common/VendorIcon.vue'
import {
  FILTER_ALL,
  QUOTA_TYPES,
  ENDPOINT_TYPES
} from '@/api/pricing/constants'
import { parseTags } from '@/views/pricing/lib/filters'
import type { PricingVendor, PricingModel } from '@/api/pricing/types'
import { isFeatureHidden } from '@/plugins/spi/registry'

const props = defineProps<{
  quotaTypeFilter: string
  endpointTypeFilter: string
  vendorFilter: string
  groupFilter: string
  tagFilter: string
  vendors: PricingVendor[]
  groups: string[]
  groupRatios: Record<string, number>
  tags: string[]
  models: PricingModel[]
  hasActiveFilters: boolean
}>()

const emit = defineEmits<{
  (e: 'update:quotaTypeFilter', v: string): void
  (e: 'update:endpointTypeFilter', v: string): void
  (e: 'update:vendorFilter', v: string): void
  (e: 'update:groupFilter', v: string): void
  (e: 'update:tagFilter', v: string): void
  (e: 'clear-filters'): void
}>()

const { t } = useI18n()

// PD-03：商业版无倍率概念，隐藏分组过滤器
const groupHidden = isFeatureHidden('group-ratio')

// Q8c：端点类型选项（"全部"单独渲染，其余按芯片列出）
const endpointOptions = computed(() => [
  { value: ENDPOINT_TYPES.OPENAI, label: 'Chat' },
  { value: ENDPOINT_TYPES.OPENAI_RESPONSE, label: 'Response' },
  { value: ENDPOINT_TYPES.ANTHROPIC, label: 'Anthropic' },
  { value: ENDPOINT_TYPES.GEMINI, label: 'Gemini' },
  { value: ENDPOINT_TYPES.IMAGE_GENERATION, label: t('pricing.image') },
  { value: ENDPOINT_TYPES.EMBEDDINGS, label: t('pricing.embeddings') },
  { value: ENDPOINT_TYPES.JINA_RERANK, label: 'Rerank' },
  { value: ENDPOINT_TYPES.OPENAI_VIDEO, label: t('pricing.video') }
])

// count 统计：基于全量 models，口径与 lib/filters.ts 过滤逻辑一致
function countByEndpoint(type: string): number {
  return props.models.filter((m) => m.supportedEndpointTypes?.includes(type)).length
}
function countByVendor(name: string): number {
  return props.models.filter((m) => m.vendorName === name).length
}
function countByTag(tag: string): number {
  const lower = tag.toLowerCase()
  return props.models.filter((m) =>
    parseTags(m.tags).map((tg) => tg.toLowerCase()).includes(lower)
  ).length
}

// 芯片单选语义：点击已激活项回退到"全部"，否则选中
function pick(currentValue: string, optionValue: string, allValue: string, updater: (v: string) => void) {
  updater(currentValue === optionValue ? allValue : optionValue)
}
function onEndpointPick(value: string) {
  pick(props.endpointTypeFilter, value, ENDPOINT_TYPES.ALL, (v) => emit('update:endpointTypeFilter', v))
}
function onVendorPick(value: string) {
  pick(props.vendorFilter, value, FILTER_ALL, (v) => emit('update:vendorFilter', v))
}
function onTagPick(value: string) {
  pick(props.tagFilter, value, FILTER_ALL, (v) => emit('update:tagFilter', v))
}
</script>

<template>
  <div class="pricing-sidebar">
    <div
      v-if="hasActiveFilters"
      class="pricing-sidebar__clear"
    >
      <el-button
        text
        size="small"
        @click="emit('clear-filters')"
      >
        {{ t('pricing.clearFilters') }}
      </el-button>
    </div>

    <el-divider content-position="left">
      {{ t('pricing.pricingType') }}
    </el-divider>
    <el-radio-group
      :model-value="quotaTypeFilter"
      @update:model-value="(v: any) => emit('update:quotaTypeFilter', v as string)"
    >
      <el-radio :value="QUOTA_TYPES.ALL">
        {{ t('pricing.allModels') }}
      </el-radio>
      <el-radio :value="QUOTA_TYPES.TOKEN">
        {{ t('pricing.tokenBased') }}
      </el-radio>
      <el-radio :value="QUOTA_TYPES.REQUEST">
        {{ t('pricing.perRequest') }}
      </el-radio>
    </el-radio-group>

    <el-divider content-position="left">
      {{ t('pricing.vendor') }}
    </el-divider>
    <div class="pricing-sidebar__chips">
      <el-check-tag
        :checked="vendorFilter === FILTER_ALL"
        @change="emit('update:vendorFilter', FILTER_ALL)"
      >
        {{ t('pricing.allVendors') }}
        <span class="pricing-sidebar__count">{{ models.length }}</span>
      </el-check-tag>
      <el-check-tag
        v-for="v in vendors"
        :key="v.id"
        :checked="vendorFilter === v.name"
        @change="onVendorPick(v.name)"
      >
        <span class="pricing-sidebar__vendor-chip">
          <LobeIcon :vendor="v.name" :vendor-icon="v.icon" :size="14" />
          <span class="pricing-sidebar__vendor-name">{{ v.name }}</span>
        </span>
        <span class="pricing-sidebar__count">{{ countByVendor(v.name) }}</span>
      </el-check-tag>
    </div>

    <el-divider content-position="left">
      {{ t('pricing.endpointType') }}
    </el-divider>
    <div class="pricing-sidebar__chips">
      <el-check-tag
        :checked="endpointTypeFilter === ENDPOINT_TYPES.ALL"
        @change="emit('update:endpointTypeFilter', ENDPOINT_TYPES.ALL)"
      >
        {{ t('pricing.allTypes') }}
        <span class="pricing-sidebar__count">{{ models.length }}</span>
      </el-check-tag>
      <el-check-tag
        v-for="opt in endpointOptions"
        :key="opt.value"
        :checked="endpointTypeFilter === opt.value"
        @change="onEndpointPick(opt.value)"
      >
        {{ opt.label }}
        <span class="pricing-sidebar__count">{{ countByEndpoint(opt.value) }}</span>
      </el-check-tag>
    </div>

    <el-divider
      v-if="!groupHidden"
      content-position="left"
    >
      {{ t('pricing.group') }}
    </el-divider>
    <el-select
      v-if="!groupHidden"
      :model-value="groupFilter"
      size="default"
      style="width: 100%"
      @update:model-value="(v: string) => emit('update:groupFilter', v)"
    >
      <el-option
        :value="FILTER_ALL"
        :label="t('pricing.allGroups')"
      />
      <el-option
        v-for="g in groups"
        :key="g"
        :value="g"
        :label="`${g} (${groupRatios[g] ?? 1}x)`"
      />
    </el-select>

    <el-divider
      v-if="tags.length > 0"
      content-position="left"
    >
      {{ t('pricing.tag') }}
    </el-divider>
    <div
      v-if="tags.length > 0"
      class="pricing-sidebar__chips"
    >
      <el-check-tag
        :checked="tagFilter === FILTER_ALL"
        @change="emit('update:tagFilter', FILTER_ALL)"
      >
        {{ t('pricing.allTags') }}
        <span class="pricing-sidebar__count">{{ models.length }}</span>
      </el-check-tag>
      <el-check-tag
        v-for="tag in tags"
        :key="tag"
        :checked="tagFilter === tag"
        @change="onTagPick(tag)"
      >
        {{ tag }}
        <span class="pricing-sidebar__count">{{ countByTag(tag) }}</span>
      </el-check-tag>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pricing-sidebar {
  &__clear {
    text-align: right;
  }

  &__chips {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-2);
  }

  &__vendor-chip {
    display: inline-flex;
    gap: var(--ys-spacing-1);
    align-items: center;
  }

  &__vendor-name {
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    vertical-align: middle;
    white-space: nowrap;
  }

  &__count {
    padding: 0 6px;
    margin-left: 4px;
    font-size: 11px;
    font-weight: 500;
    font-variant-numeric: tabular-nums;
    color: var(--el-text-color-secondary);
    background: var(--el-fill-color);
    border-radius: 10px;
  }

  // el-check-tag 选中态下 count 徽标对比度调整
  :deep(.el-check-tag.is-checked) .pricing-sidebar__count {
    color: var(--el-color-white);
    background: rgb(255 255 255 / 25%);
  }

  :deep(.el-divider__text) {
    font-size: var(--ys-font-size-xs);
    font-weight: 600;
  }

  :deep(.el-radio-group) {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-1);
    align-items: flex-start;
  }

  :deep(.el-check-tag) {
    padding: var(--ys-spacing-1) 10px;
    font-size: var(--ys-font-size-xs);
  }
}
</style>
