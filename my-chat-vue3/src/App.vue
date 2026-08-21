<template>
  <div id="app">
    <!-- 路由出口 - 匹配的组件会渲染在这里 -->
    <router-view />
  </div>
</template>
<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useNotifyStore } from '@/stores/notify'

const notifyStore = useNotifyStore()

onMounted(() => {
  notifyStore.connect()
})

onUnmounted(() => {
  notifyStore.disconnect()
})
</script>
<style lang="css" scoped>
#app{
  max-height: 100vh;
  overflow: hidden;
}
</style>
