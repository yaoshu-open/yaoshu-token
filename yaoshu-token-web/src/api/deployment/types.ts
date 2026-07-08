/**
 * 部署 API 与状态类型声明。
 * 后端契约：ai-docs/后端设计/API_Contract/契约_模型与部署.md §五。
 *
 * PriceEstimation / PriceBreakdown 双键名兼容：后端契约未明确，classic 源码用
 * snake_case + PascalCase 双探，类型声明同步双字段。
 */

/** 部署生命周期状态枚举（覆盖 classic + default 所有已知 status 字符串） */
export type DeploymentStatus =
  | 'running'
  | 'completed'
  | 'deployment_requested'
  | 'termination_requested'
  | 'destroyed'
  | 'failed'
  | 'pending'
  | 'deployed'
  | 'stopping'
  | 'terminated'

/** 日志流过滤 */
export type DeploymentStream = 'stdout' | 'stderr'

/** 容器事件 */
export interface ContainerEvent {
  time?: number | string
  message?: string
}

/** 容器实例 */
export interface DeploymentContainer {
  container_id: string
  device_id?: string
  status?: DeploymentStatus
  brand_name?: string
  hardware?: string
  public_url?: string
  gpus_per_container?: number
  created_at?: number
  events?: ContainerEvent[]
}

/** 容器列表响应 */
export interface ContainerListResponse {
  containers: DeploymentContainer[]
}

/** 位置信息 */
export interface DeploymentLocation {
  id: number | string
  name: string
  iso2: string
}

/** 容器配置 */
export interface ContainerConfig {
  image_url?: string
  traffic_port?: number
  entrypoint?: string[]
  env_variables?: Record<string, string>
}

/** 部署详情 */
export interface DeploymentDetails {
  id: string
  deployment_name?: string
  container_name?: string
  status: DeploymentStatus
  brand_name?: string
  hardware_name?: string
  hardware_id?: number
  total_gpus?: number
  gpus_per_container?: number
  total_containers?: number
  hardware_quantity?: number
  created_at?: number
  updated_at?: number
  started_at?: number
  finished_at?: number
  amount_paid?: number
  compute_minutes_served?: number
  compute_minutes_remaining?: number
  completed_percent?: number
  time_remaining?: string
  container_config?: ContainerConfig
  locations?: DeploymentLocation[]
}

/** 部署列表项（list view 简化版） */
export interface DeploymentListItem {
  id: string
  deployment_name?: string
  container_name?: string
  status: DeploymentStatus
  brand_name?: string
  hardware_name?: string
  total_gpus?: number
  created_at?: number
  time_remaining?: string
}

/** 部署列表响应（与 default 一致） */
export interface DeploymentListResponse {
  items: DeploymentListItem[]
  total: number
}

/** 价格估算入参 */
export interface PriceEstimationPayload {
  location_ids: number[]
  hardware_id: number
  gpus_per_container: number
  duration_hours: number
  replica_count: number
  currency?: 'usdc' | string
  duration_type?: 'hour' | string
  duration_qty?: number
  hardware_qty?: number
}

/** 价格 breakdown（双键名兼容） */
export interface PriceBreakdown {
  total_cost?: number
  TotalCost?: number
  hourly_rate?: number
  HourlyRate?: number
  compute_cost?: number
  ComputeCost?: number
}

/** 价格估算响应（双键名兼容） */
export interface PriceEstimation {
  estimated_cost?: number
  EstimatedCost?: number
  currency?: string
  Currency?: string
  price_breakdown?: PriceBreakdown
  PriceBreakdown?: PriceBreakdown
}

/** 延长入参 */
export interface ExtendPayload {
  duration_hours: number
}

/** 日志查询参数 */
export interface DeploymentLogsParams {
  container_id: string
  stream?: 'stdout' | 'stderr' | 'all'
  follow?: boolean
}

/** 部署列表查询参数 */
export interface DeploymentListParams {
  p: number
  page_size: number
  status?: string
  keyword?: string
}

// ===== MD-C4-create：io.net 部署创建 Wizard 扩展类型 =====

/** 硬件类型（io.net 透传，snake_case） */
export interface HardwareType {
  id: number
  name: string
  brand_name?: string
  gpu_count?: number
  display_name?: string
}

/** 可用副本项（io.net 透传，snake_case） */
export interface AvailableReplica {
  location_id?: number
  location?: { id: number; name: string }
  available?: number
  total_available?: number
}

/** 可用副本响应（io.net 透传，snake_case） */
export interface AvailableReplicasResponse {
  hardware_types?: Array<{ id: number; available: number }>
  total_available?: number
  replicas?: AvailableReplica[]
}

/** 名称查重响应 */
export interface CheckNameResponse {
  available: boolean
}

/** io.net 部署设置 */
export interface DeploymentSettings {
  api_key?: string
  is_configured?: boolean
}

/** 测试连接响应 */
export interface TestConnectionResponse {
  success: boolean
  message?: string
}

/** 创建部署请求体（io.net 透传，snake_case） */
export interface CreateDeploymentPayload {
  resource_private_name: string
  duration_hours: number
  gpus_per_container: number
  hardware_id: number
  location_ids: number[]
  container_config: {
    replica_count: number
    env_variables: Record<string, string>
    secret_env_variables: Record<string, string>
    entrypoint?: string[]
    args?: string[]
    traffic_port?: number
  }
  registry_config: {
    image_url: string
    registry_username?: string
    registry_secret?: string
  }
  currency?: string
}
