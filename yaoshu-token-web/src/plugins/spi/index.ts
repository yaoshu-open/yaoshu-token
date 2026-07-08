import type { App, Component } from 'vue'
import { registerComponent } from './registry'
// 使用 @ 别名导入扩展入口，定制构建时通过 Vite alias 覆盖此模块
import { setupExtensions } from '@/plugins/spi/extensions-entry'

// 开源默认注册的组件插槽（随扩展需求增长在此注册默认组件）
const defaultComponents: Record<string, Component> = {}

export function setupSpiExtensions(app: App): void {
  // 1. 注册开源默认组件
  for (const [slot, component] of Object.entries(defaultComponents)) {
    registerComponent(slot, component)
  }
  // 2. 调用扩展入口（开源版为 no-op，定制实现通过 alias 覆盖注入）
  setupExtensions(app)
}
