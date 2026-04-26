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
        <ChatBox />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import ChatList from '@/components/ChatList.vue'
import ChatBox from '@/components/ChatBox.vue'
import { useChatStore } from '@/stores/chat'
import { onMounted } from 'vue'
import logo from '@/assets/my-chat-logo.png'
import { generateChatId } from '@/utils/streamChat'

const chatStore = useChatStore()

function handleAddConversation() {
    const id = generateChatId()
    chatStore.createConversation(id)
}

onMounted(() => {
  chatStore.fetchChatList()
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
    }
  }
}
</style>