<template>
  <div class="paper-layout">
    <aside class="paper-sidebar">
      <div class="sidebar-header">
        <h2>模拟炒股</h2>
        <p>虚拟盘 · 非实盘</p>
      </div>
      <el-menu :default-active="activeMenu" :router="true" class="paper-menu">
        <el-menu-item
          v-for="item in menuItems"
          :key="item.route"
          :index="item.route"
        >
          {{ item.label }}
        </el-menu-item>
      </el-menu>
    </aside>

    <main class="paper-content">
      <div class="top-bar">
        <el-button size="small" @click="goLobby">回到大厅</el-button>
      </div>
      <div class="content-area">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => route.path)

const menuItems = [
  { label: '持仓状况', route: '/app/paper-trade/positions' },
  { label: '历史记录', route: '/app/paper-trade/history' },
  { label: '账户设置', route: '/app/paper-trade/settings' },
]

function goLobby() {
  router.push({ name: 'lobby' })
}
</script>

<style scoped lang="less">
.paper-layout {
  display: flex;
  height: 100vh;
  background: linear-gradient(160deg, #ecfdf5 0%, #f8fafc 45%, #fff 100%);
}

.paper-sidebar {
  width: 200px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid #dcfce7;
  display: flex;
  flex-direction: column;

  .sidebar-header {
    padding: 16px 16px 12px;
    border-bottom: 1px solid #ebeef5;

    h2 {
      margin: 0;
      font-size: 18px;
      font-weight: 800;
      color: #166534;
    }

    p {
      margin: 4px 0 0;
      font-size: 12px;
      color: #65a30d;
    }
  }

  .paper-menu {
    border-right: none;
    flex: 1;

    .el-menu-item {
      font-size: 14px;
      font-weight: 500;
      color: #475569;

      &:hover {
        background: #f0fdf4;
        color: #15803d;
      }

      &.is-active {
        color: #15803d;
        background: #ecfdf5;
        border-right: 3px solid #16a34a;
      }
    }
  }
}

.paper-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  .top-bar {
    height: 44px;
    padding: 0 16px;
    display: flex;
    justify-content: flex-end;
    align-items: center;
    background: rgba(255, 255, 255, 0.9);
    border-bottom: 1px solid #e2e8f0;
    flex-shrink: 0;
  }

  .content-area {
    flex: 1;
    min-height: 0;
    overflow: auto;
    padding: 12px 16px 20px;
    display: flex;
    flex-direction: column;
  }
}
</style>
