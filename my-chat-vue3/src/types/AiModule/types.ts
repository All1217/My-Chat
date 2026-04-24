export interface ChatSessionVO {
    title: string
    conversationId: string
}
export interface ChatSessionDTO {
    title?: string
    userId?: number
    conversationId: string
}
export interface Message {
    messageType: string
    text: string
}