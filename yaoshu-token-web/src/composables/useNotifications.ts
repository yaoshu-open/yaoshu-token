import { computed, ref } from 'vue'
import { useNotificationsStore } from '@/store/modules/notifications'
import { useStatus } from './useStatus'
import type { Announcement } from '@/api/system/types'
function hashString(input: string): string {
  let hash = 0
  if (!input) return '0'
  for (let i = 0; i < input.length; i += 1) {
    const chr = input.charCodeAt(i)
    hash = (hash << 5) - hash + chr
    hash |= 0
  }
  return hash.toString(36)
}
function getAnnouncementKey(item: Announcement): string {
  if (!item) return ''
  if (item.id !== undefined && item.id !== null) {
    return `id:${item.id}`
  }
  const fingerprint = JSON.stringify({
    publishDate: item.publishDate ?? '',
    content: ((item.content as string) ?? '').trim(),
    extra: ((item.extra as string) ?? '').trim(),
    type: item.type ?? '',
    title: ((item.title as string) ?? '').trim(),
    link: ((item.link as string) ?? '').trim()
  })
  return `hash:${hashString(fingerprint)}`
}

/**
 * 通知（Announcements）状态聚合。
 *
 * 从 useStatus 派生 announcements 列表（前 20 条 + announcements_enabled 开关），
 * 计算 unreadCount + 暴露 markRead 操作。
 */
export function useNotifications() {
  const { status } = useStatus()
  const notificationsStore = useNotificationsStore()

  const popoverOpen = ref<boolean>(false)

  const announcements = computed<Announcement[]>(() => {
    const s = status.value
    if (!s) return []
    const enabled = s.announcementsEnabled ?? false
    if (!enabled) return []
    const list = s.announcements ?? []
    return Array.isArray(list) ? list.slice(0, 20) : []
  })

  const unreadCount = computed(() =>
    announcements.value.filter((item) => {
      const key = getAnnouncementKey(item)
      return !notificationsStore.isAnnouncementRead(key)
    }).length
  )

  function markAllAnnouncementsRead(): void {
    const keys = announcements.value.map(getAnnouncementKey).filter(Boolean)
    notificationsStore.markAnnouncementsRead(keys)
  }

  return {
    popoverOpen,
    announcements,
    unreadCount,
    markAllAnnouncementsRead,
    isAnnouncementRead: notificationsStore.isAnnouncementRead.bind(
      notificationsStore
    ),
    getAnnouncementKey
  }
}
