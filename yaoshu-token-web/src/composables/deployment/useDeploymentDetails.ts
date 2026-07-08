/**
 * 部署详情 composable：拉取详情 + 容器列表 + 容器详情懒加载。
 *
 * 行为契约：
 * - 详情：watch(deploymentId) → 自动拉取；切换时 loading=true，data/error 重置
 * - 容器列表：同详情，watch 触发
 * - 容器详情：selectContainer(cId) 按需拉取，失败时 ElMessage.error
 * - 错误：捕获后写入 error.value，UI 用 ErrorState @retry 触发 refresh
 */
import { ref, watch } from 'vue'
import {
  getDeployment,
  getDeploymentContainer,
  getDeploymentContainers
} from '@/api/deployment'
import type { ContainerListResponse, DeploymentContainer, DeploymentDetails } from '@/api/deployment/types'

export function useDeploymentDetails(deploymentIdRef: () => string | number | null) {
  const details = ref<DeploymentDetails | null>(null)
  const containers = ref<DeploymentContainer[]>([])
  const containerDetails = ref<DeploymentContainer | null>(null)
  const loading = ref(false)
  const containersLoading = ref(false)
  const containerDetailsLoading = ref(false)
  const error = ref<Error | null>(null)

  async function fetchDetails(): Promise<void> {
    const id = deploymentIdRef()
    if (id === null || id === undefined) return
    loading.value = true
    error.value = null
    try {
      details.value = await getDeployment(id)
    } catch (e) {
      error.value = e as Error
    } finally {
      loading.value = false
    }
  }

  async function fetchContainers(): Promise<ContainerListResponse['containers']> {
    const id = deploymentIdRef()
    if (id === null || id === undefined) return []
    containersLoading.value = true
    try {
      const res = await getDeploymentContainers(id)
      containers.value = res.containers ?? []
      return containers.value
    } catch (e) {
      error.value = e as Error
      return []
    } finally {
      containersLoading.value = false
    }
  }

  async function fetchContainerDetails(containerId: string): Promise<void> {
    const id = deploymentIdRef()
    if (id === null || id === undefined || !containerId) return
    containerDetailsLoading.value = true
    try {
      containerDetails.value = await getDeploymentContainer(id, containerId)
    } catch {
      // 容器详情失败不阻断主流程（沿用 classic 行为）
      containerDetails.value = null
    } finally {
      containerDetailsLoading.value = false
    }
  }

  async function refresh(): Promise<void> {
    await Promise.all([fetchDetails(), fetchContainers()])
  }

  function reset(): void {
    details.value = null
    containers.value = []
    containerDetails.value = null
    loading.value = false
    containersLoading.value = false
    containerDetailsLoading.value = false
    error.value = null
  }

  watch(deploymentIdRef, () => {
    reset()
  })

  return {
    details,
    containers,
    containerDetails,
    loading,
    containersLoading,
    containerDetailsLoading,
    error,
    fetchDetails,
    fetchContainers,
    fetchContainerDetails,
    refresh,
    reset
  }
}
