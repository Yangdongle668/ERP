<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { http, type PageResult } from '@/api/http'
import type { StatusMap } from '@/components'
import { formatAmount, formatQty } from '@/utils/format'

/** 客户 360 视图的业务单据页签（报价、订单、出货、应收、客诉）：调用对应模块的列表接口，按客户过滤，最多显示最近 100 条 */
export interface RelatedColumn {
  prop: string
  label: string
  width?: number
  minWidth?: number
  type?: 'link' | 'amount' | 'qty' | 'status' | 'text'
  statusMap?: StatusMap
}

const props = defineProps<{
  url: string
  customerId: string
  columns: RelatedColumn[]
  /** 单号列跳转的详情路由前缀，如 /sales/order/ */
  routePrefix: string
  params?: Record<string, unknown>
  emptyText: string
}>()

const router = useRouter()
const rows = ref<Record<string, unknown>[]>([])
const loading = ref(false)
const total = ref(0)

onMounted(async () => {
  loading.value = true
  try {
    const r = await http.get<PageResult<Record<string, unknown>>>(props.url, { customerId: props.customerId, pageNo: 1, pageSize: 100, ...props.params })
    rows.value = r.list
    total.value = r.total
  } finally {
    loading.value = false
  }
})

function text(v: unknown) {
  return v === null || v === undefined ? '' : String(v)
}
</script>

<template>
  <el-table v-loading="loading" :data="rows">
    <el-table-column v-for="c in columns" :key="c.prop" :label="c.label" :width="c.width" :min-width="c.minWidth"
      :align="c.type === 'amount' || c.type === 'qty' ? 'right' : undefined">
      <template #default="{ row }">
        <el-link v-if="c.type === 'link'" type="primary" underline="never" @click="router.push(routePrefix + row.id)">{{ text(row[c.prop]) }}</el-link>
        <span v-else-if="c.type === 'amount'" class="num">{{ formatAmount(row[c.prop] as string) }}</span>
        <span v-else-if="c.type === 'qty'" class="num">{{ formatQty(row[c.prop] as string) }}</span>
        <StatusTag v-else-if="c.type === 'status'" :value="row[c.prop] as string" :map="c.statusMap" />
        <template v-else>{{ text(row[c.prop]) }}</template>
      </template>
    </el-table-column>
    <template #empty><ErpEmpty compact :description="emptyText" /></template>
  </el-table>
  <p v-if="total > rows.length" class="more">共 {{ total }} 条，仅显示最近 {{ rows.length }} 条</p>
</template>

<style scoped>
.more { margin-top: var(--erp-space-2); color: var(--erp-color-text-secondary); }
</style>
