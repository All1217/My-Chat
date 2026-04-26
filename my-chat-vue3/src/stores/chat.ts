import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ragHttp } from '@/utils/http'
import type { ChatSessionVO, ChatSessionDTO } from '@/types/AiModule/types'
import { request, mutate } from '@/utils/request'
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
    async function fetchChatList() {
        const list = await request(
            () => ragHttp.get<ChatSessionVO[]>('/ai/history/getConversations'),
            { errorMsg: '获取会话列表失败' }
        )
        if (list) {
            chatList.value = list
        }
    }
    /** 新建会话（持久化到后端并加入列表） */
    async function createConversation(id: string) {
        try {
            await ragHttp.post(`/ai/history/addConversation?conversationId=${id}`)
            // 能走到这里说明 HTTP 200，后端已写库
            chatList.value.push({ conversationId: id, title: '' })
            currentChatId.value = id
        } catch {
            ElMessage.error('创建会话失败！')
        }
    }
    /** 更新会话 */
    async function updateConversation(dto: ChatSessionDTO) {
        const ok = await mutate(
            () => ragHttp.post('/ai/history/update', dto),
            '更新失败！'
        )
        if (ok) {
            const index = chatList.value.findIndex(c => c.conversationId === dto.conversationId)
            if (index !== -1 && dto.title !== undefined) {
                // 替换整个对象，确保响应式触发
                chatList.value[index] = { ...chatList.value[index], title: dto.title }
            }
            ElMessage.success('更新成功！')
        }
    }
    /** 删除会话 */
    async function deleteConversation(id: string) {
        // 删除接口没有返回值，所以直接调用 ragHttp.delete
        ragHttp.delete(`/ai/history/deleteById?id=${id}`);
        const index = chatList.value.findIndex(c => c.conversationId === id)
        if (index !== -1) {
            chatList.value.splice(index, 1);
            currentChatId.value = '';
            ElMessage.success('删除成功！');
        }
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