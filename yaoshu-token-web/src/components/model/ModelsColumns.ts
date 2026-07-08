/**
 * 模型表格列配置。
 *
 * 列定义与渲染逻辑分离：列元数据在此，渲染插槽在 ModelsTable.vue。
 */

/** 列标识 */
export type ModelColumnKey =
  | 'selection'
  | 'id'
  | 'icon'
  | 'model_name'
  | 'name_rule'
  | 'status'
  | 'vendor'
  | 'description'
  | 'tags'
  | 'endpoints'
  | 'bound_channels'
  | 'enable_groups'
  | 'quota_types'
  | 'sync_official'
  | 'created_time'
  | 'updated_time'
  | 'actions'

/** 列配置项 */
export interface ModelColumnConfig {
  key: ModelColumnKey
  /** 列标签 */
  label: string
  /** 列宽度（px） */
  width?: number
  /** 最小宽度（px） */
  minWidth?: number
  /** 是否默认显示 */
  defaultVisible?: boolean
  /** 紧凑模式下是否隐藏 */
  hideInCompact?: boolean
  /** 是否固定列 */
  fixed?: 'left' | 'right'
}

/** 全量列配置（顺序即表格列顺序） */
export const MODEL_COLUMNS: ModelColumnConfig[] = [
  {
    key: 'selection',
    label: '',
    width: 45,
    fixed: 'left',
    defaultVisible: true,
  },
  {
    key: 'id',
    label: 'model.columns.id',
    width: 70,
    defaultVisible: true,
  },
  {
    key: 'icon',
    label: 'model.columns.icon',
    width: 60,
    defaultVisible: true,
  },
  {
    key: 'model_name',
    label: 'model.columns.modelName',
    minWidth: 200,
    fixed: 'left',
    defaultVisible: true,
  },
  {
    key: 'name_rule',
    label: 'model.columns.matchType',
    width: 130,
    defaultVisible: true,
  },
  {
    key: 'status',
    label: 'model.columns.status',
    width: 100,
    defaultVisible: true,
  },
  {
    key: 'vendor',
    label: 'model.columns.vendor',
    width: 140,
    defaultVisible: true,
  },
  {
    key: 'description',
    label: 'model.columns.description',
    width: 150,
    defaultVisible: false,
    hideInCompact: true,
  },
  {
    key: 'tags',
    label: 'model.columns.tags',
    width: 150,
    defaultVisible: true,
    hideInCompact: true,
  },
  {
    key: 'endpoints',
    label: 'model.columns.endpoints',
    width: 150,
    defaultVisible: false,
    hideInCompact: true,
  },
  {
    key: 'bound_channels',
    label: 'model.columns.boundChannels',
    width: 150,
    defaultVisible: false,
    hideInCompact: true,
  },
  {
    key: 'enable_groups',
    label: 'model.columns.enableGroups',
    width: 150,
    defaultVisible: false,
    hideInCompact: true,
  },
  {
    key: 'quota_types',
    label: 'model.columns.quotaTypes',
    width: 130,
    defaultVisible: false,
    hideInCompact: true,
  },
  {
    key: 'sync_official',
    label: 'model.columns.officialSync',
    width: 120,
    defaultVisible: true,
    hideInCompact: true,
  },
  {
    key: 'created_time',
    label: 'model.columns.created',
    width: 160,
    defaultVisible: false,
    hideInCompact: true,
  },
  {
    key: 'updated_time',
    label: 'model.columns.updated',
    width: 160,
    defaultVisible: false,
    hideInCompact: true,
  },
  {
    key: 'actions',
    label: 'model.columns.actions',
    width: 80,
    fixed: 'right',
    defaultVisible: true,
  },
]
