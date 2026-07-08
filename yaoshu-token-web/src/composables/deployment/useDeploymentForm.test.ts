/**
 * useDeploymentForm 单元测试。
 * 覆盖点：
 *   1. createInitialForm 返回默认状态
 *   2. resetForm 重置表单
 *   3. buildPayload 将扁平表单转换为 io.net API 嵌套结构
 */
import { describe, expect, it } from 'vitest'
import {
  createInitialForm,
  useDeploymentForm
} from '@/composables/deployment/useDeploymentForm'

describe('useDeploymentForm', () => {
  it('createInitialForm 返回默认状态', () => {
    const form = createInitialForm()
    expect(form.resource_private_name).toBe('')
    expect(form.duration_hours).toBe(1)
    expect(form.gpus_per_container).toBe(1)
    expect(form.replica_count).toBe(1)
    expect(form.hardware_id).toBeNull()
    expect(form.location_ids).toEqual([])
    expect(form.env_variables).toEqual([])
    expect(form.secret_env_variables).toEqual([])
    expect(form.currency).toBe('usdc')
  })

  it('resetForm 重置表单到默认状态', () => {
    const { form, resetForm } = useDeploymentForm()
    form.resource_private_name = 'test-deploy'
    form.hardware_id = 7
    form.location_ids = [1, 2]
    form.env_variables.push({ key: 'KEY1', value: 'val1' })

    resetForm()

    expect(form.resource_private_name).toBe('')
    expect(form.hardware_id).toBeNull()
    expect(form.location_ids).toEqual([])
    expect(form.env_variables).toEqual([])
  })

  it('buildPayload 将扁平表单转换为 io.net API 嵌套结构', () => {
    const { form, buildPayload } = useDeploymentForm()
    form.resource_private_name = 'my-deploy'
    form.image_url = 'registry.example.com/app:v1'
    form.duration_hours = 24
    form.hardware_id = 7
    form.gpus_per_container = 2
    form.replica_count = 3
    form.location_ids = [1, 2]
    form.env_variables = [
      { key: 'DEBUG', value: 'true' },
      { key: '', value: 'ignored' },
      { key: '  trimmed  ', value: 'val' }
    ]
    form.secret_env_variables = [{ key: 'TOKEN', value: 'secret' }]
    form.entrypoint = 'python serve.py --port 8080'
    form.args = '--debug --verbose'
    form.traffic_port = 8080
    form.registry_username = 'user'
    form.registry_secret = 'pass'

    const payload = buildPayload()

    expect(payload.resource_private_name).toBe('my-deploy')
    expect(payload.duration_hours).toBe(24)
    expect(payload.gpus_per_container).toBe(2)
    expect(payload.hardware_id).toBe(7)
    expect(payload.location_ids).toEqual([1, 2])
    expect(payload.container_config.replica_count).toBe(3)
    expect(payload.container_config.env_variables).toEqual({
      DEBUG: 'true',
      trimmed: 'val'
    })
    expect(payload.container_config.secret_env_variables).toEqual({
      TOKEN: 'secret'
    })
    expect(payload.container_config.entrypoint).toEqual([
      'python',
      'serve.py',
      '--port',
      '8080'
    ])
    expect(payload.container_config.args).toEqual(['--debug', '--verbose'])
    expect(payload.container_config.traffic_port).toBe(8080)
    expect(payload.registry_config.image_url).toBe('registry.example.com/app:v1')
    expect(payload.registry_config.registry_username).toBe('user')
    expect(payload.registry_config.registry_secret).toBe('pass')
    expect(payload.currency).toBe('usdc')
  })

  it('buildPayload 空字段处理（entrypoint/args/registry 可选）', () => {
    const { form, buildPayload } = useDeploymentForm()
    form.resource_private_name = 'test'
    form.image_url = 'img'
    form.hardware_id = 1
    form.location_ids = [1]

    const payload = buildPayload()

    expect(payload.container_config.entrypoint).toBeUndefined()
    expect(payload.container_config.args).toBeUndefined()
    expect(payload.container_config.traffic_port).toBeUndefined()
    expect(payload.registry_config.registry_username).toBeUndefined()
    expect(payload.registry_config.registry_secret).toBeUndefined()
  })
})
