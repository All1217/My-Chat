<template>
  <div class="chat-view">
    <!-- 左侧边栏 -->
    <ChatList @changeWidth="handleChangeWidth" ref="chatRef" :chatList="chatList" @id-change="handleIdChange" />
    <!-- 左上角按钮 -->
    <div class="tool-box">
      <RouterLink :to="{ name: 'home' }" class="to-home"><img :src="logo" alt="" @click=""></RouterLink>
      <div class="tool" title="开启新对话">
        <Plus style="width: 20px; height: 20px;" />
      </div>
      <div class="tool" @click="openSidebar" title="打开侧边栏">
        <ArrowRight style="width: 20px; height: 20px;" />
      </div>
      <span v-show="curTitle != ''" :class="isSidebarClosed ? 'chat-title' : 'chat-title title-open'">
        {{ curTitle }}
      </span>
    </div>
    <!-- 中部对话框 -->
    <div class="message-wrap">
      <div :class="isSidebarClosed ? 'message-box close-sidebar' : 'message-box'">
        <ChatBox chat-id=""></ChatBox>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import ChatList from '@/components/ChatList.vue';
import ChatBox from '@/components/ChatBox.vue';
import { ref, onMounted, computed } from 'vue';
import logo from '@/assets/my-chat-logo.png';
import { ragHttp } from '@/util/http';
import { ElMessage } from 'element-plus';
import { SpringAiChatMemoryVO } from '@/types/AiModule/types';

const isSidebarClosed = ref<boolean>(false);
const chatRef = ref<InstanceType<typeof ChatList>>()
function handleChangeWidth() {
  isSidebarClosed.value = true;
}
function openSidebar() {
  isSidebarClosed.value = false;
  chatRef.value?.openSidebarChild()
}
/**
 * 会话列表业务群
 */
const curChat = ref<SpringAiChatMemoryVO>(null);
const chatList = ref<SpringAiChatMemoryVO[]>([]);
const curTitle = computed(() => {
  if (curChat.value == null) return '';
  if (curChat.value.title == '' || curChat.value.title == null) return curChat.value.conversationId;
  return curChat.value.title;
});
async function getConversationIds() {
  try {
    const res = await ragHttp.get<SpringAiChatMemoryVO[]>('/ai/history/getConversations');
    if (res.data.code === 200) {
      chatList.value = res.data.data;
    } else {
      console.log(res.data.message);
      ElMessage.error(res.data.message || "获取会话列表失败！");
    }
  } catch (e) {
    console.log(e);
    ElMessage.error("获取会话列表失败！");
  }
}
const handleIdChange = (id: string) => {
  if (chatList.value.length == 0) return;
  let t = null;
  chatList.value.forEach(e => {
    if (e.conversationId == id) {
      t = e;
      return;
    }
  })
  curChat.value = t;
}
onMounted(() => {
  getConversationIds();
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