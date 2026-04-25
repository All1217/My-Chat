<template>
    <div class="chat-component" :class="{ 'component-slide': !chatStore.isSidebarOpen }">
        <div class="chat-header">
            <div class="chat-header-up">
                <RouterLink :to="{ name: 'home' }" class="logo-box">
                    <img :src="logo" alt="" />
                    My Chat
                </RouterLink>
                <div class="arrow-box" @click="chatStore.closeSidebar" title="收起侧边栏">
                    <ArrowLeft style="width: 100%; height: 100%;" />
                </div>
            </div>
            <div class="chat-header-down">
                <div class="open-new-chat" @click="handleAddConversation">
                    <Plus style="width: 22px; height: 22px; margin-right: 5px;" />
                    <span>打开新对话</span>
                </div>
            </div>
        </div>
        <div class="chat-body">
            <ul>
                <li v-for="chat in chatStore.chatList" :key="chat.conversationId"
                    :class="chatStore.currentChatId === chat.conversationId ? 'chat-item chat-active' : 'chat-item'"
                    @click="chatStore.selectConversation(chat.conversationId)"
                    @mouseenter="curShowMore = chat.conversationId" @mouseleave="curShowMore = ''">
                    <span>{{ chat.title || chat.conversationId }}</span>
                    <el-dropdown trigger="click">
                        <div class="chat-item-more"
                            v-show="chatStore.currentChatId === chat.conversationId || curShowMore === chat.conversationId">
                            <More style="width: 15px; height: 15px;" />
                        </div>
                        <template #dropdown>
                            <el-dropdown-menu>
                                <el-dropdown-item @click="showRenameDialog = true">重命名</el-dropdown-item>
                                <el-dropdown-item @click="showDeleteDialog = true">删除</el-dropdown-item>
                            </el-dropdown-menu>
                        </template>
                    </el-dropdown>

                </li>
            </ul>
        </div>
        <div class="chat-footer">
            <div class="avatar-box">
                <User style="width: 20px; height: 20px;" />
            </div>
            <span class="user-name">12345678912</span>
        </div>

        <el-dialog v-model="showRenameDialog" title="重命名" width="400"
            @open="() => { newName = chatStore.currentTitle || '' }">
            <el-input v-model="newName" style="width: 100%" maxlength="30" show-word-limit />
            <template #footer>
                <div class="dialog-footer">
                    <el-button @click="showRenameDialog = false">取消</el-button>
                    <el-button type="primary" @click="handleConfirmRename">确定</el-button>
                </div>
            </template>
        </el-dialog>

        <el-dialog v-model="showDeleteDialog" title="是否确认删除该会话？" width="300">
            这将彻底清除该会话的所有聊天记录！
            <template #footer>
                <div class="dialog-footer">
                    <el-button @click="showDeleteDialog = false">取消</el-button>
                    <el-button type="danger" @click="handleDelete">确定</el-button>
                </div>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useChatStore } from '@/stores/chat'
import { generateChatId } from '@/utils/streamChat'
import logo from '@/assets/my-chat-logo.png'
import { ElMessage } from "element-plus";

const chatStore = useChatStore()
const curShowMore = ref<string>('')

function handleAddConversation() {
    const id = generateChatId()
    chatStore.createConversation(id)
}

// 重命名
const showRenameDialog = ref<boolean>(false);
const newName = ref<string>('');
function handleConfirmRename() {
    if (newName.value.trim() === '') {
        ElMessage.error('名称不能为空');
        return;
    }
    chatStore.updateConversation({ conversationId: chatStore.currentChatId, title: newName.value });
    showRenameDialog.value = false;
}

// 删除会话
const showDeleteDialog = ref<boolean>(false);
function handleDelete() {
    chatStore.deleteConversation(chatStore.currentChatId);
    showDeleteDialog.value = false;
}
</script>
<style scoped lang="less">
.chat-active {
    background-color: #e4ecfc !important;

    span {
        color: #3964fe !important;
    }
}

.component-slide {
    transform: translateX(-100%);
}

.chat-component {
    position: fixed;
    width: 300px;
    height: 100vh;
    background-color: #f9fafb;
    overflow: hidden;
    transition: transform .5s;
    z-index: 10;

    .chat-header {
        height: 120px;

        .chat-header-up {
            height: 60px;
            display: flex;
            align-items: center;
            justify-content: space-around;

            .arrow-box {
                width: 22px;
                height: 22px;
                cursor: pointer;
            }

            .logo-box {
                display: inline-block;
                height: 60px;
                color: #9d48ff;
                line-height: 60px;
                font-size: 20px;
                font-weight: bold;

                img {
                    width: 45px;
                    height: 45px;
                }
            }
        }

        .chat-header-down {
            display: flex;
            align-items: center;
            justify-content: center;
            height: 60px;

            .open-new-chat {
                display: flex;
                align-items: center;
                justify-content: center;
                width: 200px;
                height: 40px;
                background-color: #fff;
                border-radius: 20px;
                cursor: pointer;
                box-shadow: 2px 2px 5px rgba(0, 0, 0, 0.1);
                transition: all ease 0.5s;

                span {
                    font-size: 16px;
                }
            }

            .open-new-chat:hover {
                box-shadow: 2px 2px 10px rgba(0, 0, 0, 0.3);
            }
        }
    }

    .chat-body {
        height: 76vh;

        ul {
            padding-top: 10px;
            padding-bottom: 10px;
            padding-left: 10px;
            max-height: 76vh;
            overflow: auto;

            .chat-item {
                position: relative;
                padding-left: 10px;
                padding-right: 10px;
                width: calc(100% - 10px);
                max-width: calc(100% - 10px);
                height: 36px;
                margin-bottom: 10px;
                border-radius: 8px;
                cursor: pointer;

                span {
                    display: inline-block;
                    width: calc(100% - 25px);
                    max-width: calc(100% - 25px);
                    font-size: 15px;
                    line-height: 36px;
                    color: #000;
                    white-space: nowrap; // 强制不换行
                    text-overflow: ellipsis; // 超出显示省略号
                    overflow: hidden;
                }

                .chat-item-more {
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    position: absolute;
                    right: -26px;
                    top: 5px;
                    height: 26px;
                    width: 26px;
                    border-radius: 50%;
                    background-color: #e1e1e1;
                }
            }

            .chat-item:hover {
                background-color: #eff0f1;
            }
        }
    }

    .chat-footer {
        display: flex;
        align-items: center;
        padding-left: 20px;
        height: calc(24vh - 120px);
        box-shadow: 3px 3px 13px rgba(0, 0, 0, 0.3);
        cursor: pointer;

        .avatar-box {
            display: flex;
            width: 32px;
            height: 32px;
            border-radius: 50%;
            background-color: #e4e4e4;
            align-items: center;
            justify-content: center;
        }

        .user-name {
            display: inline-block;
            padding-left: 10px;
            width: 200px;
            height: 32px;
            max-width: 200px;
            line-height: 32px;
            font-size: 15px;
        }
    }
}
</style>