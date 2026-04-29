<template>
  <div class="chat-view">
    <ChatList />
    <div class="tool-box">
      <RouterLink :to="{ name: 'home' }" class="to-home">
        <img :src="logo" alt="" />
      </RouterLink>
      <div class="tool" title="开启新对话" @click="handleAddConversation">
        <Plus style="width: 20px; height: 20px;" />
      </div>
      <div class="tool" @click="chatStore.openSidebar" title="打开侧边栏">
        <ArrowRight style="width: 20px; height: 20px;" />
      </div>
      <span v-if="chatStore.currentTitle" class="chat-title" :class="{ 'title-open': chatStore.isSidebarOpen }">
        {{ chatStore.currentTitle }}
      </span>
    </div>
    <div class="message-wrap">
      <div class="message-box" :class="{ 'close-sidebar': !chatStore.isSidebarOpen }">
        <ChatBox ref="chatBoxRef" @scroll-changed="updateThumb" />
        <!-- 自定义滑条 -->
        <div class="custom-scrollbar" v-if="showScrollbar" @mousedown="startDrag">
          <div class="custom-scrollbar-thumb" :style="{ height: thumbHeight + '%', top: thumbTop + '%' }"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import ChatList from '@/components/ChatList.vue'
import ChatBox from '@/components/ChatBox.vue'
import { useChatStore } from '@/stores/chat'
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import logo from '@/assets/my-chat-logo.png'
import { generateChatId } from '@/utils/streamChat'

/**
 * 自定义滑条
 */
const chatBoxRef = ref<InstanceType<typeof ChatBox>>()
// 自定义滚动条状态
const showScrollbar = ref(false)
const thumbHeight = ref(50)         // 百分比
const thumbTop = ref(0)
const containerHeight = ref(0)      // ul 可视高度
const scrollHeight = ref(0)        // ul 总滚动高度
let dragState: { startY: number; startTop: number } | null = null
// 获取 ul 的引用
function getUl(): HTMLElement | null {
  return chatBoxRef.value?.chatBoxulRef ?? null
}
// 同步更新滑块状态
function updateThumb() {
  const ul = getUl()
  if (!ul) return
  const ratio = ul.scrollHeight / ul.clientHeight
  if (ratio <= 1) {
    showScrollbar.value = false
    return
  }
  showScrollbar.value = true
  containerHeight.value = ul.clientHeight
  scrollHeight.value = ul.scrollHeight
  thumbHeight.value = (ul.clientHeight / ul.scrollHeight) * 100
  thumbTop.value = (ul.scrollTop / (ul.scrollHeight - ul.clientHeight)) * (100 - thumbHeight.value)
}
// 开始拖动滑块
function startDrag(e: MouseEvent) {
  e.preventDefault() // 阻止默认行为（如文本选中、拖拽图片等）
  const thumb = (e.target as HTMLElement).closest('.custom-scrollbar-thumb')
  if (!thumb) return
  dragState = { startY: e.clientY, startTop: thumbTop.value }
  // 禁用文本选择，避免拖拽时选中页面文本
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', stopDrag)
}
function onDrag(e: MouseEvent) {
  if (!dragState) return
  const ul = getUl()
  if (!ul) return
  const deltaY = e.clientY - dragState.startY
  const trackHeight = ul.clientHeight
  const thumbActualHeight = trackHeight * (thumbHeight.value / 100)
  const maxTop = trackHeight - thumbActualHeight
  const newTop = Math.max(0, Math.min(maxTop, (dragState.startTop / 100) * trackHeight + deltaY))
  const newTopPercent = (newTop / trackHeight) * 100
  thumbTop.value = newTopPercent
  // 计算对应的 scrollTop
  const scrollRange = ul.scrollHeight - ul.clientHeight
  ul.scrollTop = (newTop / (trackHeight - thumbActualHeight)) * scrollRange
}
function stopDrag() {
  dragState = null
  // 恢复文本选择
  document.body.style.userSelect = ''
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
}

const chatStore = useChatStore()

function handleAddConversation() {
  const id = generateChatId()
  chatStore.createConversation(id)
}

onMounted(() => {
  chatStore.fetchChatList()
  nextTick(() => {
    updateThumb()
  })
})
onUnmounted(() => {
  const ul = getUl()
  if (ul) {
    ul.removeEventListener('scroll', updateThumb)
  }
})
</script>

<style scoped lang="less">
.close-sidebar {
  width: 100vw !important;
}

.title-open {
  margin-left: 165px;
}

.chat-view {
  position: relative;

  .tool-box {
    position: absolute;
    top: 15px;
    left: 15px;
    display: flex;
    align-items: center;
    width: auto;
    height: 32px;
    z-index: 5;

    .to-home {
      display: inline-block;
      height: 32px;
      margin-right: 10px;

      img {
        height: 100%;
      }
    }

    .tool {
      display: flex;
      justify-content: center;
      align-items: center;
      width: 30px;
      height: 30px;
      margin-right: 10px;
      border-radius: 50%;
      box-shadow: 2px 2px 5px rgba(0, 0, 0, 0.1);
      cursor: pointer;
    }

    .chat-title {
      padding-left: 15px;
      padding-right: 15px;
      font-size: 20px;
      font-weight: bold;
      color: black;
      background-color: #ffffff;
      border-radius: 8px;
      box-shadow: 2px 2px 5px rgba(0, 0, 0, 0.1);
      transition: margin-left .5s;
    }
  }

  .message-wrap {
    display: flex;
    justify-content: right;
    height: 100vh;

    .message-box {
      position: relative;
      display: flex;
      justify-content: center;
      width: calc(100vw - 300px);
      height: 100vh;
      transition: width .5s;

      .custom-scrollbar {
        position: absolute;
        right: 0;
        top: 0;
        width: 8px;
        height: 100%;
        background: transparent;
        z-index: 20;

        .custom-scrollbar-thumb {
          position: absolute;
          right: 0;
          width: 100%;
          background: rgba(0, 0, 0, 0.3);
          border-radius: 4px;
          cursor: pointer;
          transition: background 0.2s;

          &:hover {
            background: rgba(0, 0, 0, 0.5);
          }
        }
      }
    }
  }
}
</style>