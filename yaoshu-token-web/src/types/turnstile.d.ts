// Turnstile 全局类型声明：Cloudflare Turnstile widget API
// 来源：https://developers.cloudflare.com/turnstile/get-started/client-side-rendering/
// 仅声明前端使用的 render/reset/remove 三个方法 + 渲染参数

interface TurnstileRenderOptions {
  sitekey: string
  action?: string
  callback?: string
  'expired-callback'?: string
  'error-callback'?: string
  theme?: 'light' | 'dark' | 'auto'
  size?: 'normal' | 'flexible' | 'compact'
  [key: string]: unknown
}

interface Turnstile {
  // 渲染 widget：返回 widgetId 用于后续 reset/remove
  render(container: HTMLElement, options: TurnstileRenderOptions): string
  // 重置 widget：清除已有 token，重新触发挑战
  reset(widgetId?: string): void
  // 移除 widget：清理 DOM 与事件
  remove(widgetId: string): void
  // 获取当前 token（可选，一般通过 callback 拿）
  getResponse(widgetId?: string): string | undefined
}

declare global {
  interface Window {
    turnstile?: Turnstile
    // Turnstile 回调函数动态挂载（实例独立命名）
    [key: string]: unknown
  }
}

export {}
