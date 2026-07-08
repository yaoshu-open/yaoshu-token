<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search } from '@element-plus/icons-vue'
import CompactModeToggle from '@/components/CompactModeToggle.vue'
import { TOKEN_STATUS_OPTIONS } from '@/api/token/constants'
import type { TokenFilters } from '@/composables/token/useTokensData'

const { t } = useI18n()

const props = defineProps<{
  filters: TokenFilters
  isCompact: boolean
}>()

const emit = defineEmits<{
  (e: 'update:filters', val: Partial<TokenFilters>): void
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
  <div class="tokens-toolbar">
    <div class="tokens-toolbar__left">
      <el-input
        v-model="keyword"
        :placeholder="t('token.list.searchPlaceholder')"
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
        style="width: 120px"
      >
        <el-option
          v-for="opt in TOKEN_STATUS_OPTIONS"
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
    <div class="tokens-toolbar__right">
      <CompactModeToggle
        table-key="tokens"
        variant="switch"
      />
    </div>
  </div>
</template>

<style scoped>
.tokens-toolbar { display: flex; gap: var(--ys-spacing-3); align-items: center; justify-content: space-between; padding: var(--ys-spacing-2) 0; }
.tokens-toolbar__left { display: flex; flex-wrap: wrap; gap: var(--ys-spacing-2); align-items: center; }
.tokens-toolbar__right { display: flex; gap: var(--ys-spacing-2); align-items: center; }
</style>
