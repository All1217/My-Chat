<template>
  <div class="chat-view">
    <!-- 左侧边栏 -->
    <ChatList @changeWidth="handleChangeWidth" ref="chatRef"></ChatList>
    <!-- 左上角按钮 -->
    <div class="tool-box">
      <RouterLink :to="{ name: 'home' }" class="to-home"><img :src="logo" alt="" @click=""></RouterLink>
      <div class="tool" title="开启新对话">
        <Plus style="width: 20px; height: 20px;" />
      </div>
      <div class="tool" @click="openSidebar" title="打开侧边栏">
        <ArrowRight style="width: 20px; height: 20px;" />
      </div>
      <span :class="isSidebarClosed ? 'chat-title' : 'chat-title title-open'">对话框标题对话框标题对话框标题</span>
    </div>
    <!-- 中部对话框 -->
    <div class="message-wrap">
      <div :class="isSidebarClosed ? 'message-box close-sidebar' : 'message-box'">
        <ChatBox></ChatBox>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import ChatList from '@/components/ChatList.vue';
import ChatBox from '../components/ChatBox.vue';
import { ref } from 'vue';
import logo from '@/assets/my-chat-logo.png'

const isSidebarClosed = ref<boolean>(false);
const chatRef = ref<InstanceType<typeof ChatList>>()
function handleChangeWidth() {
  isSidebarClosed.value = true;
}
function openSidebar() {
  isSidebarClosed.value = false;
  chatRef.value?.openSidebarChild()
}
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