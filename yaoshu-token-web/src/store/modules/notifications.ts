import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

const STORAGE_KEY = 'yaoshu_notification_state'

// 持久化结构：上次已读公告 hash + 已读 announcement keys
interface PersistedState {
  lastReadNotice: string
  readAnnouncementKeys: string[]
}

function readPersisted(): PersistedState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { lastReadNotice: '', readAnnouncementKeys: [] }
    const parsed = JSON.parse(raw) as Partial<PersistedState>
    return {
      lastReadNotice:
        typeof parsed.lastReadNotice === 'string' ? parsed.lastReadNotice : '',
      readAnnouncementKeys: Array.isArray(parsed.readAnnouncementKeys)
        ? parsed.readAnnouncementKeys.filter(
            (k): k is string => typeof k === 'string'
          )
        : []
    }
  } catch {
    return { lastReadNotice: '', readAnnouncementKeys: [] }
  }
}

function writePersisted(state: PersistedState): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  } catch {
    /* 持久化失败不阻塞业务 */
  }
}

export const useNotificationsStore = defineStore('notifications', () => {
  const initial = readPersisted()

  const lastReadNotice = ref<string>(initial.lastReadNotice)
  // Set 实现 O(1) 查询
  const readAnnouncementKeys = ref<Set<string>>(
    new Set(initial.readAnnouncementKeys)
  )

  function persist(): void {
    writePersisted({
      lastReadNotice: lastReadNotice.value,
      readAnnouncementKeys: Array.from(readAnnouncementKeys.value)
    })
  }

  // 标记 notice（公告字符串内容本身作为指纹）已读
  function markNoticeRead(noticeContent: string): void {
    const trimmed = (noticeContent ?? '').trim()
    if (!trimmed) return
    lastReadNotice.value = trimmed
    persist()
  }

  // 批量标记若干 announcement key 已读
  function markAnnouncementsRead(keys: string[]): void {
    if (!keys.length) return
    let changed = false
    for (const k of keys) {
      if (!readAnnouncementKeys.value.has(k)) {
        readAnnouncementKeys.value.add(k)
        changed = true
      }
    }
    if (changed) persist()
  }

  function isAnnouncementRead(key: string): boolean {
    return readAnnouncementKeys.value.has(key)
  }

  function reset(): void {
    lastReadNotice.value = ''
    readAnnouncementKeys.value = new Set()
    persist()
  }

  // 派生：当前已读 key 数量（供 UI 调试或统计）
  const readCount = computed(() => readAnnouncementKeys.value.size)

  return {
    lastReadNotice,
    readAnnouncementKeys,
    readCount,
    markNoticeRead,
    markAnnouncementsRead,
    isAnnouncementRead,
    reset
  }
})
