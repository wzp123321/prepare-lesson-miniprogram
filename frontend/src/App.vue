<script setup lang="ts">
import zhCn from "element-plus/es/locale/lang/zh-cn";
// 根组件：挂载路由出口（实际布局由 layouts/AdminLayout.vue 提供）
</script>

<template>
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>
<style lang="less">
body,
html {
  margin: 0;
  padding: 0;
  width: 100%;
  height: 100%;
}
</style>
