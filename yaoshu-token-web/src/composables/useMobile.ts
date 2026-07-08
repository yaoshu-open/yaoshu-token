import { useMediaQuery } from './useMediaQuery'
export const MOBILE_BREAKPOINT = 768

export function useMobile() {
  return useMediaQuery(`(max-width: ${MOBILE_BREAKPOINT - 1}px)`)
}
