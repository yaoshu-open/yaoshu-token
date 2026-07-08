import type { App } from 'vue'

// SPI 扩展点占位入口，定制实现通过构建期 alias 覆盖注入
export function setupExtensions(_app: App): void {
  // no-op
}
