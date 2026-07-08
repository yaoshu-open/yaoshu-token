<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search, Refresh, ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import type { CommonLogsFilters } from '@/composables/usage-logs/useCommonLogsData'
import { isFeatureHidden } from '@/plugins/spi/registry'
import { useDashboardData } from '@/composables/dashboard/useDashboardData'
import { getTokens } from '@/api/token'

interface CommonLogsToolbarProps {
  filters: CommonLogsFilters
  loading?: boolean
  isAdmin?: boolean
}

const props = defineProps<CommonLogsToolbarProps>()
const emit = defineEmits<{
  (e: 'search'): void
  (e: 'reset'): void
  (e: 'update:filters', partial: Partial<CommonLogsFilters>): void
}>()
const { t } = useI18n()

// 高级筛选默认折叠（常用筛选默认展开）
const showAdvanced = ref(false)

// 商业版 PD-03 ：无分组概念，隐藏 group 筛选项
const groupHidden = isFeatureHidden('group-ratio')

// 模型/API 密钥下拉选项（PD-08-5：文本输入改下拉）
const { availableModels } = useDashboardData()
const tokenNameOptions = ref<string[]>([])
onMounted(async () => {
  try {
    const data = await getTokens({ pageNum: 1, pageSize: 200 })
    tokenNameOptions.value = (data.list ?? [])
      .map((tk) => tk.name)
      .filter((n): n is string => Boolean(n))
  } catch {
    // 拉取失败保持空数组，filterable 仍可输入
    tokenNameOptions.value = []
  }
})

function update<K extends keyof CommonLogsFilters>(key: K, value: CommonLogsFilters[K]) {
  emit('update:filters', { [key]: value } as Partial<CommonLogsFilters>)
}

const logTypeOptions = [
  { label: t('usageLogs.types.all'), value: 0 },
  { label: t('usageLogs.types.consume'), value: 2 },
  { label: t('usageLogs.types.error'), value: 5 },
  { label: t('usageLogs.types.refund'), value: 6 },
  { label: t('usageLogs.types.topup'), value: 1 },
  { label: t('usageLogs.types.manage'), value: 3 },
  { label: t('usageLogs.types.system'), value: 4 },
]
</script>

<template>
  <div class="common-logs-toolbar">
    <!-- 常用筛选（始终展开） -->
    <div class="common-logs-toolbar__row">
      <div class="common-logs-toolbar__date">
        <ElDatePicker
          :model-value="props.filters.dateRange"
          type="datetimerange"
          :start-placeholder="t('usageLogs.filter.startTime')"
          :end-placeholder="t('usageLogs.filter.endTime')"
          value-format="x"
          style="width: 100%"
          @update:model-value="(v: [Date, Date] | null) => update('dateRange', v)"
        />
      </div>
      <ElSelect
        :model-value="props.filters.modelName"
        :placeholder="t('usageLogs.filter.modelName')"
        clearable
        filterable
        class="common-logs-toolbar__field"
        @update:model-value="(v: string) => update('modelName', v ?? '')"
        @change="emit('search')"
      >
        <ElOption
          v-for="m in availableModels"
          :key="m"
          :label="m"
          :value="m"
        />
      </ElSelect>
      <ElSelect
        :model-value="props.filters.logType"
        :placeholder="t('usageLogs.filter.logType')"
        class="common-logs-toolbar__field"
        @update:model-value="(v: number) => update('logType', v)"
        @change="emit('search')"
      >
        <ElOption
          v-for="opt in logTypeOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </ElSelect>
      <ElSelect
        :model-value="props.filters.tokenName"
        :placeholder="t('usageLogs.filter.tokenName')"
        clearable
        filterable
        class="common-logs-toolbar__field"
        @update:model-value="(v: string) => update('tokenName', v ?? '')"
        @change="emit('search')"
      >
        <ElOption
          v-for="name in tokenNameOptions"
          :key="name"
          :label="name"
          :value="name"
        />
      </ElSelect>
      <div class="common-logs-toolbar__actions">
        <ElButton
          type="primary"
          :icon="Search"
          :loading="loading"
          @click="emit('search')"
        >
          {{ t('common.search') }}
        </ElButton>
        <ElButton
          :icon="Refresh"
          @click="emit('reset')"
        >
          {{ t('common.reset') }}
        </ElButton>
        <ElButton
          link
          type="primary"
          @click="showAdvanced = !showAdvanced"
        >
          {{ t('usageLogs.filter.advanced') }}
          <ElIcon class="el-icon--right">
            <component :is="showAdvanced ? ArrowUp : ArrowDown" />
          </ElIcon>
        </ElButton>
      </div>
    </div>

    <!-- 高级筛选（默认折叠） -->
    <div
      v-if="showAdvanced"
      class="common-logs-toolbar__row common-logs-toolbar__row--advanced"
    >
      <ElInput
        v-if="!groupHidden"
        :model-value="props.filters.group"
        :placeholder="t('usageLogs.filter.group')"
        clearable
        class="common-logs-toolbar__field"
        @update:model-value="(v: string) => update('group', v)"
        @keyup.enter="emit('search')"
      />
      <ElInput
        v-if="isAdmin"
        :model-value="props.filters.username"
        :placeholder="t('usageLogs.filter.username')"
        clearable
        class="common-logs-toolbar__field"
        @update:model-value="(v: string) => update('username', v)"
        @keyup.enter="emit('search')"
      />
      <ElInput
        v-if="isAdmin"
        :model-value="props.filters.channel"
        :placeholder="t('usageLogs.filter.channelId')"
        clearable
        class="common-logs-toolbar__field"
        @update:model-value="(v: string) => update('channel', v)"
        @keyup.enter="emit('search')"
      />
      <!-- PD-08：requestId/upstreamRequestId 是上游 API 调试字段，管理员专属 -->
      <ElInput
        v-if="isAdmin"
        :model-value="props.filters.requestId"
        :placeholder="t('usageLogs.filter.requestId')"
        clearable
        class="common-logs-toolbar__field"
        @update:model-value="(v: string) => update('requestId', v)"
        @keyup.enter="emit('search')"
      />
      <ElInput
        v-if="isAdmin"
        :model-value="props.filters.upstreamRequestId"
        :placeholder="t('usageLogs.filter.upstreamRequestId')"
        clearable
        class="common-logs-toolbar__field"
        @update:model-value="(v: string) => update('upstreamRequestId', v)"
        @keyup.enter="emit('search')"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens' as *;

.common-logs-toolbar {
  display: flex;
  flex-direction: column;
  gap: $spacing-3;
  padding: $spacing-4;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: $radius-md;

  &__row {
    display: flex;
    flex-wrap: wrap;
    gap: $spacing-2;
    align-items: center;

    &--advanced {
      padding-top: $spacing-3;
      border-top: 1px dashed var(--el-border-color-lighter);
    }
  }

  &__date {
    flex: 0 0 220px;
    width: 220px;

    @media (width <= 768px) {
      flex: 1 1 100%;
      width: 100%;
    }
  }

  &__field {
    width: 180px;

    @media (width <= 768px) {
      width: 100%;
    }
  }

  &__actions {
    display: flex;
    gap: $spacing-2;
    margin-left: auto;
  }
}
</style>
