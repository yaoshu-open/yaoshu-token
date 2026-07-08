/**
 * 上游模型更新 composable：检测/应用/批量操作 + 模态状态管理。
 *
 * 职责：封装上游更新 4 端点调用 + UpstreamUpdateDialog 模态状态，防重入。
 */
import { ref, readonly } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  applyAllUpstreamUpdates,
  applyUpstreamUpdates,
  detectAllUpstreamUpdates,
  detectUpstreamUpdates
} from '@/api/channel'
import {
  getManualIgnoredModelCount,
  normalizeModelList
} from '@/lib/channel/upstream-update-utils'
import type { Channel } from '@/api/channel/types'

export function useChannelUpstreamUpdates(
  refresh: () => Promise<void> | void
) {
  const { t } = useI18n()

  // 模态状态
  const showModal = ref(false)
  const channel = ref<{ id: number; name?: string } | null>(null)
  const addModels = ref<string[]>([])
  const removeModels = ref<string[]>([])
  const preferredTab = ref<'add' | 'remove'>('add')

  // 加载态
  const applyLoading = ref(false)
  const detectAllLoading = ref(false)
  const applyAllLoading = ref(false)

  // 防重入锁（非响应式）
  let applyLocked = false
  let detectLocked = false
  let detectAllLocked = false
  let applyAllLocked = false

  /** 打开 UpstreamUpdateDialog */
  function openModal(
    record: { id: number; name?: string } | null,
    pendingAdd: string[] = [],
    pendingRemove: string[] = [],
    tab: 'add' | 'remove' = 'add'
  ): void {
    const normAdd = normalizeModelList(pendingAdd)
    const normRemove = normalizeModelList(pendingRemove)
    if (!record?.id || (normAdd.length === 0 && normRemove.length === 0)) {
      ElMessage.info(t('channel.upstream.noProcessable'))
      return
    }
    channel.value = record
    addModels.value = normAdd
    removeModels.value = normRemove
    preferredTab.value = tab
    showModal.value = true
  }

  /** 关闭对话框并重置状态 */
  function closeModal(): void {
    showModal.value = false
    channel.value = null
    addModels.value = []
    removeModels.value = []
    preferredTab.value = 'add'
  }

  /** 应用单渠道更新（未选中的 add 模型归入 ignore_models） */
  async function applyUpdates(opts: {
    addModels?: string[]
    removeModels?: string[]
  } = {}): Promise<void> {
    if (applyLocked) return
    if (!channel.value?.id) {
      closeModal()
      return
    }
    applyLocked = true
    applyLoading.value = true
    try {
      const normSelectedAdd = normalizeModelList(opts.addModels || [])
      const selectedAddSet = new Set(normSelectedAdd)
      const ignoreModels = addModels.value.filter(
        (m) => !selectedAddSet.has(m)
      )

      const res = await applyUpstreamUpdates({
        id: channel.value.id,
        addModels: normSelectedAdd,
        ignoreModels: ignoreModels,
        removeModels: normalizeModelList(opts.removeModels || [])
      })

      const addedCount = res.addedModels?.length || 0
      const removedCount = res.removedModels?.length || 0
      const ignoredCount = normalizeModelList(ignoreModels).length
      const totalIgnored = getManualIgnoredModelCount(res.settings)
      ElMessage.success(
        t('channel.upstream.appliedSummary', {
          added: addedCount,
          removed: removedCount,
          ignored: ignoredCount,
          totalIgnored
        })
      )
      closeModal()
      await refresh()
    } catch (e) {
      ElMessage.error((e as Error)?.message || t('common.operationFailed'))
    } finally {
      applyLocked = false
      applyLoading.value = false
    }
  }

  /** 批量应用所有渠道更新 */
  async function applyAllUpdates(): Promise<void> {
    if (applyAllLocked) return
    applyAllLocked = true
    applyAllLoading.value = true
    try {
      const res = await applyAllUpstreamUpdates()
      ElMessage.success(
        t('channel.upstream.batchAppliedSummary', {
          channels: res.processedChannels || 0,
          added: res.addedModels || 0,
          removed: res.removedModels || 0,
          fails: (res.failedChannelIds || []).length
        })
      )
      await refresh()
    } catch (e) {
      ElMessage.error((e as Error)?.message || t('channel.upstream.batchFailed'))
    } finally {
      applyAllLocked = false
      applyAllLoading.value = false
    }
  }

  /** 检测单渠道上游变更 */
  async function detectChannelUpdates(
    ch: { id: number; name?: string } | Channel | null
  ): Promise<void> {
    if (detectLocked || !ch?.id) return
    detectLocked = true
    try {
      const res = await detectUpstreamUpdates(ch.id)
      const addCount = res.addModels?.length || 0
      const removeCount = res.removeModels?.length || 0

      // 检测到差异时自动打开对话框
      if (addCount > 0 || removeCount > 0) {
        openModal(ch, res.addModels, res.removeModels, addCount > 0 ? 'add' : 'remove')
      } else {
        ElMessage.success(
          t('channel.upstream.detectSummary', { add: addCount, remove: removeCount })
        )
      }
      await refresh()
    } catch (e) {
      ElMessage.error((e as Error)?.message || t('channel.upstream.detectFailed'))
    } finally {
      detectLocked = false
    }
  }

  /** 批量检测所有渠道上游变更 */
  async function detectAllUpdates(): Promise<void> {
    if (detectAllLocked) return
    detectAllLocked = true
    detectAllLoading.value = true
    try {
      const res = await detectAllUpstreamUpdates()
      ElMessage.success(
        t('channel.upstream.batchDetectSummary', {
          channels: res.processedChannels || 0,
          add: res.detectedAddModels || 0,
          remove: res.detectedRemoveModels || 0,
          fails: (res.failedChannelIds || []).length
        })
      )
      await refresh()
    } catch (e) {
      ElMessage.error(
        (e as Error)?.message || t('channel.upstream.batchDetectFailed')
      )
    } finally {
      detectAllLocked = false
      detectAllLoading.value = false
    }
  }

  return {
    showModal: readonly(showModal),
    channel: readonly(channel),
    addModels: readonly(addModels),
    removeModels: readonly(removeModels),
    preferredTab: readonly(preferredTab),
    applyLoading: readonly(applyLoading),
    detectAllLoading: readonly(detectAllLoading),
    applyAllLoading: readonly(applyAllLoading),
    openModal,
    closeModal,
    applyUpdates,
    applyAllUpdates,
    detectChannelUpdates,
    detectAllUpdates
  }
}
