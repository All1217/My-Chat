import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  server: {
    proxy: {
      // 代理所有以 /api 开头的请求到后端服务
      '/api': {
        target: 'http://localhost:8080/jeecg-boot',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      // 备用
      '/backend': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/backend/, '')
      }
    }
  }
})
