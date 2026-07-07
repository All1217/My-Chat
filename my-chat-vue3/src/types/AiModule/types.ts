export interface ChatSessionVO {
    title: string
    conversationId: string
    kbId?: string
}
export interface ChatSessionDTO {
    title?: string
    userId?: number
    conversationId: string
    kbId?: string
}
export interface Message {
    messageType: string
    text: string
    thinking?: string | null
}
