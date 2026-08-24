import { fileURLToPath, URL } from 'node:url'

import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { configDefaults, defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需引入：组件与对应样式只在实际使用的页面 chunk 中出现，
    // Landing / Login 等初始路由不再承担全量 UI 库成本。
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  test: {
    exclude: [...configDefaults.exclude, 'e2e/**'],
    server: {
      deps: {
        // 组件测试会经按需引入加载 element-plus 子模块；交给 vite 转换以处理其中的 CSS 导入。
        inline: ['element-plus'],
      },
    },
  },
})
