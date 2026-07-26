<template>
    <div class="message">
        <div class="message-list-wrap" ref="messageListRef">
            <div class="default-advice" v-if="messages == null || messages.length == 0">
                <h1>在下方聊天框输入您想问的问题……</h1>
            </div>
            <ul ref="chatBoxulRef">
                <li :class="msg.messageType === MessageType.ASSISTANT ? 'message-ai' : 'message-user'"
                    v-for="msg in visibleMessages">
                    <p class="message-content" v-if="msg.messageType === MessageType.USER">{{ msg.text }}</p>
                    <div class="message-content" v-else>
                        <AgentActivityTimeline :parts="msg.parts" />
                        <div v-if="msg.thinking" class="thinking-box">
                            <el-collapse>
                                <el-collapse-item title="思考过程">
                                    <MarkdownRenderer :content="msg.thinking" />
                                </el-collapse-item>
                            </el-collapse>
                        </div>
                        <MarkdownRenderer v-if="msg.text" :content="msg.text" />
                    </div>
                    <div class="tool">
                        <div class="tool-item" title="复制文本" @click="copyText(msg.text)">
                            <CopyDocument style="width: 20px; height: 20px;" />
                        </div>
                    </div>
                </li>
                <!-- 正在输入的AI消息 -->
                <li class="message-ai" v-if="isStreaming">
                    <div class="message-content">
                        <AgentActivityTimeline :parts="streamingParts" />
                        <div v-if="streamingThinking" class="thinking-box">
                            <el-collapse>
                                <el-collapse-item title="思考过程（实时更新中...）">
                                    <MarkdownRenderer :content="streamingThinking" />
                                </el-collapse-item>
                            </el-collapse>
                        </div>
                        <MarkdownRenderer v-if="streamingContent" :content="streamingContent" />
                    </div>
                    <div class="tool">
                        <div class="tool-item" title="停止生成" @click="stopStreaming">
                            <Close style="width: 20px; height: 20px;" />
                        </div>
                    </div>
                </li>
            </ul>
            <!-- 脱标悬浮：已选文件面板 -->
            <div v-if="showFilePanel && selectedFiles.length" class="file-panel">
                <div class="file-panel-header">
                    <span class="file-panel-title">待发送文件</span>
                    <el-button link type="info" size="small" :icon="Close" @click="showFilePanel = false" />
                </div>
                <div class="file-panel-body">
                    <div v-for="(f, i) in selectedFiles" :key="i" class="file-panel-item">
                        <span class="file-icon">{{ fileIcon(f) }}</span>
                        <span class="file-name">{{ f.name }}</span>
                        <span class="file-size">{{ formatFileSize(f.size) }}</span>
                        <el-button link type="danger" size="small" :icon="Close" @click="selectedFiles.splice(i, 1)" />
                    </div>
                </div>
            </div>
            <div class="float-buttons" v-if="(messages && messages.length > 0) || isStreaming || selectedFiles.length">
                <el-tooltip v-if="selectedFiles.length" content="查看待发送文件" placement="left">
                    <div class="float-btn file-toggle-btn" :class="{ active: showFilePanel }"
                        @click="showFilePanel = !showFilePanel">
                        <UploadFilled style="width: 14px; height: 14px;" />
                        <span class="file-badge">{{ selectedFiles.length }}</span>
                    </div>
                </el-tooltip>
                <div class="jump-btn" @click="isAtBottom ? jumpToTop() : jumpToBottom()">
                    <ArrowUpBold v-show="isAtBottom" style="width: 16px; height: 16px; margin-top: 6px;" />
                    <ArrowDownBold v-show="!isAtBottom" style="width: 16px; height: 16px; margin-top: 6px;" />
                </div>
            </div>
        </div>
        <div class="chat-box">
            <textarea v-model="inputText" placeholder="输入你的问题……" @keydown.enter.exact.prevent="sendMessage"
                :disabled="isStreaming"></textarea>

            <!-- 隐藏的文件选择 input -->
            <input ref="fileInputRef" type="file" multiple style="display:none" accept="image/*,.pdf,.txt,.md"
                @change="handleFileSelect" />

            <button class="chat-box-btn send-btn" @click="sendMessage" :disabled="isStreaming || !inputText.trim()">
                <Promotion style="width: 17px; height: 17px; color: #fff;" />
            </button>
            <button class="chat-box-btn upload-btn" @click="triggerFilePicker">
                <Plus style="width: 17px; height: 17px; color: #fff;" />
            </button>
            <div class="option-bar">
                <!-- 当前工作目录标签 -->
                <el-tag v-if="chatStore.currentWorkspace" type="warning" size="small" style="margin-left: 8px;">
                    📁 当前目录：{{ chatStore.currentWorkspace }}
                </el-tag>
                <el-tooltip v-if="chatStore.currentChat?.kbId" content="查看/切换知识库" placement="top">
                    <el-tag type="primary" size="small" style="cursor: pointer; margin-left: 8px;"
                        @click="goToKnowledgeStore">
                        📚 当前知识库：{{ chatStore.getKbDisplayName(chatStore.currentChat?.kbId) }}
                    </el-tag>
                </el-tooltip>
            </div>
        </div>
    </div>
</template>
<script setup lang="ts">
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import AgentActivityTimeline from '@/components/AgentActivityTimeline.vue'
import { MessageType } from '@/types/AiModule/enums'
import { watch, nextTick } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Close, UploadFilled } from '@element-plus/icons-vue'
import { useChatScroll } from '@/composables/useChatScroll'
import { useFileUpload } from '@/composables/useFileUpload'
import { useChatStream } from '@/composables/useChatStream'

const chatStore = useChatStore()
const router = useRouter()

const emit = defineEmits<{ (e: 'scroll-changed'): void }>()

// ---------- composable: 滚动 ----------
const {
    chatBoxulRef,
    isAtBottom,
    scrollToBottom,
    jumpToTop,
    jumpToBottom,
    updateIsAtBottom,
    connectStreaming,
} = useChatScroll(() => emit('scroll-changed'))

// ---------- composable: 文件上传 ----------
const {
    fileInputRef,
    selectedFiles,
    showFilePanel,
    triggerFilePicker,
    handleFileSelect,
    fileIcon,
    formatFileSize,
    consumeFiles,
} = useFileUpload()

// ---------- composable: 流式聊天 ----------
const {
    messages,
    visibleMessages,
    inputText,
    isStreaming,
    streamingContent,
    streamingThinking,
    streamingParts,
    sendMessage,
    stopStreaming,
} = useChatStream(scrollToBottom, consumeFiles, formatFileSize)

// 流式状态联动滚动
connectStreaming(isStreaming)

// 消息变化时更新底部位置
watch(messages, () => nextTick(() => updateIsAtBottom()))

// 暴露 ulRef 供父组件（ChatView）自定义滑条
defineExpose({ chatBoxulRef })

function goToKnowledgeStore() {
    router.push({ name: 'store' })
}

function copyText(text: string) {
    navigator.clipboard.writeText(text).then(() => {
        ElMessage.success('复制成功')
    }).catch(() => {
        ElMessage.error('复制失败')
    })
}
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
        position: relative;
        padding-top: 50px;
        height: 100%;

        /* 脱标悬浮按钮组 */
        .float-buttons {
            position: absolute;
            right: 0;
            bottom: 10px;
            display: flex;
            flex-direction: row;
            align-items: center;
            justify-content: right;
            gap: 6px;

            .jump-btn {
                width: 28px;
                height: 28px;
                background-color: #fff;
                border-radius: 50%;
                text-align: center;
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
                cursor: pointer;
                transition: all 0.2s;

                &:hover {
                    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
                    transform: scale(1.05);
                }
            }

            .float-btn {
                width: 28px;
                height: 28px;
                border-radius: 50%;
                background: #fff;
                display: flex;
                align-items: center;
                justify-content: center;
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
                cursor: pointer;
                transition: all 0.2s;
                position: relative;

                &:hover {
                    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
                    transform: scale(1.05);
                }

                &.active {
                    background: #437dff;
                    color: #fff;
                }
            }
        }

        .file-badge {
            position: absolute;
            top: -4px;
            right: -4px;
            min-width: 14px;
            height: 14px;
            padding: 0 3px;
            font-size: 10px;
            line-height: 14px;
            text-align: center;
            border-radius: 7px;
            background: #f56c6c;
            color: #fff;
        }

        /* 脱标悬浮文件面板 */
        .file-panel {
            position: absolute;
            right: 0;
            bottom: 10px;
            width: 100%;
            max-height: 200px;
            background: rgba(255, 255, 255, 0.95);
            backdrop-filter: blur(8px);
            border-radius: 10px;
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
            border: 1px solid rgba(0, 0, 0, 0.06);
            display: flex;
            flex-direction: column;
            z-index: 15;
            overflow: hidden;
        }

        .file-panel-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 8px 12px;
            border-bottom: 1px solid #f0f0f0;

            .file-panel-title {
                font-size: 12px;
                font-weight: 600;
                color: #303133;
            }
        }

        .file-panel-body {
            flex: 1;
            overflow-y: auto;
            padding: 4px 0;
        }

        .file-panel-item {
            display: flex;
            align-items: center;
            gap: 6px;
            padding: 5px 12px;
            font-size: 12px;
            transition: background 0.15s;

            &:hover {
                background: #f5f7fa;
            }

            .file-icon {
                font-size: 14px;
                flex-shrink: 0;
            }

            .file-name {
                flex: 1;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
                color: #303133;
            }

            .file-size {
                flex-shrink: 0;
                color: #909399;
            }
        }

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
                    white-space: pre-wrap;
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

                    .thinking-box {
                        margin-bottom: 8px;
                    }

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
            height: 115px;
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

        .chat-box-btn {
            position: absolute;
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

        .send-btn {
            right: 20px;
            bottom: 37px;
        }

        .upload-btn {
            right: 60px;
            bottom: 37px;
        }

        .option-bar {
            display: flex;
            align-items: center;
            height: 35px;
            width: 100%;
            background-color: #eeeeee;
        }
    }
}
</style>