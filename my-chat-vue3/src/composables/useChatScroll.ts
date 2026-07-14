import { ref, nextTick, onMounted, onUnmounted, type Ref } from 'vue'

/**
 * 聊天滚动控制 — 自动滚底 / 跳转按钮 / 滚动事件
 *
 * @param onScroll  每次滚动时的回调（用于父组件同步自定义滑条）
 */
export function useChatScroll(onScroll?: () => void) {
    const chatBoxulRef = ref<HTMLElement>()
    const autoScrollEnabled = ref(true)
    const isAtBottom = ref(false)
    const BOTTOM_THRESHOLD = 100

    // 流式状态引用（从 useChatStream 注入，用于控制自动滚动）
    let isStreamingRef: Ref<boolean> | null = null

    /** 连接流式状态 — 流式期间用户滚开底部则暂停自动滚动 */
    function connectStreaming(ref: Ref<boolean>) {
        isStreamingRef = ref
    }

    function updateIsAtBottom() {
        const ul = chatBoxulRef.value
        if (!ul) return
        isAtBottom.value = ul.scrollHeight - ul.scrollTop - ul.clientHeight < BOTTOM_THRESHOLD
    }

    function scrollToBottom(force = false) {
        if (!force && !autoScrollEnabled.value) return
        nextTick(() => {
            if (chatBoxulRef.value) {
                chatBoxulRef.value.scrollTop = chatBoxulRef.value.scrollHeight
                onScroll?.()
            }
        })
    }

    function jumpToTop() {
        const ul = chatBoxulRef.value
        if (!ul) return
        ul.scrollTop = 0
    }

    function jumpToBottom() {
        const ul = chatBoxulRef.value
        if (!ul) return
        ul.scrollTop = ul.scrollHeight
    }

    function onUlScroll() {
        updateIsAtBottom()
        if (isStreamingRef?.value) {
            autoScrollEnabled.value = isAtBottom.value
        }
        onScroll?.()
    }

    onMounted(() => {
        const ul = chatBoxulRef.value
        if (ul) ul.addEventListener('scroll', onUlScroll)
    })
    onUnmounted(() => {
        const ul = chatBoxulRef.value
        if (ul) ul.removeEventListener('scroll', onUlScroll)
    })

    return {
        chatBoxulRef,
        autoScrollEnabled,
        isAtBottom,
        scrollToBottom,
        jumpToTop,
        jumpToBottom,
        updateIsAtBottom,
        connectStreaming,
    }
}
