<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <el-card>
    <el-empty :description="`「${route.meta.module} - ${route.meta.title}」开发中`">
      <p v-if="route.meta.doc" class="doc">需求文档：docs/requirements/{{ route.meta.doc }}</p>
    </el-empty>
  </el-card>
</template>

<style scoped>
.doc { color: var(--el-text-color-secondary); font-size: 13px; }
</style>
