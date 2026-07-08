<script setup lang="ts">
/**
 * 模型表格主体组件。
 *
 * 职责：渲染模型列表表格，支持选择/紧凑模式。
 * 不负责：数据加载（useModelsData）/ 工具栏（ModelsToolbar）/ 操作确认。
 *
 * 紧凑模式（T-MO-01）：通过 CSS 类切换行高与 padding。
 */
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElTable, ElTableColumn } from 'element-plus'
import type { TableInstance } from 'element-plus'
import {
  MODEL_COLUMNS,
  type ModelColumnKey,
} from './ModelsColumns'
import {
  MODEL_STATUS_CONFIG,
  QUOTA_TYPE_CONFIG,
} from '@/api/model/constants'
import {
  formatTimestamp,
  formatEndpointsDisplay,
  getNameRuleConfigByRule,
  parseModelTags,
} from '@/lib/model/model-utils'
import type { Model, Vendor } from '@/api/model/types'
import { MoreFilled } from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    models: Model[]
    loading?: boolean
    isCompact?: boolean
    selectedIds?: number[]
    vendors?: Vendor[]
    visibleColumns?: ModelColumnKey[]
  }>(),
  {
    loading: false,
    isCompact: false,
    selectedIds: () => [],
    vendors: () => [],
    visibleColumns: () =>
      MODEL_COLUMNS.filter((c) => c.defaultVisible).map((c) => c.key),
  }
)

const emit = defineEmits<{
  (e: 'selection-change', ids: number[]): void
  (e: 'row-action', action: string, model: Model): void
}>()

const { t } = useI18n()

const tableRef = ref<TableInstance>()

const vendorMap = computed(() => {
  const map: Record<number, Vendor> = {}
  for (const v of props.vendors) {
    map[v.id] = v
  }
  return map
})

const visibleColumnSet = computed(() => new Set(props.visibleColumns))

const visibleColumns = computed(() =>
  MODEL_COLUMNS.filter(
    (c) => visibleColumnSet.value.has(c.key) && (!props.isCompact || !c.hideInCompact)
  )
)

function handleSelectionChange(selection: Model[]): void {
  emit(
    'selection-change',
    selection.map((m) => m.id)
  )
}

function getModelIcon(model: Record<string, unknown>): string {
  const icon = (model.icon as string) || (vendorMap.value[(model.vendorId as number) ?? 0]?.icon) || (model.modelName as string)?.[0] || 'N'
  return icon
}

function getTagType(color: string): 'success' | 'primary' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'success' | 'primary' | 'warning' | 'info' | 'danger'> = {
    success: 'success',
    primary: 'primary',
    warning: 'warning',
    info: 'info',
    danger: 'danger',
  }
  return map[color] ?? 'info'
}

defineExpose({
  tableRef,
})
</script>

<template>
  <div
    class="models-table"
    :class="{ 'models-table--compact': isCompact }"
  >
    <el-table
      ref="tableRef"
      :data="models"
      :loading="loading"
      row-key="id"
      stripe
      :border="false"
      @selection-change="handleSelectionChange"
    >
      <template
        v-for="col in visibleColumns"
        :key="col.key"
      >
        <!-- 选择列 -->
        <el-table-column
          v-if="col.key === 'selection'"
          type="selection"
          :width="col.width"
          :fixed="col.fixed"
        />

        <!-- ID 列 -->
        <el-table-column
          v-else-if="col.key === 'id'"
          :prop="col.key"
          :label="t(col.label)"
          :width="col.width"
        >
          <template #default="{ row }">
            <span class="models-table__id">{{ row.id }}</span>
          </template>
        </el-table-column>

        <!-- 图标列 -->
        <el-table-column
          v-else-if="col.key === 'icon'"
          :label="t(col.label)"
          :width="col.width"
        >
          <template #default="{ row }">
            <div class="models-table__icon">
              {{ getModelIcon(row)?.charAt(0)?.toUpperCase() || 'N' }}
            </div>
          </template>
        </el-table-column>

        <!-- 模型名称列 -->
        <el-table-column
          v-else-if="col.key === 'model_name'"
          :prop="col.key"
          :label="t(col.label)"
          :min-width="col.minWidth"
          :fixed="col.fixed"
        >
          <template #default="{ row }">
            <span class="models-table__model-name">{{ row.modelName }}</span>
          </template>
        </el-table-column>

        <!-- 匹配类型列 -->
        <el-table-column
          v-else-if="col.key === 'name_rule'"
          :label="t(col.label)"
          :width="col.width"
        >
          <template #default="{ row }">
            <el-tag
              :type="getTagType(getNameRuleConfigByRule(row.nameRule).color)"
              size="small"
            >
              {{ getNameRuleConfigByRule(row.nameRule).label }}
              <template v-if="row.nameRule !== 0 && row.matchedCount">
                ({{ row.matchedCount }})
              </template>
            </el-tag>
          </template>
        </el-table-column>

        <!-- 状态列 -->
        <el-table-column
          v-else-if="col.key === 'status'"
          :label="t(col.label)"
          :width="col.width"
        >
          <template #default="{ row }">
            <el-tag
              :type="MODEL_STATUS_CONFIG[row.status as 0 | 1]?.type || 'info'"
              size="small"
            >
              {{ MODEL_STATUS_CONFIG[row.status as 0 | 1]?.label ? t(MODEL_STATUS_CONFIG[row.status as 0 | 1]!.label) : t('model.status.unknown') }}
            </el-tag>
          </template>
        </el-table-column>

        <!-- 供应商列 -->
        <el-table-column
          v-else-if="col.key === 'vendor'"
          :label="t(col.label)"
          :width="col.width"
        >
          <template #default="{ row }">
            <span v-if="vendorMap[row.vendorId]">
              {{ vendorMap[row.vendorId].name }}
            </span>
            <span
              v-else
              class="models-table__placeholder"
            >-</span>
          </template>
        </el-table-column>

        <!-- 描述列 -->
        <el-table-column
          v-else-if="col.key === 'description'"
          :label="t(col.label)"
          :width="col.width"
        >
          <template #default="{ row }">
            <el-tooltip
              v-if="row.description"
              :content="row.description"
              placement="top"
              :show-after="500"
            >
              <span class="models-table__ellipsis">{{ row.description }}</span>
            </el-tooltip>
            <span
              v-else
              class="models-table__placeholder"
            >-</span>
          </template>
        </el-table-column>

        <!-- 标签列 -->
        <el-table-column
          v-else-if="col.key === 'tags'"
          :label="t(col.label)"
          :width="col.width"
        >
          <template #default="{ row }">
            <template v-if="parseModelTags(row.tags).length">
              <el-tag
                v-for="tag in parseModelTags(row.tags).slice(0, 2)"
                :key="tag"
                size="small"
                style="margin-right: 4px"
              >
                {{ tag }}
              </el-tag>
              <el-tooltip
                v-if="parseModelTags(row.tags).length > 2"
                placement="top"
              >
                <template #content>
                  <div>
                    <span
                      v-for="tag in parseModelTags(row.tags)"
                      :key="tag"
                      style="margin-right: 4px"
                    >
                      {{ tag }}
                    </span>
                  </div>
                </template>
                <el-tag size="small">
                  +{{ parseModelTags(row.tags).length - 2 }}
                </el-tag>
              </el-tooltip>
            </template>
            <span
              v-else
              class="models-table__placeholder"
            >-</span>
          </template>
        </el-table-column>

        <!-- 端点列 -->
        <el-table-column
          v-else-if="col.key === 'endpoints'"
          :label="t(col.label)"
          :width="col.width"
        >
          <template #default="{ row }">
            <template v-if="formatEndpointsDisplay(row.endpoints).length">
              <el-tag
                v-for="ep in formatEndpointsDisplay(row.endpoints).slice(0, 2)"
                :key="ep"
                size="small"
                style="margin-right: 4px"
              >
                {{ ep }}
              </el-tag>
            </template>
            <span
              v-else
              class="models-table__placeholder"
            >-</span>
          </template>
        </el-table-column>

        <!-- 绑定渠道列 -->
        <el-table-column
          v-else-if="col.key === 'bound_channels'"
          :label="t(col.label)"
          :width="col.width"
        >
          <template #default="{ row }">
            <template v-if="row.boundChannels && row.boundChannels.length">
              <el-tag
                v-for="ch in row.boundChannels.slice(0, 2)"
                :key="ch.name"
                size="small"
                style="margin-right: 4px"
              >
                {{ ch.name }} ({{ ch.type }})
              </el-tag>
            </template>
            <span
              v-else
              class="models-table__placeholder"
            >-</span>
          </template>
        </el-table-column>

        <!-- 启用分组列 -->
        <el-table-column
          v-else-if="col.key === 'enable_groups'"
          :label="t(col.label)"
          :width="col.width"
        >
          <template #default="{ row }">
            <template v-if="row.enableGroups && row.enableGroups.length">
              <el-tag
                v-for="g in row.enableGroups.slice(0, 2)"
                :key="g"
                size="small"
                style="margin-right: 4px"
              >
                {{ g }}
              </el-tag>
            </template>
            <span
              v-else
              class="models-table__placeholder"
            >-</span>
          </template>
        </el-table-column>

        <!-- 配额类型列 -->
        <el-table-column
          v-else-if="col.key === 'quota_types'"
          :label="t(col.label)"
          :width="col.width"
        >
          <template #default="{ row }">
            <template v-if="row.quotaTypes && row.quotaTypes.length">
              <el-tag
                v-for="qt in row.quotaTypes"
                :key="qt"
                :type="getTagType(QUOTA_TYPE_CONFIG[qt]?.type || 'info')"
                size="small"
                style="margin-right: 4px"
              >
                {{ QUOTA_TYPE_CONFIG[qt]?.label || String(qt) }}
              </el-tag>
            </template>
            <span
              v-else
              class="models-table__placeholder"
            >-</span>
          </template>
        </el-table-column>

        <!-- 同步状态列 -->
        <el-table-column
          v-else-if="col.key === 'sync_official'"
          :label="t(col.label)"
          :width="col.width"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.syncOfficial === 1 ? 'success' : 'warning'"
              size="small"
            >
              {{ row.syncOfficial === 1 ? 'Official Sync' : 'No Sync' }}
            </el-tag>
          </template>
        </el-table-column>

        <!-- 创建时间列 -->
        <el-table-column
          v-else-if="col.key === 'created_time'"
          :label="t(col.label)"
          :width="col.width"
        >
          <template #default="{ row }">
            <span class="models-table__timestamp">{{ formatTimestamp(row.createdTime) }}</span>
          </template>
        </el-table-column>

        <!-- 更新时间列 -->
        <el-table-column
          v-else-if="col.key === 'updated_time'"
          :label="t(col.label)"
          :width="col.width"
        >
          <template #default="{ row }">
            <span class="models-table__timestamp">{{ formatTimestamp(row.updatedTime) }}</span>
          </template>
        </el-table-column>

        <!-- 操作列 -->
        <el-table-column
          v-else-if="col.key === 'actions'"
          :width="col.width"
          :fixed="col.fixed"
        >
          <template #default="{ row }">
            <el-dropdown
              trigger="click"
              @command="(cmd: string) => emit('row-action', cmd, row as Model)"
            >
              <el-button
                text
                size="small"
                circle
              >
                <el-icon><MoreFilled /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="edit">
                    Edit
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="row.status !== 1"
                    command="enable"
                  >
                    Enable
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="row.status === 1"
                    command="disable"
                  >
                    Disable
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="row.description"
                    command="description"
                  >
                    Description
                  </el-dropdown-item>
                  <el-dropdown-item
                    command="delete"
                    divided
                  >
                    Delete
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </template>
    </el-table>
  </div>
</template>

<style scoped>
.models-table {
  height: 100%;
}

.models-table--compact :deep(.el-table__row) {
  height: 36px;
}

.models-table--compact :deep(.el-table__cell) {
  padding: var(--ys-spacing-1) var(--ys-spacing-2);
}

.models-table__id {
  font-family: monospace;
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-secondary);
}

.models-table__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  font-size: var(--ys-font-size-base);
  font-weight: 600;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-light);
  border-radius: var(--ys-radius-sm);
}

.models-table__model-name {
  font-family: monospace;
  font-size: var(--ys-font-size-sm);
  font-weight: 500;
}

.models-table__placeholder {
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-placeholder);
}

.models-table__ellipsis {
  display: inline-block;
  max-width: 130px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.models-table__timestamp {
  font-family: monospace;
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}
</style>
