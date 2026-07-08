// M0 已声明 title/requireAuth/roles，M1-A 追加 sidebarView 用于显式 drill-in 视图绑定
declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requireAuth?: boolean
    roles?: string[]
    // 显式指定 drill-in view id（覆盖路径正则匹配，用于特殊页面强制切换侧边栏视图）
    sidebarView?: string
  }
}

export {}
