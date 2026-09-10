<template>
  <div class="not-found">
    <div class="code tech-gradient-text">404</div>
    <p class="msg">页面走丢了，可能被碳排放吹散了 ~</p>
    <el-button type="primary" size="large" round @click="$router.push('/dashboard')">
      返回数据总览
    </el-button>
  </div>
</template>

<script setup>
</script>

<style scoped>
.not-found {
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 20px;
  background: var(--tech-bg);
}
.code {
  font-size: 120px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: 8px;
}
.msg {
  font-size: 15px;
  color: #51606e;
  margin: 0;
}
</style>
