import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import VxeUI from 'vxe-pc-ui'
import VxeTable from 'vxe-table'
import VXETablePluginElement from 'vxe-table-plugin-element'
import App from './App.vue'
import router from './router'
import { setupRouterGuards } from './router/guards'
import pinia from './store'
import { useThemeStore } from './store/modules/theme'
import i18n from './plugins/i18n'
import { setupSpiExtensions } from './plugins/spi'
import { getRegisteredRoutes } from './plugins/spi/registry'

// 品牌字体：Inter（西文）+ JetBrains Mono（等宽），自托管无外网依赖
import '@fontsource-variable/inter'
import '@fontsource-variable/jetbrains-mono'

// Element Plus 样式：全量引入确保 ElMessage 等指令式 API 样式就位
import 'element-plus/dist/index.css'
// Element Plus 暗色模式 CSS 变量：缺失会导致 html.dark 下 EP 组件仍为亮色，暗色模式完全失效
import 'element-plus/theme-chalk/dark/css-vars.css'
// vxe-table 样式
import 'vxe-pc-ui/lib/style.css'
import 'vxe-table/lib/style.css'
import 'uno.css'
import '@/styles/index.scss'

const app = createApp(App)

// 注册顺序不可调整——Element Plus 必须先于插件注册，否则插件拦截失效
app.use(ElementPlus)
app.use(VxeUI)
app.use(VxeTable)
VxeUI.use(VXETablePluginElement)

app.use(pinia)
app.use(router)
app.use(i18n)

// 路由守卫在 app.use(router) 后注册
setupRouterGuards(router)

// SPI 扩展点初始化（注册默认组件 + 调用扩展入口）
setupSpiExtensions(app)

// 动态注册 SPI 注入的路由（setupExtensions 在路由表创建后才执行，需补注册）
// /ee/* 路由包裹鉴权布局，其他路由作为顶级独立路由
const spiRoutes = getRegisteredRoutes()
const eeChildren = spiRoutes.filter((r) => r.path.startsWith('/ee/'))
if (eeChildren.length > 0) {
  router.addRoute({
    path: '/ee',
    component: () => import('@/layouts/default.vue'),
    meta: { requireAuth: true },
    children: eeChildren.map((r) => ({
      path: r.path.replace('/ee/', ''),
      component: r.component,
      meta: r.meta ?? { requireAuth: true }
    }))
  })
}
for (const r of spiRoutes.filter((r) => !r.path.startsWith('/ee/'))) {
  router.addRoute({
    path: r.path,
    component: r.component,
    meta: r.meta ?? { requireAuth: true }
  })
}

// 主动初始化主题 store（触发 applyThemeClass 等副作用，避免 /login 等公共页首屏主题闪烁，观察项 O4）
void useThemeStore()

app.mount('#app')

// 动态添加的路由需在初始导航后重导航一次，确保 SPI 注入的路由被正确匹配
// catch-all 404 路由会在动态路由之前匹配，replace 强制重新解析
router.isReady().then(() => {
  if (spiRoutes.length > 0) {
    router.replace(router.currentRoute.value.fullPath)
  }
})
