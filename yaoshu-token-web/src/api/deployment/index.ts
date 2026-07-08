/**
 * 部署 API Service。
 * 后端契约：ai-docs/后端设计/API_Contract/契约_模型与部署.md §五。
 */
import { request } from '@/utils/request'
import { createChannel } from '@/api/channel'
import type { AddChannelRequest } from '@/api/channel/types'
import { DEPLOYMENT_ENDPOINTS } from './constants'
import type {
  AvailableReplicasResponse,
  CheckNameResponse,
  ContainerListResponse,
  CreateDeploymentPayload,
  DeploymentContainer,
  DeploymentDetails,
  DeploymentListParams,
  DeploymentListResponse,
  DeploymentLocation,
  DeploymentLogsParams,
  DeploymentSettings,
  ExtendPayload,
  HardwareType,
  PriceEstimation,
  PriceEstimationPayload,
  TestConnectionResponse
} from './types'

/** 部署列表 */
export function listDeployments(params: DeploymentListParams): Promise<DeploymentListResponse> {
  return request.get<DeploymentListResponse>(DEPLOYMENT_ENDPOINTS.LIST, { params })
}

/** 搜索部署 */
export function searchDeployments(params: DeploymentListParams): Promise<DeploymentListResponse> {
  return request.get<DeploymentListResponse>(DEPLOYMENT_ENDPOINTS.SEARCH, { params })
}

/** 部署详情 */
export function getDeployment(id: string | number): Promise<DeploymentDetails> {
  return request.get<DeploymentDetails>(DEPLOYMENT_ENDPOINTS.DETAIL(id))
}

/** 容器列表 */
export function getDeploymentContainers(id: string | number): Promise<ContainerListResponse> {
  return request.get<ContainerListResponse>(DEPLOYMENT_ENDPOINTS.CONTAINERS(id))
}

/** 容器详情 */
export function getDeploymentContainer(
  id: string | number,
  containerId: string
): Promise<DeploymentContainer> {
  return request.get<DeploymentContainer>(DEPLOYMENT_ENDPOINTS.CONTAINER(id, containerId))
}

/** 部署日志（返回原始字符串，由 composable 解析为行） */
export function getDeploymentLogs(
  id: string | number,
  params: DeploymentLogsParams
): Promise<string> {
  return request.get<string>(DEPLOYMENT_ENDPOINTS.LOGS(id), { params })
}

/** 价格估算 */
export function estimatePrice(payload: PriceEstimationPayload): Promise<PriceEstimation> {
  return request.post<PriceEstimation>(DEPLOYMENT_ENDPOINTS.ESTIMATE_PRICE, payload)
}

/** 延长部署 */
export function extendDeployment(
  id: string | number,
  payload: ExtendPayload
): Promise<DeploymentDetails> {
  return request.post<DeploymentDetails>(DEPLOYMENT_ENDPOINTS.EXTEND(id), payload)
}

/** 删除部署 */
export function deleteDeployment(id: string | number): Promise<void> {
  return request.delete<void>(DEPLOYMENT_ENDPOINTS.DELETE(id))
}

/** 重命名部署 */
export function renameDeployment(
  id: string | number,
  name: string
): Promise<DeploymentDetails> {
  return request.put<DeploymentDetails>(DEPLOYMENT_ENDPOINTS.RENAME(id), { name })
}

/**
 * 同步部署到渠道（T-MD-04）。
 * 流程：获取容器列表 → 找到 public_url → 创建渠道（type=4, base_url=容器URL）
 */
export async function syncDeploymentToChannel(
  deploymentId: string | number,
  deploymentName: string
): Promise<void> {
  const containerResp = await getDeploymentContainers(deploymentId)
  const containers = containerResp?.containers || []
  const activeContainer = containers.find((c) => c?.public_url)
  if (!activeContainer?.public_url) {
    throw new Error('NO_CONTAINER_URL')
  }

  const baseUrl = String(activeContainer.public_url).trim().replace(/\/+$/, '')
  if (!baseUrl) {
    throw new Error('NO_CONTAINER_URL')
  }

  const safeName = String(deploymentName || 'ionet').slice(0, 60)
  const channelName = `[IO.NET] ${safeName}`
  const randomKey = `ionet-${crypto.randomUUID?.()?.replace(/-/g, '') || `${Math.random().toString(36).slice(2)}${Math.random().toString(36).slice(2)}`}`

  const otherInfo = {
    source: 'ionet',
    deployment_id: String(deploymentId),
    deployment_name: safeName,
    container_id: activeContainer.container_id || null,
    public_url: baseUrl
  }

  const payload: AddChannelRequest = {
    mode: 'single',
    channel: {
      name: channelName,
      type: 4,
      key: randomKey,
      baseUrl: baseUrl,
      group: 'default',
      tag: 'ionet',
      remark: `[IO.NET] Auto-synced from deployment ${deploymentId}`,
      otherInfo: JSON.stringify(otherInfo)
    }
  }

  await createChannel(payload)
}

// ===== MD-C4-create：io.net 部署创建 Wizard Service =====

/** 硬件类型列表 */
export function getHardwareTypes(): Promise<HardwareType[]> {
  return request.get<HardwareType[]>(DEPLOYMENT_ENDPOINTS.HARDWARE_TYPES)
}

/** 地区列表 */
export function getLocations(): Promise<DeploymentLocation[]> {
  return request.get<DeploymentLocation[]>(DEPLOYMENT_ENDPOINTS.LOCATIONS)
}

/** 可用副本查询（依赖 hardware_id + gpu_count） */
export function getAvailableReplicas(
  hardwareId: number,
  gpuCount: number
): Promise<AvailableReplicasResponse> {
  return request.get<AvailableReplicasResponse>(DEPLOYMENT_ENDPOINTS.AVAILABLE_REPLICAS, {
    params: { hardware_id: hardwareId, gpu_count: gpuCount }
  })
}

/** 名称查重 */
export function checkDeploymentName(name: string): Promise<CheckNameResponse> {
  return request.get<CheckNameResponse>(DEPLOYMENT_ENDPOINTS.CHECK_NAME, { params: { name } })
}

/** 创建部署 */
export function createDeployment(payload: CreateDeploymentPayload): Promise<DeploymentDetails> {
  return request.post<DeploymentDetails>(DEPLOYMENT_ENDPOINTS.CREATE, payload)
}

/** io.net 部署设置 */
export function getDeploymentSettings(): Promise<DeploymentSettings> {
  return request.get<DeploymentSettings>(DEPLOYMENT_ENDPOINTS.SETTINGS)
}

/** 测试 io.net 连接 */
export function testDeploymentConnection(): Promise<TestConnectionResponse> {
  return request.post<TestConnectionResponse>(DEPLOYMENT_ENDPOINTS.TEST_CONNECTION)
}
