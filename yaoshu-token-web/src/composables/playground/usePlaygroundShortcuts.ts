/**
 * usePlaygroundShortcuts — Playground 全局快捷键体系。
 *
 * 基于原生 keydown（对齐 useCommandMenu 模式，不引入库）。
 * 全局快捷键：window keydown 监听。
 * 输入区快捷键（Cmd+1/2/3 角色切换）：同样在全局 handler 中判断。
 */
export interface ShortcutActions {
  clear: () => void
  regenerate: () => void
  stop: () => void
  saveTemplate: () => void
  setRole: (role: 'user' | 'assistant' | 'system') => void
  closeDrawer: () => void
  showHelp: () => void
}

function isMac(): boolean {
  return navigator.platform.toUpperCase().includes('MAC')
}

function isModifierPressed(e: KeyboardEvent): boolean {
  return isMac() ? e.metaKey : e.ctrlKey
}

function isInputFocused(): boolean {
  const el = document.activeElement
  if (!el) return false
  const tag = el.tagName.toLowerCase()
  return tag === 'input' || tag === 'textarea' || (el as HTMLElement).isContentEditable
}

export function usePlaygroundShortcuts(actions: ShortcutActions) {
  function handleKeydown(e: KeyboardEvent): void {
    const mod = isModifierPressed(e)

    // Cmd/Ctrl + L: 清空对话
    if (mod && e.key === 'l' && !e.shiftKey) {
      e.preventDefault()
      actions.clear()
      return
    }

    // Cmd/Ctrl + Shift + R: 重新生成
    if (mod && e.shiftKey && (e.key === 'R' || e.key === 'r')) {
      e.preventDefault()
      actions.regenerate()
      return
    }

    // Cmd/Ctrl + .: 停止生成
    if (mod && e.key === '.') {
      e.preventDefault()
      actions.stop()
      return
    }

    // Cmd/Ctrl + Shift + S: 保存模板
    if (mod && e.shiftKey && (e.key === 'S' || e.key === 's')) {
      e.preventDefault()
      actions.saveTemplate()
      return
    }

    // Cmd/Ctrl + 1/2/3: 切换角色（输入区焦点时生效）
    if (mod && ['1', '2', '3'].includes(e.key) && isInputFocused()) {
      e.preventDefault()
      const roles = ['user', 'assistant', 'system'] as const
      const idx = parseInt(e.key) - 1
      actions.setRole(roles[idx])
      return
    }

    // ?: 唤起快捷键帮助（非输入区焦点时）
    if (e.key === '?' && !isInputFocused()) {
      e.preventDefault()
      actions.showHelp()
      return
    }

    // Esc: 关闭抽屉
    if (e.key === 'Escape') {
      actions.closeDrawer()
      return
    }
  }

  function setup(): void {
    window.addEventListener('keydown', handleKeydown)
  }

  function teardown(): void {
    window.removeEventListener('keydown', handleKeydown)
  }

  return { setup, teardown }
}

/** 快捷键表（供帮助弹窗展示） */
export const SHORTCUT_TABLE: Array<{ keys: string; actionKey: string }> = [
  { keys: '⌘/Ctrl + L', actionKey: 'playground.shortcuts.clear' },
  { keys: '⌘/Ctrl + Shift + R', actionKey: 'playground.shortcuts.regenerate' },
  { keys: '⌘/Ctrl + .', actionKey: 'playground.shortcuts.stop' },
  { keys: '⌘/Ctrl + Shift + S', actionKey: 'playground.shortcuts.saveTemplate' },
  { keys: '⌘/Ctrl + 1/2/3', actionKey: 'playground.shortcuts.switchRole' },
  { keys: '?', actionKey: 'playground.shortcuts.showHelp' },
  { keys: 'Esc', actionKey: 'playground.shortcuts.close' }
]
