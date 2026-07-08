/**
 * usePromptTemplates — Playground 预设模板管理（localStorage 持久化）。
 *
 * 模板保存当前 config/systemPrompt/prompt 的快照，支持一键加载恢复。
 * 与 ConfigManager 区别：ConfigManager 是文件导入导出（设备迁移），
 * 模板是应用内一键切换（高频复用）。
 */
import { ref, watch } from 'vue'
import type { PlaygroundConfig, ParameterEnabled } from '@/api/playground/types'

export interface PromptTemplate {
  id: string
  name: string
  icon?: string
  prompt?: string
  systemPrompt?: string
  config?: Partial<PlaygroundConfig>
  parameterEnabled?: Partial<ParameterEnabled>
  createdAt: number
}

const STORAGE_KEY = 'playground_templates'

function loadTemplates(): PromptTemplate[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    return JSON.parse(raw) as PromptTemplate[]
  } catch {
    return []
  }
}

function persistTemplates(templates: PromptTemplate[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(templates))
  } catch {
    // localStorage 不可用时静默跳过
  }
}

export function usePromptTemplates() {
  const templates = ref<PromptTemplate[]>(loadTemplates())

  watch(templates, (next) => {
    persistTemplates(next)
  }, { deep: true })

  function addTemplate(template: Omit<PromptTemplate, 'id' | 'createdAt'>): PromptTemplate {
    const newTemplate: PromptTemplate = {
      ...template,
      id: `tpl_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      createdAt: Date.now()
    }
    templates.value = [...templates.value, newTemplate]
    return newTemplate
  }

  function deleteTemplate(id: string): void {
    templates.value = templates.value.filter((t) => t.id !== id)
  }

  function renameTemplate(id: string, name: string): void {
    templates.value = templates.value.map((t) =>
      t.id === id ? { ...t, name } : t
    )
  }

  return {
    templates,
    addTemplate,
    deleteTemplate,
    renameTemplate
  }
}
