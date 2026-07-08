import { ref } from 'vue'

// 全局单例：模块作用域 ref，所有调用方共享同一实例
// 全局 UI 浮层开合态无需持久化/跨模块响应式共享，composable 单例足够
const isOpen = ref(false)

function handleKeydown(e: KeyboardEvent): void {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    isOpen.value = !isOpen.value
  }
}

export function useCommandMenu() {
  function open(): void {
    isOpen.value = true
  }

  function close(): void {
    isOpen.value = false
  }

  function toggle(): void {
    isOpen.value = !isOpen.value
  }

  return { isOpen, open, close, toggle, handleKeydown }
}

// 在 App.vue 挂载时注册全局 Cmd/K 监听
export function setupCommandMenuShortcut(): void {
  if (typeof window !== 'undefined') {
    window.addEventListener('keydown', handleKeydown)
  }
}

export function teardownCommandMenuShortcut(): void {
  if (typeof window !== 'undefined') {
    window.removeEventListener('keydown', handleKeydown)
  }
}
