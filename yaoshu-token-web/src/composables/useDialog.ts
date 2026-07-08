import { computed, ref, type Ref } from 'vue'

// 单 dialog 状态契约
export interface DialogHandlers {
  open: () => void
  close: () => void
  toggle: () => void
}

/**
 * 单 dialog 开闭管理。
 *
 * 用法：
 *   const { isOpen, open, close, toggle } = useDialog()
 */
export function useDialog(initialOpen = false): DialogHandlers & {
  isOpen: Ref<boolean>
} {
  const isOpen = ref<boolean>(initialOpen)
  const open = (): void => {
    isOpen.value = true
  }
  const close = (): void => {
    isOpen.value = false
  }
  const toggle = (): void => {
    isOpen.value = !isOpen.value
  }
  return { isOpen, open, close, toggle }
}

// 多 dialog 集合契约
export interface DialogsHandlers<T extends string> {
  isOpen: (key: T) => boolean
  open: (key: T) => void
  close: (key: T) => void
  toggle: (key: T) => void
  closeAll: () => void
  hasAnyOpen: Ref<boolean>
}

/**
 * 多 dialog 开闭管理（同一组件管理 N 个具名 dialog 时使用，避免 N 个 ref）。
 *
 * 用法：
 *   const dialogs = useDialogs<'edit' | 'delete' | 'detail'>()
 *   dialogs.open('edit')
 *   v-if="dialogs.isOpen('edit')"
 */
export function useDialogs<T extends string>(): DialogsHandlers<T> {
  const openSet = ref<Set<T>>(new Set()) as Ref<Set<T>>

  const isOpen = (key: T): boolean => openSet.value.has(key)
  const open = (key: T): void => {
    if (!openSet.value.has(key)) {
      openSet.value.add(key)
      // 触发响应式：Set 直接 add 不会触发，需要重新赋值
      openSet.value = new Set(openSet.value)
    }
  }
  const close = (key: T): void => {
    if (openSet.value.has(key)) {
      const next = new Set(openSet.value)
      next.delete(key)
      openSet.value = next
    }
  }
  const toggle = (key: T): void => {
    if (openSet.value.has(key)) close(key)
    else open(key)
  }
  const closeAll = (): void => {
    if (openSet.value.size > 0) openSet.value = new Set()
  }
  const hasAnyOpen = computed(() => openSet.value.size > 0)

  return { isOpen, open, close, toggle, closeAll, hasAnyOpen }
}
