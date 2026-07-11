/**
 * useModelComparison — 多模型并行对比 composable。
 *
 * 独立于 useAiChat（单 Chat 实例不支持多流并发），
 * 创建 N 个独立 fetch + SSE 流式解析，并发限流 3 个。
 *
 * 最大 4 列，每列独立 AbortController。
 */
import { ref, computed } from 'vue'
import { getToken } from '@/utils/auth'
import type {
  ChatCompletionChunk,
  ChatCompletionMessage
} from '@/api/playground/types'

export interface ComparisonColumn {
  id: string
  model: string
  status: 'idle' | 'streaming' | 'done' | 'error'
  content: string
  reasoning?: string
  usage?: { prompt_tokens: number; completion_tokens: number; total_tokens: number }
  error?: string
}

const MAX_COLUMNS = 4
const MAX_CONCURRENT = 3

/** 从 SSE 事件块提取 data 内容 */
function extractData(block: string): string | null {
  const lines = block.split('\n')
  const dataLines: string[] = []
  for (const line of lines) {
    if (line.startsWith('data:')) {
      dataLines.push(line.length > 5 && line[5] === ' ' ? line.slice(6) : line.slice(5))
    }
  }
  return dataLines.length > 0 ? dataLines.join('\n') : null
}

export function useModelComparison(options: {
  systemPrompt: () => string
}) {
  const selectedModels = ref<string[]>([])
  const columns = ref<ComparisonColumn[]>([])
  const isComparing = computed(() =>
    columns.value.some((c) => c.status === 'streaming')
  )
  const prompt = ref('')

  const abortControllers = new Map<string, AbortController>()

  function addColumn(model: string): void {
    if (selectedModels.value.length >= MAX_COLUMNS) return
    if (selectedModels.value.includes(model)) return
    selectedModels.value = [...selectedModels.value, model]
    columns.value = [
      ...columns.value,
      { id: `col_${model}`, model, status: 'idle', content: '', reasoning: '' }
    ]
  }

  function removeColumn(model: string): void {
    // 中止该列的流（如有）并移除列
    const col = columns.value.find((c) => c.model === model)
    if (col) {
      const controller = abortControllers.get(col.id)
      if (controller) {
        controller.abort()
        abortControllers.delete(col.id)
      }
    }
    selectedModels.value = selectedModels.value.filter((m) => m !== model)
    columns.value = columns.value.filter((c) => c.model !== model)
  }

  function updateColumn(id: string, updates: Partial<ComparisonColumn>): void {
    columns.value = columns.value.map((c) =>
      c.id === id ? { ...c, ...updates } : c
    )
  }

  async function runComparison(): Promise<void> {
    if (selectedModels.value.length === 0) return

    // 列在 addColumn 时已创建，此处仅重置状态（保持 id 稳定）
    columns.value = columns.value.map((c) => ({
      ...c,
      status: 'idle' as const,
      content: '',
      reasoning: '',
      error: undefined,
      usage: undefined
    }))

    const queue = [...columns.value]

    async function runNext(): Promise<void> {
      const column = queue.shift()
      if (!column) return
      await runSingleColumn(column)
      if (queue.length > 0) {
        await runNext()
      }
    }

    const runnerCount = Math.min(MAX_CONCURRENT, queue.length)
    const runners = Array.from({ length: runnerCount }, () => runNext())
    await Promise.all(runners)
  }

  async function runSingleColumn(column: ComparisonColumn): Promise<void> {
    const controller = new AbortController()
    abortControllers.set(column.id, controller)

    updateColumn(column.id, {
      status: 'streaming',
      content: '',
      reasoning: '',
      error: undefined
    })

    try {
      const token = getToken()
      const baseUrl = import.meta.env.VITE_API_BASE || ''
      const sysPrompt = options.systemPrompt().trim()

      const messages: ChatCompletionMessage[] = []
      if (sysPrompt) {
        messages.push({ role: 'system', content: sysPrompt })
      }
      messages.push({ role: 'user', content: prompt.value })

      const response = await fetch(`${baseUrl}/pg/chat/completions`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          ...(token ? { 'yaoshu-token': token } : {})
        },
        body: JSON.stringify({
          model: column.model,
          messages,
          stream: true
        }),
        signal: controller.signal
      })

      if (!response.ok || !response.body) {
        let errorMsg = `HTTP ${response.status}`
        try {
          const errBody = await response.json()
          errorMsg = errBody?.error?.message || errBody?.message || errorMsg
        } catch {
          // 响应体解析失败
        }
        updateColumn(column.id, { status: 'error', error: errorMsg })
        return
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      let content = ''
      let reasoning = ''
      let usage: ComparisonColumn['usage']

      while (true) {
        if (controller.signal.aborted) break
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        let sepIndex: number
        while ((sepIndex = buffer.indexOf('\n\n')) !== -1) {
          const block = buffer.slice(0, sepIndex)
          buffer = buffer.slice(sepIndex + 2)

          const data = extractData(block)
          if (!data) continue
          if (data === '[DONE]') {
            updateColumn(column.id, { status: 'done', content, reasoning, usage })
            return
          }

          try {
            const parsed = JSON.parse(data) as ChatCompletionChunk
            if (parsed.usage) {
              usage = parsed.usage
            }
            const delta = parsed.choices?.[0]?.delta
            if (delta?.content) {
              content += delta.content
              updateColumn(column.id, { content })
            }
            if (delta?.reasoning_content) {
              reasoning += delta.reasoning_content
              updateColumn(column.id, { reasoning })
            }
          } catch {
            // JSON 解析失败，跳过
          }
        }
      }

      updateColumn(column.id, { status: 'done', content, reasoning, usage })
    } catch (e) {
      if (controller.signal.aborted) {
        updateColumn(column.id, { status: 'done' })
      } else {
        updateColumn(column.id, { status: 'error', error: (e as Error).message })
      }
    } finally {
      abortControllers.delete(column.id)
    }
  }

  function abortAll(): void {
    abortControllers.forEach((c) => c.abort())
    abortControllers.clear()
    columns.value = columns.value.map((c) =>
      c.status === 'streaming' ? { ...c, status: 'done' as const } : c
    )
  }

  function clearComparison(): void {
    abortAll()
    selectedModels.value = []
    columns.value = []
  }

  /** 单列重试：重置该列状态并重新运行（用于错误态重试） */
  async function retryColumn(id: string): Promise<void> {
    const column = columns.value.find((c) => c.id === id)
    if (!column) return
    await runSingleColumn(column)
  }

  return {
    selectedModels,
    columns,
    isComparing,
    prompt,
    addColumn,
    removeColumn,
    runComparison,
    retryColumn,
    abortAll,
    clearComparison
  }
}
