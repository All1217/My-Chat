import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ragService } from '@/utils/http'
import type { ChatSessionVO, ChatSessionDTO } from '@/types/AiModule/types'
import { request, mutate } from '@/utils/request'

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
    async function fetchChatList() {
        const list = await request(
            () => ragService.get<ChatSessionVO[]>('/ai/history/getConversations')
        )
        if (list) {
            chatList.value = list.data
        }
    }
    /** 新建会话（持久化到后端并加入列表） */
    async function createConversation(id: string) {
        const ok = await mutate(
            () => ragService.post(`/ai/history/addConversation?conversationId=${id}`),
            '创建会话失败！'
        )
        if (ok) {
            chatList.value.push({ conversationId: id, title: '' })
            currentChatId.value = id
        }
    }
    /** 更新会话 */
    async function updateConversation(dto: ChatSessionDTO) {
        const ok = await mutate(
            () => ragService.post('/ai/history/update', dto),
            '更新失败！'
        )
        if (ok) {
            const target = chatList.value.find(c => c.conversationId === dto.conversationId)
            if (target && dto.title !== undefined) {
                target.title = dto.title
            }
        }
    }

    /** 切换当前会话 */
    function selectConversation(id: string) {
        currentChatId.value = id
    }

    /** 侧边栏控制 */
    function openSidebar() {
        isSidebarOpen.value = true
    }
    function closeSidebar() {
        isSidebarOpen.value = false
    }
    function toggleSidebar() {
        isSidebarOpen.value = !isSidebarOpen.value
    }

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
        updateConversation
    }
})