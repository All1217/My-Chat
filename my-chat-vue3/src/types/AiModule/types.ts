import type { MessagePart } from './streamEvents'

export interface ChatSessionVO {
    title: string
    conversationId: string
    kbId?: string
    workDir?: string
}
export interface ChatSessionDTO {
    title?: string
    userId?: number
    conversationId: string
    kbId?: string
    workDir?: string
}

export interface Message {
    messageType: string
    text: string
    thinking?: string | null
    /** 本轮可观测片段（工具时间线）；第 3 周起历史接口可回放 */
    parts?: MessagePart[]
}

export type { MessagePart }
