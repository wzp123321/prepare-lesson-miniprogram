import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 后端本地端口（与 application.yml 的 SERVER_PORT 默认 18899 一致）
// 本地开发用 vite 代理把 /api 转发到后端，免去跨域。
// 生产构建若由后端托管或独立部署到同域，则无需代理（后端直接提供 /api）。
const BACKEND_TARGET = 'http://47.116.35.76:18899'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: BACKEND_TARGET, // 改这里即可切换后端地址
        changeOrigin: true,
        // 若后端网关已带 /api 前缀则无需 rewrite；此处保持透传
      }
    }
  }
})
