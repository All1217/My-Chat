import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { chatApi } from '@/api/chat'
import type { ChatSessionVO, ChatSessionDTO } from '@/types/AiModule/types'
import { ElMessage } from 'element-plus'

export const useChatStore = defineStore('chat', () => {
    // ========== 状态 ==========
    const chatList = ref<ChatSessionVO[]>([])
    const currentChatId = ref<string | null>(null)
    const isSidebarOpen = ref<boolean>(true)

    // ========== 计算属性 ==========
    const currentChat = computed<ChatSessionVO | null>(() => {
        if (!currentChatId.value) return null
        return chatList.value.find(c => c.conversationId === currentChatId.value) ?? null
    })

    const currentTitle = computed(() => {
        if (!currentChat.value) return ''
        return currentChat.value.title || currentChat.value.conversationId || ''
    })

    // ========== 方法 ==========
    /** 获取会话列表 */
    async function fetchChatList(kbId?: string) {
        try {
            chatList.value = await chatApi.getConversations(kbId)
        } catch { /* 已 toast */ }
    }
    /** 新建会话（持久化到后端并加入列表） */
    async function createConversation(id: string, kbId?: string) {
        try {
            await chatApi.addConversation(id, kbId)
            chatList.value.push({ conversationId: id, title: '', kbId })
            currentChatId.value = id
        } catch { /* 已 toast */ }
    }
    /** 更新会话 */
    async function updateConversation(dto: ChatSessionDTO) {
        try {
            await chatApi.updateConversation(dto)
            const index = chatList.value.findIndex(c => c.conversationId === dto.conversationId)
            if (index !== -1 && dto.title !== undefined) {
                chatList.value[index] = { ...chatList.value[index], title: dto.title }
            }
            ElMessage.success('更新成功！')
        } catch { /* 已 toast */ }
    }
    /** 删除会话 */
    async function deleteConversation(id: string) {
        try {
            await chatApi.deleteConversation(id)
            const index = chatList.value.findIndex(c => c.conversationId === id)
            if (index !== -1) {
                chatList.value.splice(index, 1)
                currentChatId.value = ''
                ElMessage.success('删除成功！')
            }
        } catch { /* 已 toast */ }
    }

    /** 切换当前会话 */
    function selectConversation(id: string) {
        currentChatId.value = id
    }

    /** 侧边栏控制 */
    function openSidebar() { isSidebarOpen.value = true }
    function closeSidebar() { isSidebarOpen.value = false }
    function toggleSidebar() { isSidebarOpen.value = !isSidebarOpen.value }

    return {
        chatList,
        currentChatId,
        isSidebarOpen,
        currentChat,
        currentTitle,
        fetchChatList,
        createConversation,
        selectConversation,
        openSidebar,
        closeSidebar,
        toggleSidebar,
        updateConversation,
        deleteConversation
    }
})