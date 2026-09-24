<script setup lang="ts">
/**
 * 分页（UI 设计规范 T1）：每页 20/50/100/200，默认 20，显示总条数；翻页后触发 change，由列表页重新加载。
 * <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
 */
defineProps<{ pageNo: number; pageSize: number; total: number }>()
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
    <el-pagination
      :current-page="pageNo"
      :page-size="pageSize"
      :total="total"
      :page-sizes="[20, 50, 100, 200]"
      layout="total, sizes, prev, pager, next, jumper"
      background
      @current-change="onPage"
      @size-change="onSize"
    />
  </div>
</template>

<style scoped>
.erp-pagination { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
