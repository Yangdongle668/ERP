<script setup lang="ts">
import { useRoute } from 'vue-router'

/** 未开发页面的占位（菜单项未指定 component 时使用） */
const route = useRoute()
</script>

<template>
  <ErpPage>
    <ErpPanel>
      <ErpEmpty icon="Tools" :description="`「${route.meta.module} / ${route.meta.title}」正在开发中`">
        <span v-if="route.meta.doc" class="erp-text-caption">需求文档：docs/requirements/{{ route.meta.doc }}</span>
      </ErpEmpty>
    </ErpPanel>
  </ErpPage>
</template>
