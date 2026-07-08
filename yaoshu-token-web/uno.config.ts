import { defineConfig, presetUno, presetAttributify, presetIcons } from 'unocss'

export default defineConfig({
  presets: [
    presetUno(),
    presetAttributify(),
    presetIcons({
      scale: 1.2,
      warn: true,
      mode: 'background-img'
    })
  ],
  // Design Token 主题映射：使 UnoCSS 原子类可用 token 变量
  // 设计见 设计_DesignToken与样式重构.md §五
  theme: {
    colors: {
      primary: 'var(--ys-color-primary)',
      success: 'var(--el-color-success)',
      warning: 'var(--el-color-warning)',
      danger: 'var(--el-color-danger)'
    },
    spacing: {
      1: '4px',
      2: '8px',
      3: '12px',
      4: '16px',
      5: '20px',
      6: '24px',
      8: '32px',
      10: '40px',
      12: '48px'
    },
    radius: {
      sm: '4px',
      base: '6px',
      md: '8px',
      lg: '12px',
      xl: '16px'
    }
  },
  // icons.ts 中的图标经配置对象动态绑定（:class），UnoCSS 静态扫描无法提取，需在此 safelist 强制生成
  // 数据源：src/components/layout/icons.ts（SIDEBAR_ICONS/HEADER_ICONS/PROFILE_ICONS）+ auth 组件动态图标
  // 新增图标时同步追加（遵循规约-样式与UI U2 铁律：动态绑定必须加 safelist）
  safelist: [
    // SIDEBAR_ICONS（M1-A）
    'i-ep-magic-stick',
    'i-ep-chat-dot-round',
    'i-ep-data-line',
    'i-ep-menu',
    'i-ep-trend-charts',
    'i-ep-data-analysis',
    'i-ep-key',
    'i-ep-document',
    'i-ep-finished',
    'i-ep-wallet',
    'i-ep-user',
    'i-ep-connection',
    'i-ep-box',
    'i-ep-user-filled',
    'i-ep-ticket',
    'i-ep-credit-card',
    'i-ep-setting',
    // HEADER_ICONS（M1-A + M1-B 修正 language）
    'i-ep-search',
    'i-ep-bell',
    'i-lucide-languages', // M1-B（M1-A-Judge-T2）：原 i-ep-languege 无效名，改 lucide
    'i-ep-moon',
    'i-ep-switch-button',
    'i-ep-close',
    'i-ep-arrow-left',
    'i-ep-arrow-right',
    // PROFILE_ICONS（M1-A）
    'i-ep-user',
    // AUTH 组件动态图标（M1-B OAuthProviders / UserAuthForm / OtpForm 等）
    'i-ep-chat-line-round',
    'i-ep-platform',
    'i-ep-promotion',
    'i-ep-loading',
    'i-ep-document-copy',
    'i-ep-check',
    // Playground AiChatPanel 操作按钮（F06）
    'i-ep-copy-document',
    'i-ep-delete',
    // Dashboard 扩展面板（PD-08 后续：ApiInfoPanel/UptimePanel）
    'i-ep-guide',
    'i-ep-aim',
    'i-ep-refresh',
    // 商业版 AuthBrandPanel 价值点图标（动态绑定，静态扫描无法提取）
    'i-ep-cpu',
    'i-ep-lock',
    // i-ep-data-line 已在 SIDEBAR_ICONS safelist，此处不重复
  ]
})
