import { ragClient } from '@/utils/http'
import type { ChatSessionVO, ChatSessionDTO, Message } from '@/types/AiModule/types'

export const chatApi = {
  /** 获取会话列表（可选按 kbId 过滤） */
  getConversations: (kbId?: string) =>
    ragClient.get<ChatSessionVO[]>('/ai/history/getConversations', {
      params: kbId ? { kbId } : undefined,
    }),

  addConversation: (conversationId: string, kbId?: string) =>
    ragClient.post<void>('/ai/history/addConversation', null, {
      params: kbId ? { conversationId, kbId } : { conversationId },
    }),

  updateConversation: (dto: ChatSessionDTO) =>
    ragClient.post<void>('/ai/history/update', dto),

  deleteConversation: (id: string) =>
    ragClient.delete<void>('/ai/history/deleteById', { params: { id } }),

  getMessages: (conversationId: string) =>
    ragClient.get<Message[]>(`/ai/history/getMessages/${conversationId}`),
}
