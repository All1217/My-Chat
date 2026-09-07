<template>
  <div class="roleplay">
    <header class="roleplay-header">
      <el-button @click="goLobby">回到大厅</el-button>
    </header>
    <main class="roleplay-main">
      <h1 class="roleplay-title">角色扮演</h1>
      <p class="roleplay-desc">本页属于独立功能包，后续角色选择与情景互动在此扩展。</p>
    </main>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

/** 返回功能大厅。 */
function goLobby() {
  router.push({ name: 'lobby' })
}
</script>

<style scoped lang="less">
.roleplay {
  min-height: 100vh;
  background: linear-gradient(135deg, #f8f5ff 0%, #f0f5ff 50%, #fff 100%);
  padding: 32px 48px;
}

.roleplay-header {
  display: flex;
  justify-content: flex-end;
}

.roleplay-main {
  max-width: 720px;
  margin: 80px auto 0;
  text-align: center;
}

.roleplay-title {
  font-size: 36px;
  font-weight: 800;
  background: linear-gradient(135deg, #9d48ff, #437dff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 16px;
}

.roleplay-desc {
  font-size: 16px;
  color: #666;
  line-height: 1.7;
}
</style>
