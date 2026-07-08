import { onScopeDispose, ref, type Ref } from 'vue'

/**
 * 媒体查询响应式 boolean。
 */
export function useMediaQuery(query: string): Ref<boolean> {
  const matches = ref<boolean>(false)

  // SSR 兜底：window 不可用时直接返回 false ref
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return matches
  }

  const media = window.matchMedia(query)
  matches.value = media.matches

  const handler = (event: MediaQueryListEvent): void => {
    matches.value = event.matches
  }
  media.addEventListener('change', handler)

  onScopeDispose(() => {
    media.removeEventListener('change', handler)
  })

  return matches
}
