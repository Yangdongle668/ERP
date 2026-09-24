<script setup lang="ts">
/**
 * 分页（UI 设计规范 T1）：每页 20/50/100/200，默认 20，显示总条数；翻页后触发 change，由列表页重新加载。
 * <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="Number(total) || 0" @change="load" />
 */
// 后端 Long 序列化为字符串，这里兼容字符串总数
defineProps<{ pageNo: number; pageSize: number; total: number | string }>()
const emit = defineEmits<{ 'update:pageNo': [v: number]; 'update:pageSize': [v: number]; change: [] }>()

function onPage(p: number) {
  emit('update:pageNo', p)
  emit('change')
}
function onSize(s: number) {
  emit('update:pageSize', s)
  emit('update:pageNo', 1)
  emit('change')
}
</script>

<template>
  <div class="erp-pagination">
    <span class="erp-pagination__total">共 <span class="num">{{ (Number(total) || 0).toLocaleString('zh-CN') }}</span> 条</span>
    <el-pagination
      :current-page="pageNo"
      :page-size="pageSize"
      :total="Number(total) || 0"
      :page-sizes="[20, 50, 100, 200]"
      layout="sizes, prev, pager, next, jumper"
      background
      @current-change="onPage"
      @size-change="onSize"
    />
  </div>
</template>

<style scoped>
.erp-pagination { margin-top: 16px; display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
.erp-pagination__total { font-size: var(--erp-font-size-secondary); color: var(--erp-color-text-secondary); }
</style>
