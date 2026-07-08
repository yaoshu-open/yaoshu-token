<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search } from '@element-plus/icons-vue'
import CompactModeToggle from '@/components/CompactModeToggle.vue'
import { REDEMPTION_STATUS_OPTIONS } from '@/api/redemption/constants'
import type { RedemptionFilters } from '@/composables/redemption/useRedemptionsData'

const { t } = useI18n()

const props = defineProps<{
  filters: RedemptionFilters
  isCompact: boolean
}>()

const emit = defineEmits<{
  (e: 'update:filters', val: Partial<RedemptionFilters>): void
  (e: 'search'): void
  (e: 'reset-filters'): void
}>()

const keyword = computed({
  get: () => props.filters.keyword,
  set: (val: string) => emit('update:filters', { keyword: val }),
})
const status = computed({
  get: () => props.filters.status,
  set: (val: string) => emit('update:filters', { status: val }),
})
</script>

<template>
  <div class="redemption-toolbar">
    <div class="redemption-toolbar__left">
      <el-input
        v-model="keyword"
        :placeholder="t('redemption.list.searchPlaceholder')"
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
        v-model="status"
        size="small"
        style="width: 140px"
      >
        <el-option
          v-for="opt in REDEMPTION_STATUS_OPTIONS"
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
    <div class="redemption-toolbar__right">
      <CompactModeToggle
        table-key="redemptions"
        variant="switch"
      />
    </div>
  </div>
</template>

<style scoped>
.redemption-toolbar {
  display: flex;
  gap: var(--ys-spacing-3);
  align-items: center;
  justify-content: space-between;
  padding: var(--ys-spacing-2) 0;
}

.redemption-toolbar__left {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ys-spacing-2);
  align-items: center;
}

.redemption-toolbar__right {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
}
</style>
