/**
 * 部署 API 端点常量 + 状态映射。
 *
 * 后端契约：ai-docs/后端设计/API_Contract/契约_模型与部署.md §五。
 */

export const DEPLOYMENT_ENDPOINTS = {
  LIST: '/api/deployments/',
  SEARCH: '/api/deployments/search',
  DETAIL: (id: string | number) => `/api/deployments/${id}`,
  LOGS: (id: string | number) => `/api/deployments/${id}/logs`,
  CONTAINERS: (id: string | number) => `/api/deployments/${id}/containers`,
  CONTAINER: (id: string | number, containerId: string) =>
    `/api/deployments/${id}/containers/${containerId}`,
  ESTIMATE_PRICE: '/api/deployments/price-estimation',
  EXTEND: (id: string | number) => `/api/deployments/${id}/extend`,
  DELETE: (id: string | number) => `/api/deployments/${id}`,
  RENAME: (id: string | number) => `/api/deployments/${id}/name`,
  // MD-C4-create：io.net 部署创建 Wizard 端点
  HARDWARE_TYPES: '/api/deployments/hardware-types',
  LOCATIONS: '/api/deployments/locations',
  AVAILABLE_REPLICAS: '/api/deployments/available-replicas',
  CHECK_NAME: '/api/deployments/check-name',
  CREATE: '/api/deployments/',
  SETTINGS: '/api/deployments/settings',
  TEST_CONNECTION: '/api/deployments/settings/test-connection'
} as const

import type { DeploymentStatus, DeploymentStream } from './types'

/** 部署状态字符串 → i18n key + 颜色变体（消费 M1-C StatusBadge） */
export const DEPLOYMENT_STATUS_CONFIG: Record<
  string,
  { i18nKey: string; variant: 'success' | 'warning' | 'danger' | 'info' | 'neutral' | 'primary' }
> = {
  running: { i18nKey: 'deployment.status.running', variant: 'success' },
  completed: { i18nKey: 'deployment.status.completed', variant: 'success' },
  deployment_requested: { i18nKey: 'deployment.status.deploymentRequested', variant: 'info' },
  termination_requested: { i18nKey: 'deployment.status.terminationRequested', variant: 'warning' },
  destroyed: { i18nKey: 'deployment.status.destroyed', variant: 'danger' },
  failed: { i18nKey: 'deployment.status.failed', variant: 'danger' },
  pending: { i18nKey: 'deployment.status.pending', variant: 'warning' },
  deployed: { i18nKey: 'deployment.status.deployed', variant: 'primary' },
  stopping: { i18nKey: 'deployment.status.stopping', variant: 'warning' },
  terminated: { i18nKey: 'deployment.status.terminated', variant: 'neutral' }
}

/** 默认 stream 过滤 */
export const DEFAULT_STREAM: DeploymentStream = 'stdout'

/** 默认自动刷新间隔（ms） */
export const LOG_AUTO_REFRESH_INTERVAL = 5000

/** 价格估算防抖（ms） */
export const PRICE_ESTIMATE_DEBOUNCE = 400

/** 容器选项：全部容器（classic 沿用 __all__） */
export const ALL_CONTAINERS = '__all__'

// 防止 DeploymentStatus 类型在 import 后被孤立
export type { DeploymentStatus, DeploymentStream }
