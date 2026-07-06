import { ragClient } from '@/utils/http'
import type { ChatSessionVO, ChatSessionDTO, Message } from '@/types/AiModule/types'

export const chatApi = {
  getConversations: () =>
    ragClient.get<ChatSessionVO[]>('/ai/history/getConversations'),

  addConversation: (conversationId: string) =>
    ragClient.post<void>('/ai/history/addConversation', null, {
      params: { conversationId },
    }),

  updateConversation: (dto: ChatSessionDTO) =>
    ragClient.post<void>('/ai/history/update', dto),

  deleteConversation: (id: string) =>
    ragClient.delete<void>('/ai/history/deleteById', { params: { id } }),

  getMessages: (conversationId: string) =>
    ragClient.get<Message[]>(`/ai/history/getMessages/${conversationId}`),
}
