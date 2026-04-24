<template>
    <div class="message">
        <div class="message-list-wrap" ref="messageListRef">
            <div class="default-advice" v-if="messages == null || messages.length == 0">
                <h1>在下方聊天框输入您想问的问题……</h1>
            </div>
            <ul>
                <li :class="msg.messageType === MessageType.ASSISTANT ? 'message-ai' : 'message-user'"
                    v-for="msg in messages">
                    <p class="message-content" v-if="msg.messageType === MessageType.USER">{{ msg.text }}</p>
                    <div class="message-content" v-else>
                        <MarkdownRenderer :content="msg.text" />
                    </div>
                    <div class="tool">
                        <div class="tool-item" title="复制文本">
                            <CopyDocument style="width: 20px; height: 20px;" />
                        </div>
                    </div>
                </li>
                <!-- 正在输入的AI消息 -->
                <li class="message-ai" v-if="isStreaming">
                    <div class="message-content">
                        <MarkdownRenderer :content="streamingContent" />
                    </div>
                    <div class="tool">
                        <div class="tool-item" title="停止生成" @click="stopStreaming">
                            <Close style="width: 20px; height: 20px;" />
                        </div>
                    </div>
                </li>
            </ul>
        </div>
        <div class="chat-box">
            <textarea v-model="inputText" placeholder="输入你的问题……" @keydown.enter.exact.prevent="sendMessage"
                :disabled="isStreaming"></textarea>
            <button class="send-btn" @click="sendMessage" :disabled="isStreaming || !inputText.trim()">
                <Promotion style="width: 16px; height: 16px; color: #fff;" />
            </button>
        </div>
    </div>
</template>
<script setup lang="ts">
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { MessageType } from '@/types/AiModule/enums'
import type { Message } from '@/types/AiModule/types'
import { ref, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { streamChat, generateChatId } from '@/utils/streamChat'
import { useChatStore } from '@/stores/chat'
import { ragService } from '@/utils/http'
import { request } from '@/utils/request'


const chatStore = useChatStore()

const messages = ref<Message[]>([])
const inputText = ref('')
const isStreaming = ref(false)
const streamingContent = ref('')
const messageListRef = ref<HTMLElement>()
let stopStreamingFn: (() => void) | null = null
// 标记：当前消息是否由本组件本地发起的（不需要从后端拉历史）
let isLocalNewChat = false

// 监听 store 中 currentChatId 的变化来加载历史消息
watch(
    () => chatStore.currentChatId,
    (newId) => {
        if (newId) {
            if (isLocalNewChat) {
                isLocalNewChat = false
                // messages.value = []
                return
            }
            getMessages(newId)
        } else {
            messages.value = []
        }
    },
)

// 发送消息
async function sendMessage() {
    const text = inputText.value.trim()
    if (!text || isStreaming.value) return

    messages.value.push({ text, messageType: MessageType.USER })
    inputText.value = ''
    scrollToBottom()

    // 如果没有当前会话，先创建
    let chatId = chatStore.currentChatId
    if (!chatId) {
        chatId = generateChatId()
        isLocalNewChat = true
        await chatStore.createConversation(chatId)
        // createConversation 内部已设置 currentChatId，且 messages 已在上面 push
    }

    startStreaming(text, chatId)
}

// 开始流式响应
function startStreaming(prompt: string, chatId: string) {
    isStreaming.value = true
    streamingContent.value = ''

    stopStreamingFn = streamChat({
        prompt,
        chatId,
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
// 停止流式响应
function stopStreaming() {
    if (stopStreamingFn) {
        stopStreamingFn()
        stopStreamingFn = null
    }
    // 如果已经有内容，保存到消息列表
    if (streamingContent.value.trim()) {
        messages.value.push({
            text: streamingContent.value + '\n\n*(用户中断了生成)*',
            messageType: MessageType.ASSISTANT
        })
    }
    isStreaming.value = false
    streamingContent.value = ''
    scrollToBottom()
}

function scrollToBottom() {
    nextTick(() => {
        if (messageListRef.value) {
            const container = messageListRef.value.querySelector('ul')
            if (container) {
                container.scrollTop = container.scrollHeight
            }
        }
    })
}

// 获取会话聊天记录
async function getMessages(id: string) {
    const list = await request(
        () => ragService.get<Message[]>(`/ai/history/getMessages/${id}`),
        { errorMsg: '获取聊天记录失败！' }
    )
    if (list) {
        messages.value = list
    }
}

onMounted(() => {
    scrollToBottom()
})
// 组件卸载时停止流式请求
onUnmounted(() => {
    if (stopStreamingFn) {
        stopStreamingFn()
    }
})

</script>
<style scoped lang="less">
// 无序、有序列表每一行前面的小黑点或者序号没有正确显示
.message {
    position: relative;
    width: 50vw;
    height: 100vh;
    max-height: 100vh;
    padding-bottom: 165px;
    padding-top: 15px;

    .message-list-wrap {
        padding-top: 50px;
        height: 100%;

        .default-advice {
            padding: 60px 15px 15px 15px;

            h1 {
                text-align: center;
            }
        }

        ul {
            display: flex;
            flex-direction: column;
            height: 100%;
            overflow: auto;

            .message-user {
                align-self: flex-end;
                max-width: 85%;
                margin-left: auto;
                margin-bottom: 15px;

                .message-content {
                    padding: 10px;
                    font-size: 15px;
                    color: #000;
                    background-color: #edf3fe;
                    border-radius: 10px;
                }
            }

            .message-ai {
                align-self: flex-start;

                .message-content {
                    padding: 10px;
                    font-size: 15px;
                    color: #000;
                    border-radius: 10px;
                    /* 确保Markdown内容能正确显示 */
                    overflow: visible;

                    /* Markdown渲染器样式 */
                    .markdown-body {
                        background-color: transparent;
                        padding: 0;
                        margin: 0;

                        /* 确保表格能正确显示 */
                        table {
                            margin: 10px 0;
                            border: 1px solid #ddd !important;

                            th,
                            td {
                                border: 1px solid #ddd !important;
                                padding: 8px 12px !important;
                            }
                        }

                        /* 确保代码块能正确显示 */
                        pre {
                            margin: 10px 0 !important;
                            background-color: #f9fafb !important;
                        }

                        /* 确保列表能正确显示 */
                        ul,
                        ol {
                            padding-left: 2em !important;
                        }

                        ul {
                            list-style-type: disc !important;
                        }

                        ol {
                            list-style-type: decimal !important;
                        }

                        li {
                            display: list-item !important;
                        }
                    }
                }
            }

            .tool {
                display: flex;
                height: 23px;
                padding: 3px 0 0 10px;

                .tool-item {
                    height: 20px;
                    cursor: pointer;
                }
            }
        }

        ul::-webkit-scrollbar {
            display: none;
        }
    }

    .chat-box {
        position: absolute;
        left: 0;
        bottom: 15px;
        width: 100%;
        height: 150px;
        background-color: #ffffff;
        box-shadow: 2px 2px 8px rgba(0, 0, 0, 0.1);
        border-radius: 10px;
        overflow: hidden;
        z-index: 10;

        textarea {
            padding: 15px;
            width: 100%;
            height: 100%;
            border: none;
            resize: none;
            outline: none;
            font-size: 16px;

            &:disabled {
                background-color: #f5f5f5;
                cursor: not-allowed;
            }
        }

        textarea::placeholder {
            font-size: 16px;
        }

        .send-btn {
            position: absolute;
            right: 20px;
            bottom: 15px;
            width: 25px;
            height: 25px;
            border-radius: 50%;
            background-color: #437dff;
            border: none;
            cursor: pointer;

            &:disabled {
                background-color: #cccccc;
                cursor: not-allowed;
            }

            &:hover:not(:disabled) {
                background-color: #3366ff;
            }
        }
    }
}
</style>