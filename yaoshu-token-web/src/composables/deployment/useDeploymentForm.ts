/**
 * 部署创建表单状态聚合。
 *
 * 设计：reactive 引用，子组件直接修改字段（无 Props/Emits 往返）。
 * buildPayload() 将扁平表单转换为 io.net API 嵌套结构（container_config + registry_config）。
 */
import { reactive } from 'vue'
import type { CreateDeploymentPayload } from '@/api/deployment/types'

export interface EnvVarItem {
  key: string
  value: string
}

export interface DeploymentFormState {
  // Step 1 基本信息
  resource_private_name: string
  image_url: string
  duration_hours: number
  // Step 2 硬件
  hardware_id: number | null
  gpus_per_container: number
  replica_count: number
  // Step 3 地区
  location_ids: number[]
  // Step 4 高级
  env_variables: EnvVarItem[]
  secret_env_variables: EnvVarItem[]
  entrypoint: string
  args: string
  traffic_port: number | null
  registry_username: string
  registry_secret: string
  // 货币
  currency: 'usdc'
}

export function createInitialForm(): DeploymentFormState {
  return {
    resource_private_name: '',
    image_url: '',
    duration_hours: 1,
    hardware_id: null,
    gpus_per_container: 1,
    replica_count: 1,
    location_ids: [],
    env_variables: [],
    secret_env_variables: [],
    entrypoint: '',
    args: '',
    traffic_port: null,
    registry_username: '',
    registry_secret: '',
    currency: 'usdc'
  }
}

export function useDeploymentForm() {
  const form = reactive<DeploymentFormState>(createInitialForm())

  function resetForm(): void {
    Object.assign(form, createInitialForm())
  }

  /** 将扁平表单转换为 io.net API 嵌套结构 */
  function buildPayload(): CreateDeploymentPayload {
    const envVars: Record<string, string> = {}
    for (const item of form.env_variables) {
      const k = item.key.trim()
      if (k) envVars[k] = item.value
    }
    const secretVars: Record<string, string> = {}
    for (const item of form.secret_env_variables) {
      const k = item.key.trim()
      if (k) secretVars[k] = item.value
    }
    return {
      resource_private_name: form.resource_private_name,
      duration_hours: form.duration_hours,
      gpus_per_container: form.gpus_per_container,
      hardware_id: form.hardware_id as number,
      location_ids: form.location_ids,
      container_config: {
        replica_count: form.replica_count,
        env_variables: envVars,
        secret_env_variables: secretVars,
        entrypoint: form.entrypoint.trim() ? form.entrypoint.trim().split(/\s+/) : undefined,
        args: form.args.trim() ? form.args.trim().split(/\s+/) : undefined,
        traffic_port: form.traffic_port ?? undefined
      },
      registry_config: {
        image_url: form.image_url,
        registry_username: form.registry_username || undefined,
        registry_secret: form.registry_secret || undefined
      },
      currency: form.currency
    }
  }

  return {
    form,
    resetForm,
    buildPayload
  }
}
