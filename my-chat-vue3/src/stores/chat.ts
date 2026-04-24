import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ragHttp } from '@/utils/http'
import { ElMessage } from 'element-plus'
import type { ChatSessionVO } from '@/types/AiModule/types'
import type { ResultData } from '@/types/models.ts'

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
        try {
            const res = await ragHttp.get<ResultData<ChatSessionVO[]>>('/ai/history/getConversations')
            if (res.data.code === 200) {
                chatList.value = res.data.data
            } else {
                ElMessage.error(res.data.message || '获取会话列表失败！')
            }
        } catch (e) {
            console.error(e)
            ElMessage.error('获取会话列表失败！')
        }
    }

    /** 新建会话（持久化到后端并加入列表） */
    async function createConversation(id: string) {
        try {
            await ragHttp.post(`/ai/history/addConversation?conversationId=${id}`)
            chatList.value.push({ conversationId: id, title: '' })
            currentChatId.value = id
        } catch (e) {
            console.error(e)
            ElMessage.error('创建会话失败！')
        }
    }

    /** 新建会话（持久化到后端并加入列表） */
    async function updateConversation(id: string) {
        try {
            await ragHttp.post(`/ai/history/addConversation?conversationId=${id}`)
            chatList.value.push({ conversationId: id, title: '' })
            currentChatId.value = id
        } catch (e) {
            console.error(e)
            ElMessage.error('更新失败！')
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
    }
})