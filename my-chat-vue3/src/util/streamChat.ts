/**
 * 流式聊天工具
 * 用于处理与后端AI聊天接口的流式通信
 */

export interface ChatStreamOptions {
  prompt: string
  chatId: string
  files?: File[]
  onMessage: (chunk: string) => void
  onComplete: () => void
  onError: (error: Error) => void
}

/**
 * 发送流式聊天请求
 * @param options 聊天选项
 * @returns 取消请求的函数
 * @description: 为什么用原生fetch不用Axios？因为axios对SSE/流式响应支持有限，需要额外配置
 * 但是fetch原生支持流式读取，且听说内存效率更高
 */
export function streamChat(options: ChatStreamOptions): () => void {
  const { prompt, chatId, files, onMessage, onComplete, onError } = options
  
  // 创建FormData对象
  const formData = new FormData()
  formData.append('prompt', prompt)
  formData.append('chatId', chatId)
  
  // 如果有文件，添加到FormData
  if (files && files.length > 0) {
    files.forEach(file => {
      formData.append('files', file)
    })
  }
  
  // 创建AbortController用于取消请求
  const controller = new AbortController()
  
  // 发送请求，因为没用Axios，所以前头要加上/rag前缀。Axios会自动加。
  fetch('/rag/ai/normalChat/chat', {
    method: 'POST',
    body: formData,
    signal: controller.signal,
    headers: {
      // 注意：不要设置Content-Type，浏览器会自动设置multipart/form-data
    }
  })
    .then(async response => {
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      
      if (!response.body) {
        throw new Error('Response body is null')
      }
      
      // 使用TextDecoder处理流式响应
      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      
      try {
        while (true) {
          const { done, value } = await reader.read()
          
          if (done) {
            onComplete()
            break
          }
          
          // 解码并处理数据
          const chunk = decoder.decode(value, { stream: true })
          onMessage(chunk)
        }
      } catch (error) {
        onError(error instanceof Error ? error : new Error('Stream reading error'))
      }
    })
    .catch(error => {
      // 如果是取消请求，不触发错误回调
      if (error.name === 'AbortError') {
        return
      }
      onError(error instanceof Error ? error : new Error('Request error'))
    })
  
  // 返回取消函数
  return () => {
    controller.abort()
  }
}

/**
 * 生成ChatID
 * @returns ChatID
 */
export function generateChatId(): string {
    return `chat_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
}