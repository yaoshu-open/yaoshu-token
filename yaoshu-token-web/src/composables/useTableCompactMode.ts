import { onScopeDispose, ref, type Ref } from 'vue'

const STORAGE_KEY = 'table_compact_modes'

// 模块级共享 ref 池：相同 tableKey 的所有调用方共享同一个 ref 实例
const sharedRefs = new Map<string, Ref<boolean>>()

function readMap(): Record<string, boolean> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object'
      ? (parsed as Record<string, boolean>)
      : {}
  } catch {
    return {}
  }
}

function writeMap(map: Record<string, boolean>): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(map))
  } catch {
    /* 隐私模式/配额满：忽略持久化失败 */
  }
}

/**
 * 表格紧凑模式：按 tableKey 维度持久化到 localStorage，跨标签页同步（storage 事件）。
 * 相同 tableKey 的所有调用方共享同一个 ref 实例，确保开关切换时所有消费方同步更新。
 *
 * 返回：[compact ref, setCompact 函数]
 */
export function useTableCompactMode(
  tableKey = 'global'
): [Ref<boolean>, (value: boolean) => void] {
  // 复用模块级共享 ref，避免每次调用创建独立 ref 导致状态不同步
  let compact = sharedRefs.get(tableKey)
  if (!compact) {
    compact = ref<boolean>(Boolean(readMap()[tableKey]))
    sharedRefs.set(tableKey, compact)
  }

  function setCompact(value: boolean): void {
    compact!.value = value
    const map = readMap()
    map[tableKey] = value
    writeMap(map)
  }

  // 跨标签页同步
  function handleStorage(event: StorageEvent): void {
    if (event.key !== STORAGE_KEY) return
    try {
      const next = event.newValue
        ? (JSON.parse(event.newValue) as Record<string, boolean>)
        : {}
      compact!.value = Boolean(next[tableKey])
    } catch {
      /* 解析失败保持当前值 */
    }
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('storage', handleStorage)
    onScopeDispose(() => {
      window.removeEventListener('storage', handleStorage)
    })
  }

  return [compact, setCompact]
}
