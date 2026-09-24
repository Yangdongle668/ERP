<template>
  <el-config-provider :locale="zhCn" :message="{ max: 3, grouping: true }" :button="{ autoInsertSpace: false }">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>
