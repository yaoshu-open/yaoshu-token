<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search } from '@element-plus/icons-vue'
import CompactModeToggle from '@/components/CompactModeToggle.vue'
import { USER_STATUS_OPTIONS, USER_ROLE_OPTIONS } from '@/api/user/constants'
import type { UserFilters } from '@/composables/user/useUsersData'

const { t } = useI18n()

const props = defineProps<{
  filters: UserFilters
  isCompact: boolean
}>()

const emit = defineEmits<{
  (e: 'update:filters', val: Partial<UserFilters>): void
  (e: 'search'): void
  (e: 'reset-filters'): void
}>()

const keyword = computed({
  get: () => props.filters.keyword,
  set: (val: string) => emit('update:filters', { keyword: val }),
})
const group = computed({
  get: () => props.filters.group,
  set: (val: string) => emit('update:filters', { group: val }),
})
const role = computed({
  get: () => props.filters.role,
  set: (val: string) => emit('update:filters', { role: val }),
})
const status = computed({
  get: () => props.filters.status,
  set: (val: string) => emit('update:filters', { status: val }),
})
</script>

<template>
  <div class="users-toolbar">
    <div class="users-toolbar__left">
      <el-input
        v-model="keyword"
        :placeholder="t('user.list.searchPlaceholder')"
        clearable
        size="small"
        style="width: 220px"
        @keyup.enter="emit('search')"
        @clear="emit('search')"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-input
        v-model="group"
        :placeholder="t('user.form.groupPlaceholder')"
        size="small"
        style="width: 100px"
        @keyup.enter="emit('search')"
      />
      <el-select
        v-model="role"
        size="small"
        style="width: 120px"
      >
        <el-option
          v-for="opt in USER_ROLE_OPTIONS"
          :key="opt.value"
          :label="t(opt.label)"
          :value="opt.value"
        />
      </el-select>
      <el-select
        v-model="status"
        size="small"
        style="width: 120px"
      >
        <el-option
          v-for="opt in USER_STATUS_OPTIONS"
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
    <div class="users-toolbar__right">
      <CompactModeToggle
        table-key="users"
        variant="switch"
      />
    </div>
  </div>
</template>

<style scoped>
.users-toolbar { display: flex; gap: var(--ys-spacing-3); align-items: center; justify-content: space-between; padding: var(--ys-spacing-2) 0; }
.users-toolbar__left { display: flex; flex-wrap: wrap; gap: var(--ys-spacing-2); align-items: center; }
.users-toolbar__right { display: flex; gap: var(--ys-spacing-2); align-items: center; }
</style>
