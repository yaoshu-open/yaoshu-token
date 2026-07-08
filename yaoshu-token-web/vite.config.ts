import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import Icons from 'unplugin-icons/vite'
import IconsResolver from 'unplugin-icons/resolver'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  // BACKEND_URL（服务端变量，不暴露到浏览器）控制 Vite proxy target
  // VITE_API_BASE 保留给前端代码用：设了=绝对URL（不走proxy），不设=空串（走相对路径经proxy转发）
  const apiBase = process.env.BACKEND_URL || env.VITE_API_BASE || 'http://localhost:9527'
  return {
    plugins: [
      vue(),
      UnoCSS(),
      AutoImport({
        imports: ['vue', 'vue-router', 'pinia'],
        resolvers: [ElementPlusResolver()],
        dts: 'src/types/auto-imports.d.ts',
        eslintrc: { enabled: true }
      }),
      Components({
        resolvers: [
          ElementPlusResolver(),
          IconsResolver({ prefix: 'i' })
        ],
        dts: 'src/types/components.d.ts'
      }),
      Icons({ autoInstall: true })
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      // 固定端口 5180 + strictPort：冲突时报错而非漂移（便于 kill 与联调定位）
      port: 5180,
      strictPort: true,
      proxy: {
        '/api': { target: apiBase, changeOrigin: true },
        '/v1': { target: apiBase, changeOrigin: true },
        '/pg': { target: apiBase, changeOrigin: true }
      }
    },
    build: {
      sourcemap: mode !== 'production'
    }
  }
})
