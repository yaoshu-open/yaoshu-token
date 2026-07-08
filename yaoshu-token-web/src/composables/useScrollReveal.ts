import type { Directive } from 'vue'

interface ScrollRevealOptions {
  delay?: number
  threshold?: number
  rootMargin?: string
}

const observers = new WeakMap<HTMLElement, IntersectionObserver>()

export const vScrollReveal: Directive<HTMLElement, ScrollRevealOptions | number | undefined> = {
  mounted(el, binding) {
    const value = binding.value
    const delay = typeof value === 'number' ? value : value?.delay ?? 0
    const threshold = typeof value === 'object' && value ? value.threshold ?? 0.1 : 0.1
    const rootMargin = typeof value === 'object' && value ? value.rootMargin ?? '0px 0px -10% 0px' : '0px 0px -10% 0px'

    // 降级：用户偏好减少动画时直接显示
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return
    }

    el.style.opacity = '0'
    el.style.transform = 'translateY(30px)'
    el.style.transition = `opacity 0.6s ease ${delay}ms, transform 0.6s ease ${delay}ms`

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            el.style.opacity = '1'
            el.style.transform = 'translateY(0)'
            observer.unobserve(el)
          }
        })
      },
      { rootMargin, threshold }
    )

    observer.observe(el)
    observers.set(el, observer)
  },
  unmounted(el) {
    observers.get(el)?.disconnect()
    observers.delete(el)
  }
}
