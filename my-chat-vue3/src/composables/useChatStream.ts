import { ref, computed, watch } from 'vue'
import { streamChat, generateChatId } from '@/utils/streamChat'
import { useChatStore } from '@/stores/chat'
import { chatApi } from '@/api/chat'
import { ElMessage } from 'element-plus'
import { MessageType } from '@/types/AiModule/enums'
import type { Message } from '@/types/AiModule/types'
import type { ChatStreamEvent, MessagePart } from '@/types/AiModule/streamEvents'
import { applyTimelineEvent, normalizeTimelineParts } from '@/utils/agentTimelineReducer'

/**
 * 聊天消息流式处理 — 发送 / 接收 / 历史加载 / NDJSON 事件归约
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
    /** 助手正文（text_delta 或 RAG 纯文本） */
    const streamingContent = ref('')
    /** 思考过程（thinking_delta） */
    const streamingThinking = ref('')
    /** 本轮工具时间线 parts */
    const streamingParts = ref<MessagePart[]>([])

    const visibleMessages = computed(() =>
        messages.value.filter(m => m.messageType !== 'system'),
    )

    let stopStreamingFn: (() => void) | null = null
    let isLocalNewChat = false

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

    async function sendMessage() {
        const text = inputText.value.trim()
        if (!text || isStreaming.value) return

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

        let chatId = chatStore.currentChatId
        if (!chatId) {
            chatId = generateChatId()
            isLocalNewChat = true
            await chatStore.createConversation(chatId, chatStore.kbId ?? undefined)
        }

        startStreaming(text, chatId, filesToSend)
    }

    function resetStreamingState() {
        streamingContent.value = ''
        streamingThinking.value = ''
        streamingParts.value = []
        stopStreamingFn = null
        isStreaming.value = false
    }

    function commitAssistantMessage(extraSuffix = '') {
        const text = (streamingContent.value + extraSuffix).trim()
        const thinking = streamingThinking.value.trim() || null
        const parts = streamingParts.value.length > 0
            ? streamingParts.value.map(p => ({ ...p }))
            : undefined
        if (!text && !thinking && (!parts || parts.length === 0)) {
            return
        }
        messages.value.push({
            text: text || (parts?.length ? '（仅工具调用，无文本回复）' : ''),
            thinking,
            parts,
            messageType: MessageType.ASSISTANT,
        })
    }

    function applyStreamEvent(event: ChatStreamEvent) {
        switch (event.type) {
            case 'route':
            case 'step':
            case 'tool_call':
            case 'tool_result':
                applyTimelineEvent(streamingParts.value, event)
                break
            case 'thinking_delta':
                // Phase 3 不再把模型思考作为公开进度。
                break
            case 'text_delta':
                if (event.text) {
                    streamingContent.value += event.text
                }
                break
            case 'error':
                if (event.message) {
                    ElMessage.warning(event.message)
                    streamingContent.value +=
                        (streamingContent.value ? '\n' : '') + `（错误：${event.message}）`
                }
                for (const p of streamingParts.value) {
                    if ((p.type === 'tool' || p.type === 'step') && p.status === 'running') {
                        p.status = 'error'
                    }
                }
                break
            case 'done':
                break
            default:
                break
        }
        scrollToBottom()
    }

    function startStreaming(prompt: string, chatId: string, files?: File[]) {
        isStreaming.value = true
        streamingContent.value = ''
        streamingThinking.value = ''
        streamingParts.value = []

        const kbId = chatStore.kbId ?? undefined

        stopStreamingFn = streamChat({
            prompt,
            chatId,
            kbId,
            files,
            // 主聊天仅 Orchestrator + 写盘质量环
            qualityLoop: true,
            onEvent: applyStreamEvent,
            onComplete: () => {
                commitAssistantMessage()
                resetStreamingState()
                scrollToBottom()
            },
            onError: (error) => {
                if (error.message?.includes('不支持图片') || error.message?.includes('400')) {
                    ElMessage.warning('当前模型不支持图片分析，已提取文档文本内容')
                }
                if (streamingContent.value.trim() || streamingParts.value.length > 0) {
                    commitAssistantMessage()
                } else {
                    messages.value.push({
                        text: `抱歉，请求出错：${error.message}`,
                        messageType: MessageType.ASSISTANT,
                    })
                }
                resetStreamingState()
                scrollToBottom()
            },
        })
    }

    function stopStreaming() {
        if (stopStreamingFn) {
            stopStreamingFn()
            stopStreamingFn = null
        }
        for (const p of streamingParts.value) {
            if ((p.type === 'tool' || p.type === 'step') && p.status === 'running') {
                p.status = 'cancelled'
            }
        }
        if (
            streamingContent.value.trim()
            || streamingThinking.value.trim()
            || streamingParts.value.length > 0
        ) {
            commitAssistantMessage('\n\n*(用户中断了生成)*')
        }
        resetStreamingState()
        scrollToBottom()
    }

    async function getMessages(id: string) {
        try {
            // 后端第 3 周起返回 ChatMessageVO（含 thinking / parts），字段与 Message 对齐
            const raw = await chatApi.getMessages(id)
            const split: Message[] = []
            for (const msg of raw) {
                if (msg.messageType === MessageType.USER && msg.text.includes('\n\n用户的问题：\n')) {
                    const idx = msg.text.indexOf('\n\n用户的问题：\n')
                    let filePart = msg.text.substring(0, idx)
                    const questionPart = msg.text.substring(idx + '\n\n用户的问题：\n'.length)
                    // 旧脏数据曾把「上传文档正文」写进 Memory：刷新后只保留文件名列表
                    const bodyMarker = '以下为上传文档正文'
                    const bodyIdx = filePart.indexOf(bodyMarker)
                    if (bodyIdx >= 0) {
                        filePart = filePart.substring(0, bodyIdx).trimEnd()
                    }
                    if (filePart.trim()) split.push({ text: filePart, messageType: MessageType.USER })
                    if (questionPart.trim()) split.push({ text: questionPart, messageType: MessageType.USER })
                } else {
                    // 原样保留 thinking / parts，供 AgentActivityTimeline 回放
                    split.push({
                        text: msg.text ?? '',
                        messageType: msg.messageType,
                        thinking: msg.thinking ?? null,
                        parts: msg.parts,
                    })
                }
            }
            messages.value = split.map(normalizeMessageParts)
            scrollToBottom(true)
        } catch { /* 已 toast */ }
    }

    /** 后端 MessagePartVO（route/step）→ 前端 MessagePart */
    function normalizeMessageParts(msg: Message): Message {
        return {
            ...msg,
            parts: normalizeTimelineParts(msg.parts),
        }
    }

    return {
        messages,
        visibleMessages,
        inputText,
        isStreaming,
        streamingContent,
        streamingThinking,
        streamingParts,
        sendMessage,
        stopStreaming,
    }
}
