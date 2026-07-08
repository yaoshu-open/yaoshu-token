<script setup lang="ts">
/**
 * 模型工具栏组件。
 *
 * 职责：搜索/筛选/紧凑模式切换。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import CompactModeToggle from '@/components/CompactModeToggle.vue'
import {
  MODEL_STATUS_OPTIONS,
  SYNC_STATUS_OPTIONS,
} from '@/api/model/constants'
import type { ModelFilters } from '@/composables/model/useModelsData'
import { Search } from '@element-plus/icons-vue'

const props = defineProps<{
  filters: ModelFilters
  isCompact: boolean
  vendorOptions: { label: string; value: string }[]
}>()

const emit = defineEmits<{
  (e: 'update:filters', val: Partial<ModelFilters>): void
  (e: 'search'): void
  (e: 'reset-filters'): void
  (e: 'toggle-compact'): void
}>()

const { t } = useI18n()

const keyword = computed({
  get: () => props.filters.keyword,
  set: (val: string) => emit('update:filters', { keyword: val }),
})

const vendor = computed({
  get: () => props.filters.vendor,
  set: (val: string) => emit('update:filters', { vendor: val }),
})

const status = computed({
  get: () => props.filters.status,
  set: (val: string) => emit('update:filters', { status: val }),
})

const syncOfficial = computed({
  get: () => props.filters.syncOfficial,
  set: (val: string) => emit('update:filters', { syncOfficial: val }),
})
</script>

<template>
  <div class="models-toolbar">
    <div class="models-toolbar__left">
      <el-input
        v-model="keyword"
        :placeholder="t('model.list.searchPlaceholder')"
        clearable
        size="small"
        style="width: 240px"
        @keyup.enter="emit('search')"
        @clear="emit('search')"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <el-select
        v-model="vendor"
        size="small"
        style="width: 160px"
      >
        <el-option
          v-for="opt in vendorOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>

      <el-select
        v-model="status"
        size="small"
        style="width: 120px"
      >
        <el-option
          v-for="opt in MODEL_STATUS_OPTIONS"
          :key="opt.value"
          :label="t(opt.label)"
          :value="opt.value"
        />
      </el-select>

      <el-select
        v-model="syncOfficial"
        size="small"
        style="width: 140px"
      >
        <el-option
          v-for="opt in SYNC_STATUS_OPTIONS"
          :key="opt.value"
          :label="t(opt.label)"
          :value="opt.value"
        />
      </el-select>

      <el-button
        size="small"
        @click="emit('reset-filters')"
      >
        {{ t('common.reset') }}
      </el-button>
    </div>

    <div class="models-toolbar__right">
      <CompactModeToggle
        table-key="models"
        variant="switch"
      />
    </div>
  </div>
</template>

<style scoped>
.models-toolbar {
  display: flex;
  gap: var(--ys-spacing-3);
  align-items: center;
  justify-content: space-between;
  padding: var(--ys-spacing-2) 0;
}

.models-toolbar__left {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ys-spacing-2);
  align-items: center;
}

.models-toolbar__right {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
}
</style>
