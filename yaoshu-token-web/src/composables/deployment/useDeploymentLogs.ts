/**
 * 部署日志 composable：日志拉取 + 客户端搜索过滤 + auto-refresh + follow + 滚动管理。
 *
 * 关键改进（vs classic）：
 * - isAtBottom 检测：用户向上滚动查看历史时不强制滚回（避免 UX 撕裂）
 * - 复制/下载以 filteredLogs 优先（语义统一）
 * - 日志行 key 用 index+lineHash，避免渲染闪烁
 *
 * 状态机：
 * - 加载: loading + logLines 空 → 显示 LoadingState
 * - 空容器: 容器列表空 → EmptyState
 * - 无匹配: logLines 非空但 filteredLogs 空 → 提示
 * - 错误: error → ErrorState @retry
 */
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { getDeploymentLogs } from '@/api/deployment'
import { ALL_CONTAINERS, DEFAULT_STREAM, LOG_AUTO_REFRESH_INTERVAL } from '@/api/deployment/constants'
import type { DeploymentStream } from '@/api/deployment/types'

export interface UseDeploymentLogsOptions {
  /** 自动滚动到底部回调（由 useDeploymentLogsList 容器暴露） */
  scrollToBottom?: () => void
  /** 滚动事件回调（外部 useDeploymentLogsList 监听 scroll 后回传 isAtBottom） */
  onAutoRefreshTick?: () => void
}

export function useDeploymentLogs(
  deploymentIdRef: () => string | number | null,
  selectedContainerIdRef: () => string,
  options?: UseDeploymentLogsOptions
) {
  const { t } = useI18n()

  const logLines = ref<string[]>([])
  const loading = ref(false)
  const error = ref<Error | null>(null)
  const searchTerm = ref('')
  const autoRefresh = ref(false)
  const following = ref(false)
  const streamFilter = ref<DeploymentStream>(DEFAULT_STREAM)
  const lastUpdatedAt = ref<Date | null>(null)
  const isAtBottom = ref(true)

  let autoRefreshTimer: ReturnType<typeof setInterval> | null = null

  const filteredLogs = computed<string[]>(() => {
    const term = searchTerm.value.trim().toLowerCase()
    if (!term) return logLines.value
    return logLines.value.filter((line) => (line ?? '').toLowerCase().includes(term))
  })

  function setIsAtBottom(value: boolean): void {
    isAtBottom.value = value
  }

  function clearAutoRefreshTimer(): void {
    if (autoRefreshTimer) {
      clearInterval(autoRefreshTimer)
      autoRefreshTimer = null
    }
  }

  async function fetchLogs(): Promise<void> {
    const id = deploymentIdRef()
    const containerId = selectedContainerIdRef()
    if (id === null || id === undefined) return
    if (!containerId || containerId === ALL_CONTAINERS) {
      logLines.value = []
      lastUpdatedAt.value = null
      loading.value = false
      return
    }
    loading.value = true
    error.value = null
    try {
      const streamValue = streamFilter.value === DEFAULT_STREAM ? 'stdout' : streamFilter.value
      const raw = await getDeploymentLogs(id, {
        container_id: containerId,
        stream: streamValue,
        follow: following.value
      })
      const normalized = (raw ?? '').replace(/\r\n?/g, '\n')
      logLines.value = normalized ? normalized.split('\n') : []
      lastUpdatedAt.value = new Date()
      // follow 模式 + 用户在底部 → 自动滚到底
      if (following.value && isAtBottom.value) {
        options?.scrollToBottom?.()
      }
    } catch (e) {
      error.value = e as Error
    } finally {
      loading.value = false
    }
  }

  function downloadLogs(): void {
    const source = filteredLogs.value.length > 0 ? filteredLogs.value : logLines.value
    if (source.length === 0) {
      // 由调用方在 UI 层禁用按钮；此处兜底静默
      return
    }
    const text = source.join('\n')
    const blob = new Blob([text], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const id = String(deploymentIdRef() ?? 'deployment')
    const safeCid =
      selectedContainerIdRef() !== ALL_CONTAINERS
        ? selectedContainerIdRef().replace(/[^a-zA-Z0-9_-]/g, '-')
        : ''
    a.download = safeCid
      ? `deployment-${id}-container-${safeCid}-logs.txt`
      : `deployment-${id}-logs.txt`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

  function copyLogsText(): string {
    const source = filteredLogs.value.length > 0 ? filteredLogs.value : logLines.value
    return source.join('\n')
  }

  function reset(): void {
    logLines.value = []
    loading.value = false
    error.value = null
    searchTerm.value = ''
    autoRefresh.value = false
    following.value = false
    streamFilter.value = DEFAULT_STREAM
    lastUpdatedAt.value = null
    isAtBottom.value = true
    clearAutoRefreshTimer()
  }

  // 自动刷新：每 5s 拉取（classic 沿用）
  watch(
    [autoRefresh, () => deploymentIdRef(), selectedContainerIdRef, streamFilter, following],
    ([enabled]) => {
      clearAutoRefreshTimer()
      if (enabled && deploymentIdRef() !== null) {
        autoRefreshTimer = setInterval(() => {
          void fetchLogs()
        }, LOG_AUTO_REFRESH_INTERVAL)
      }
    },
    { immediate: true }
  )

  // 选中容器变化：清空日志 + 立即拉取新容器
  watch(selectedContainerIdRef, () => {
    logLines.value = []
    lastUpdatedAt.value = null
    void fetchLogs()
  })

  watch(deploymentIdRef, () => {
    reset()
  })

  onUnmounted(() => {
    clearAutoRefreshTimer()
  })

  // 暴露 i18n 提示文案以供消费方复用
  const i18nKeys = {
    emptyTitle: t('deployment.logs.emptyTitle'),
    noMatchTitle: t('deployment.logs.noMatchTitle'),
    copyAll: t('deployment.logs.copyAll')
  }

  return {
    logLines,
    filteredLogs,
    loading,
    error,
    searchTerm,
    autoRefresh,
    following,
    streamFilter,
    lastUpdatedAt,
    isAtBottom,
    fetchLogs,
    downloadLogs,
    copyLogsText,
    setIsAtBottom,
    reset,
    i18nKeys
  }
}
