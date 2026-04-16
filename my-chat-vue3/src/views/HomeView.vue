<template>
  <div class="home">
    <div class="img-box">
      <img :src="logo" alt="">
    </div>
    <h1>My&nbsp;&nbsp;Chat</h1>
    <p class="home-sub-title">构建个人专属知识库</p>
    <div class="btns">
      <RouterLink :to="{ name: 'chat' }" class="home-entrance-btn">普通聊天</RouterLink>
      <RouterLink :to="{ name: 'about' }" class="home-entrance-btn">文件解析</RouterLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import logo from '@/assets/my-chat-logo.png'
</script>

<style scoped lang="less">
.home {
  padding-top: 60px;

  .img-box {
    display: flex;
    justify-content: center;

    img {
      width: 250px;
    }
  }

  h1 {
    text-align: center;
    color: #9d48ff;
    font-size: 90px;
    line-height: 120px;
    font-weight: bolder;
  }

  .home-sub-title {
    text-align: center;
    font-size: 24px;
    line-height: 40px;
    color: black;
    font-weight: 900;
  }

  .btns {
    display: flex;
    justify-content: center;
    gap: 100px;
    padding-top: 40px;

    .home-entrance-btn {
      width: 120px;
      height: 45px;
      border: 2px solid #437dff;
      border-radius: 10px;
      background-color: #fff;
      color: #437dff;
      font-size: 16px;
      font-weight: 900;
      text-align: center;
      line-height: 41px;
    }
  }
}
</style>