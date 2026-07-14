import { ref, computed, watch } from 'vue'
import { streamChat, generateChatId } from '@/utils/streamChat'
import { useChatStore } from '@/stores/chat'
import { chatApi } from '@/api/chat'
import { ElMessage } from 'element-plus'
import { MessageType } from '@/types/AiModule/enums'
import type { Message } from '@/types/AiModule/types'

/**
 * 聊天消息流式处理 — 发送 / 接收 / 历史加载 / 消息拆分
 *
 * @param scrollToBottom  滚动到底部的函数
 * @param consumeFiles    取出并清空待发送文件的函数
 * @param formatFileSize  文件大小格式化函数（来自 useFileUpload）
 */
export function useChatStream(
    scrollToBottom: (force?: boolean) => void,
    consumeFiles: () => File[],
    formatFileSize: (bytes: number) => string,
) {
    const chatStore = useChatStore()

    const messages = ref<Message[]>([])
    const inputText = ref('')
    const isStreaming = ref(false)
    const streamingContent = ref('')

    /** 过滤 system 消息（文档全文），仅展示 USER / ASSISTANT */
    const visibleMessages = computed(() =>
        messages.value.filter(m => m.messageType !== 'system'),
    )

    let stopStreamingFn: (() => void) | null = null
    let isLocalNewChat = false

    // 监听会话切换 → 加载历史
    watch(
        () => chatStore.currentChatId,
        (newId) => {
            if (newId) {
                if (isLocalNewChat) {
                    isLocalNewChat = false
                    return
                }
                getMessages(newId)
            } else {
                messages.value = []
            }
        },
        { immediate: true },
    )

    // 发送消息
    async function sendMessage() {
        const text = inputText.value.trim()
        if (!text || isStreaming.value) return

        // 若有文件，推文件信息消息（仅 UI 展示）
        const filesToSend = consumeFiles()
        if (filesToSend.length > 0) {
            const fileInfo = filesToSend
                .map(f => `- ${f.name}（${formatFileSize(f.size)}）`)
                .join('\n')
            messages.value.push({
                text: `上传了以下文件：\n${fileInfo}`,
                messageType: MessageType.USER,
            })
        }
        messages.value.push({ text, messageType: MessageType.USER })
        inputText.value = ''
        scrollToBottom(true)

        // 确保会话存在
        let chatId = chatStore.currentChatId
        if (!chatId) {
            chatId = generateChatId()
            isLocalNewChat = true
            await chatStore.createConversation(chatId, chatStore.kbId ?? undefined)
        }

        startStreaming(text, chatId, filesToSend)
    }

    // 开始流式响应
    function startStreaming(prompt: string, chatId: string, files?: File[]) {
        isStreaming.value = true
        streamingContent.value = ''

        stopStreamingFn = streamChat({
            prompt,
            chatId,
            kbId: chatStore.kbId ?? undefined,
            files,
            onMessage: (chunk) => {
                streamingContent.value += chunk
                scrollToBottom()
            },
            onComplete: () => {
                messages.value.push({
                    text: streamingContent.value,
                    messageType: MessageType.ASSISTANT,
                })
                isStreaming.value = false
                streamingContent.value = ''
                stopStreamingFn = null
                scrollToBottom()
            },
            onError: (error) => {
                if (error.message?.includes('不支持图片') || error.message?.includes('400')) {
                    ElMessage.warning('当前模型不支持图片分析，已提取文档文本内容')
                }
                messages.value.push({
                    text: `抱歉，请求出错：${error.message}`,
                    messageType: MessageType.ASSISTANT,
                })
                isStreaming.value = false
                streamingContent.value = ''
                stopStreamingFn = null
                scrollToBottom()
            },
        })
    }

    // 停止流式
    function stopStreaming() {
        if (stopStreamingFn) {
            stopStreamingFn()
            stopStreamingFn = null
        }
        if (streamingContent.value.trim()) {
            messages.value.push({
                text: streamingContent.value + '\n\n*(用户中断了生成)*',
                messageType: MessageType.ASSISTANT,
            })
        }
        isStreaming.value = false
        streamingContent.value = ''
        scrollToBottom()
    }

    // 获取历史消息（含文件消息拆分）
    async function getMessages(id: string) {
        try {
            const raw = await chatApi.getMessages(id)
            const split: Message[] = []
            for (const msg of raw) {
                if (msg.messageType === MessageType.USER && msg.text.includes('\n\n用户的问题：\n')) {
                    const idx = msg.text.indexOf('\n\n用户的问题：\n')
                    const filePart = msg.text.substring(0, idx)
                    const questionPart = msg.text.substring(idx + '\n\n用户的问题：\n'.length)
                    if (filePart.trim()) split.push({ text: filePart, messageType: MessageType.USER })
                    if (questionPart.trim()) split.push({ text: questionPart, messageType: MessageType.USER })
                } else {
                    split.push(msg)
                }
            }
            messages.value = split
            scrollToBottom(true)
        } catch { /* 已 toast */ }
    }

    return {
        messages,
        visibleMessages,
        inputText,
        isStreaming,
        streamingContent,
        sendMessage,
        stopStreaming,
    }
}