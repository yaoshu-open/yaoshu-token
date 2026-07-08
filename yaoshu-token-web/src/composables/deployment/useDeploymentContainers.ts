/**
 * 容器列表 + 选中 + 状态映射 composable。
 *
 * 行为契约：
 * - 拉取：watch(deploymentId) → 自动 fetch + 选中第一个容器（classic 行为）
 * - 切换：selectContainer(cId) → 选新容器，返回旧值用于外部清空日志
 * - 状态：getStatusConfig(s) → {color, label} 统一映射，避免组件内重复实现
 */
import { computed, ref, watch } from 'vue'
import { getDeploymentContainer, getDeploymentContainers } from '@/api/deployment'
import { ALL_CONTAINERS } from '@/api/deployment/constants'
import type { DeploymentContainer, DeploymentStatus } from '@/api/deployment/types'

const CONTAINER_STATUS_COLOR: Record<string, string> = {
  running: 'green',
  pending: 'orange',
  deployed: 'blue',
  failed: 'red',
  destroyed: 'red',
  stopping: 'orange',
  terminated: 'grey'
}

export interface ContainerStatusConfig {
  color: string
  label: string
}

export function useDeploymentContainers(deploymentIdRef: () => string | number | null) {
  const containers = ref<DeploymentContainer[]>([])
  const selectedContainerId = ref<string>(ALL_CONTAINERS)
  const containerDetails = ref<DeploymentContainer | null>(null)
  const containersLoading = ref(false)
  const containerDetailsLoading = ref(false)

  const selectedContainer = computed<DeploymentContainer | null>(() => {
    if (selectedContainerId.value === ALL_CONTAINERS) return null
    return containers.value.find((c) => c.container_id === selectedContainerId.value) ?? null
  })

  function getStatusConfig(status: DeploymentStatus | string | undefined): ContainerStatusConfig {
    const key = String(status ?? '').toLowerCase()
    return {
      color: CONTAINER_STATUS_COLOR[key] ?? 'grey',
      label: key || 'unknown'
    }
  }

  async function fetchContainers(): Promise<void> {
    const id = deploymentIdRef()
    if (id === null || id === undefined) return
    containersLoading.value = true
    try {
      const res = await getDeploymentContainers(id)
      const list = res.containers ?? []
      containers.value = list
      // 保留有效选中，否则选第一个
      if (
        selectedContainerId.value !== ALL_CONTAINERS &&
        list.some((c) => c.container_id === selectedContainerId.value)
      ) {
        return
      }
      selectedContainerId.value = list.length > 0 ? list[0].container_id : ALL_CONTAINERS
    } finally {
      containersLoading.value = false
    }
  }

  async function fetchContainerDetails(containerId: string): Promise<void> {
    const id = deploymentIdRef()
    if (id === null || id === undefined || !containerId || containerId === ALL_CONTAINERS) {
      containerDetails.value = null
      return
    }
    containerDetailsLoading.value = true
    try {
      containerDetails.value = await getDeploymentContainer(id, containerId)
    } catch {
      containerDetails.value = null
    } finally {
      containerDetailsLoading.value = false
    }
  }

  function selectContainer(containerId: string): void {
    const newValue = containerId || ALL_CONTAINERS
    selectedContainerId.value = newValue
  }

  function reset(): void {
    containers.value = []
    selectedContainerId.value = ALL_CONTAINERS
    containerDetails.value = null
    containersLoading.value = false
    containerDetailsLoading.value = false
  }

  watch(deploymentIdRef, () => {
    reset()
  })

  return {
    containers,
    selectedContainerId,
    selectedContainer,
    containerDetails,
    containersLoading,
    containerDetailsLoading,
    fetchContainers,
    fetchContainerDetails,
    selectContainer,
    getStatusConfig,
    reset
  }
}
