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
      // 针对公司后端CRM系统写的临时测试反向代理
      '/api': {
        target: 'http://localhost:8080/jeecg-boot',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      // 针对本项目的后端服务
      '/rag': {
        target: 'http://localhost:8100',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/rag/, '')
      }
    }
  }
})
