/**
 * 设置表单状态管理 composable。
 *
 * 职责：表单状态 + dirty tracking + 批量保存。
 */
import { computed, reactive, ref, watch } from 'vue'
import type { UpdateOptionRequest } from '@/api/system-option/types'

interface SettingsFormOptions<T extends object> {
  defaults: T
  /** 从 option key 到表单字段的映射（用于批量保存） */
  optionKeyMap?: Record<string, keyof T>
}

export function useSettingsForm<T extends object>(options: SettingsFormOptions<T>) {
  const form = reactive<T>({ ...options.defaults })
  const initial = ref<T>({ ...options.defaults })
  const dirty = ref(false)

  watch(
    form,
    (val) => {
      dirty.value = JSON.stringify(val) !== JSON.stringify(initial.value)
    },
    { deep: true },
  )

  function reset() {
    Object.assign(form, initial.value)
    dirty.value = false
  }

  function setDefaults(values: Partial<T>) {
    Object.assign(form, values)
    initial.value = { ...form } as T
    dirty.value = false
  }

  /** 构建批量保存 payload */
  function buildPayload(optionKeyMap: Record<string, keyof T>): UpdateOptionRequest[] {
    const payload: UpdateOptionRequest[] = []
    const formRecord = form as Record<string, unknown>
    const initialRecord = initial.value as Record<string, unknown>
    for (const [optionKey, formKey] of Object.entries(optionKeyMap)) {
      const val = formRecord[formKey as string]
      if (val !== initialRecord[formKey as string]) {
        payload.push({
          key: optionKey,
          value: typeof val === 'boolean' ? String(val) : (val as string | number),
        })
      }
    }
    return payload
  }

  const isDirty = computed(() => dirty.value)

  return { form, dirty: isDirty, reset, setDefaults, buildPayload }
}
