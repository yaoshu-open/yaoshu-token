/**
 * 渠道表格列配置。
 *
 * 列定义与渲染逻辑分离：列元数据在此，渲染插槽在 ChannelsTable.vue。
 */

import type { ChannelSortBy } from '@/api/channel/types'

/** 列标识 */
export type ChannelColumnKey =
  | 'selection'
  | 'id'
  | 'name'
  | 'type'
  | 'status'
  | 'group'
  | 'tag'
  | 'models'
  | 'balance'
  | 'response_time'
  | 'priority'
  | 'weight'
  | 'used_quota'
  | 'test_time'
  | 'actions'

/** 列配置项 */
export interface ChannelColumnConfig {
  key: ChannelColumnKey
  /** i18n key 后缀，完整 key = channel.columns.{labelKey} */
  labelKey: string
  /** 列宽度（px），不设则自适应 */
  width?: number
  /** 最小宽度（px） */
  minWidth?: number
  /** 是否可排序 */
  sortable?: boolean
  /** 对应的排序字段（CHANNEL_SORT_FIELDS 的 key） */
  sortField?: ChannelSortBy
  /** 是否固定列 */
  fixed?: 'left' | 'right' | boolean
  /** 紧凑模式下是否隐藏 */
  hideInCompact?: boolean
  /** 是否默认显示 */
  defaultVisible?: boolean
}

/** 全量列配置（顺序即表格列顺序） */
export const CHANNEL_COLUMNS: ChannelColumnConfig[] = [
  {
    key: 'selection',
    labelKey: 'selection',
    width: 45,
    fixed: 'left',
    defaultVisible: true
  },
  {
    key: 'id',
    labelKey: 'id',
    width: 70,
    sortable: true,
    sortField: 'id',
    defaultVisible: true
  },
  {
    key: 'name',
    labelKey: 'name',
    minWidth: 160,
    sortable: true,
    sortField: 'name',
    fixed: 'left',
    defaultVisible: true
  },
  {
    key: 'type',
    labelKey: 'type',
    width: 120,
    defaultVisible: true
  },
  {
    key: 'status',
    labelKey: 'status',
    width: 100,
    sortable: true,
    sortField: 'status',
    defaultVisible: true
  },
  {
    key: 'group',
    labelKey: 'group',
    width: 100,
    defaultVisible: true
  },
  {
    key: 'tag',
    labelKey: 'tag',
    width: 100,
    defaultVisible: true
  },
  {
    key: 'models',
    labelKey: 'models',
    minWidth: 200,
    defaultVisible: true
  },
  {
    key: 'balance',
    labelKey: 'balance',
    width: 110,
    sortable: true,
    sortField: 'balance',
    defaultVisible: true
  },
  {
    key: 'response_time',
    labelKey: 'response_time',
    width: 110,
    sortable: true,
    sortField: 'response_time',
    defaultVisible: true
  },
  {
    key: 'priority',
    labelKey: 'priority',
    width: 80,
    sortable: true,
    sortField: 'priority',
    hideInCompact: true,
    defaultVisible: true
  },
  {
    key: 'weight',
    labelKey: 'weight',
    width: 80,
    sortable: true,
    sortField: 'weight',
    hideInCompact: true,
    defaultVisible: false
  },
  {
    key: 'used_quota',
    labelKey: 'used_quota',
    width: 110,
    sortable: true,
    sortField: 'used_quota',
    hideInCompact: true,
    defaultVisible: false
  },
  {
    key: 'test_time',
    labelKey: 'test_time',
    width: 160,
    sortable: true,
    sortField: 'test_time',
    hideInCompact: true,
    defaultVisible: false
  },
  {
    key: 'actions',
    labelKey: 'actions',
    width: 160,
    fixed: 'right',
    defaultVisible: true
  }
]

/** 获取默认可见列 */
export function getDefaultVisibleColumns(): ChannelColumnKey[] {
  return CHANNEL_COLUMNS.filter((c) => c.defaultVisible).map((c) => c.key)
}

/** 获取紧凑模式下可见列（过滤掉 hideInCompact） */
export function getCompactVisibleColumns(
  visibleColumns: ChannelColumnKey[]
): ChannelColumnKey[] {
  const hiddenKeys = new Set(
    CHANNEL_COLUMNS.filter((c) => c.hideInCompact).map((c) => c.key)
  )
  return visibleColumns.filter((key) => !hiddenKeys.has(key))
}

/** 根据 sortField 反查 prop（用于 ElTable 的 sort-change 事件） */
export function getPropBySortField(sortBy: ChannelSortBy): string | undefined {
  const col = CHANNEL_COLUMNS.find((c) => c.sortField === sortBy)
  return col?.key
}

/** 根据 prop 查询 sortField */
export function getSortFieldByProp(prop: string): ChannelSortBy | undefined {
  const col = CHANNEL_COLUMNS.find((c) => c.key === prop)
  return col?.sortField
}
