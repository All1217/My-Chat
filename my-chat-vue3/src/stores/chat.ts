import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { chatApi } from '@/api/chat'
import type { ChatSessionVO, ChatSessionDTO } from '@/types/AiModule/types'
import { ElMessage } from 'element-plus'

export const useChatStore = defineStore('chat', () => {
    const chatList = ref<ChatSessionVO[]>([])
    const currentChatId = ref<string | null>(null)
    const isSidebarOpen = ref<boolean>(true)
    /** 当前知识库模式：null=普通聊天，字符串=知识库ID */
    const kbId = ref<string | null>(null)
    /** 当前知识库名称（用于 ChatBox 标签显示） */
    const kbName = ref<string>('')
    /** KB ID → 名称映射缓存 */
    const kbNameMap = ref<Record<string, string>>({})
    /** 当前工作空间 */
    const currentWorkspace = ref<string>('')

    const currentChat = computed<ChatSessionVO | null>(() => {
        if (!currentChatId.value) return null
        return chatList.value.find(c => c.conversationId === currentChatId.value) ?? null
    })

    const currentTitle = computed(() => {
        if (!currentChat.value) return ''
        return currentChat.value.title || currentChat.value.conversationId || ''
    })

    /** 从 API 加载所有知识库名称到缓存 */
    async function loadKbNames() {
        try {
            const { knowledgeApi } = await import('@/api/knowledge')
            const list = await knowledgeApi.list()
            const map: Record<string, string> = {}
            for (const kb of list) {
                map[kb.id] = kb.name
            }
            kbNameMap.value = map
        } catch { /* 静默 */ }
    }

    /** 根据 kbId 获取显示名称（缓存命中→名称，否则回退到 ID） */
    function getKbDisplayName(id?: string | null): string {
        if (!id) return ''
        return kbNameMap.value[id] || id
    }

    /** 设置知识库模式（知识与名称同时设置） */
    function setKbContext(id: string | null, name?: string) {
        kbId.value = id
        kbName.value = name ?? ''
    }

    /** 设置当前工作空间 */
    function setWorkspace(path: string) {
        currentWorkspace.value = path
    }

    /** 获取会话列表（可选按 kbId 过滤） */
    async function fetchChatList(filterKbId?: string) {
        try {
            chatList.value = await chatApi.getConversations(filterKbId)
        } catch { /* 已 toast */ }
    }

    /** 新建会话（kbId 为显式传入，不自动使用 store 的 kbId 默认值） */
    async function createConversation(id: string, explicitKbId?: string, title?: string) {
        try {
            await chatApi.addConversation(id, explicitKbId)
            chatList.value.push({ conversationId: id, title: title || id, kbId: explicitKbId })
            currentChatId.value = id
            // 如果传入的标题与 id 不同，调用更新接口设置标题
            if (title && title !== id) {
                await updateConversation({ conversationId: id, title })
            }
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

    function selectConversation(id: string) {
        currentChatId.value = id
    }

    function openSidebar() { isSidebarOpen.value = true }
    function closeSidebar() { isSidebarOpen.value = false }
    function toggleSidebar() { isSidebarOpen.value = !isSidebarOpen.value }

    return {
        chatList,
        currentChatId,
        isSidebarOpen,
        kbId,
        kbName,
        kbNameMap,
        currentChat,
        currentTitle,
        setKbContext,
        loadKbNames,
        getKbDisplayName,
        fetchChatList,
        createConversation,
        selectConversation,
        openSidebar,
        closeSidebar,
        toggleSidebar,
        updateConversation,
        deleteConversation,
        currentWorkspace,
        setWorkspace,
    }
})
