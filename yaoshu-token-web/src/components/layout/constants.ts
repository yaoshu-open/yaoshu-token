
export const MOBILE_DRAWER_ANIMATION = {
  overlay: {
    hidden: { opacity: 0 },
    visible: { opacity: 1 },
    exit: { opacity: 0 }
  },
  drawer: {
    hidden: { opacity: 0, y: 100 },
    visible: {
      opacity: 1,
      y: 0,
      rotate: 0,
      transition: {
        type: 'spring',
        damping: 15,
        stiffness: 200,
        staggerChildren: 0.03
      }
    },
    exit: {
      opacity: 0,
      y: 100,
      transition: { duration: 0.1 }
    }
  },
  menuItem: {
    hidden: { opacity: 0 },
    visible: { opacity: 1 }
  }
} as const

export const MOBILE_DRAWER_CONFIG = {
  overlayTransitionDuration: 0.2,
  drawerClassName:
    'fixed inset-x-0 bottom-3 z-50 mx-auto w-[95%] rounded-xl border border-el-border-color bg-el-bg-color p-4 shadow-lg md:hidden',
  overlayClassName: 'fixed inset-0 z-40 bg-black/50 backdrop-blur-sm'
} as const
export const ROOT_VIEW_KEY = '__root'
export const THEME_STORAGE_KEY = 'yaoshu_theme'

// 主题模式枚举值（与 store/modules/theme.ts ThemeMode 一致）
export const THEME_MODES = ['light', 'dark', 'system'] as const
export const SIDEBAR_VARIANTS = ['sidebar', 'floating', 'inset'] as const
export const SIDEBAR_COLLAPSIBLES = ['icon', 'offcanvas', 'none'] as const

// 侧边栏宽度（px）：展开/折叠态，与 SCSS 变量对齐
export const SIDEBAR_WIDTH_EXPANDED = 200
export const SIDEBAR_WIDTH_COLLAPSED = 64
export const STORAGE_MAX_AGE = 60 * 60 * 24 * 365
export const AUTH_PROMPT_SECONDS = 5
