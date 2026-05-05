<template>
  <div class="settings-layout">
    <!-- 左侧侧边栏 -->
    <aside class="settings-sidebar">
      <div class="sidebar-header">
        <h2>设置</h2>
      </div>
      <el-menu :default-active="activeMenu" :router="true" class="settings-menu" :collapse="false">
        <template v-for="item in menuItems" :key="item.label">
          <el-sub-menu v-if="item.children && item.children.length" :index="item.label">
            <template #title>
              <span>{{ item.label }}</span>
            </template>
            <el-menu-item v-for="child in item.children" :key="child.route" :index="child.route">
              {{ child.label }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.route">
            {{ item.label }}
          </el-menu-item>
        </template>
      </el-menu>
    </aside>

    <!-- 右侧内容区 -->
    <main class="settings-content">
      <div class="top-bar">
        <el-button type="primary" size="small" @click="goHome">首页</el-button>
        <el-button size="small" @click="goLobby">大厅</el-button>
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

interface MenuChild {
  label: string
  route: string
}
interface MenuItem {
  label: string
  route?: string
  children?: MenuChild[]
}

const menuItems: MenuItem[] = [
  { label: '大模型管理', route: '/settings/model' },
  { label: '工作区管理', route: '/settings/workspace' },
  { label: '提示词管理', route: '/settings/prompt' },
  { label: '角色卡片管理', route: '/settings/role' },
]

function goHome() {
  router.push({ name: 'home' })
}
function goLobby() {
  router.push({ name: 'lobby' })
}
</script>

<style scoped lang="less">
.settings-layout {
  display: flex;
  height: 100vh;
  background: #f5f7fa;

  .settings-sidebar {
    width: 240px;
    background: #fff;
    border-right: 1px solid #e4e7ed;
    display: flex;
    flex-direction: column;

    .sidebar-header {
      padding: 20px 20px 16px;
      border-bottom: 1px solid #ebeef5;

      h2 {
        font-size: 20px;
        font-weight: 700;
        color: #1a1a2e;
        margin: 0;
      }
    }

    .settings-menu {
      border-right: none;
      flex: 1;
      overflow-y: auto;

      .el-menu-item {
        font-size: 14px;
        font-weight: 500;
        color: #606266;
        &:hover {
          background-color: #f0f5ff;
          color: #437dff;
        }
        &.is-active {
          color: #437dff;
          background-color: #ecf5ff;
          border-right: 3px solid #437dff;
        }
      }

      .el-sub-menu__title {
        font-size: 14px;
        font-weight: 600;
        color: #303133;
        &:hover {
          background-color: #f0f5ff;
        }
      }
    }
  }

  .settings-content {
    flex: 1;
    overflow-y: auto;               /* 整个右侧区域滚动 */
    background: #fff;
    margin: 16px;
    border-radius: 12px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
    position: relative;             

    .top-bar {
      position: sticky;
      top: 0;
      z-index: 10;
      display: flex;
      justify-content: flex-end;
      align-items: center;
      gap: 8px;
      padding: 14px 30px;
      background: #fff;
      border-bottom: 1px solid #ebeef5;
    }

    .content-area {
      padding: 20px 30px;
    }
  }
}
</style>