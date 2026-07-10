/**
 * 环境配置
 * 根据不同的环境使用不同的API地址
 */

// 开发环境配置
const development = {
  apiBaseUrl: '/api',
  appTitle: 'My Chat App (Development)',
  debug: true
}

// 生产环境配置
const production = {
  apiBaseUrl: 'http://localhost:8080', // 生产环境需要完整的URL
  appTitle: 'My Chat App',
  debug: false
}

// 根据当前环境选择配置
const env = import.meta.env.MODE === 'production' ? production : development

export default env