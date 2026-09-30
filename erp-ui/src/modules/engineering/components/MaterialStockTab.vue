<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { http } from '@/api/http'
import { formatQty } from '@/utils/format'

/** 物料详情“库存”页签：仓库模块库存查询（按批次、库位），需要 inv:stock:query 权限 */
interface StockRow {
  warehouseName: string; locationCode?: string; batchNo?: string; expireDate?: string; frozen: boolean
  onHandQty: string; availableQty?: string; reservedQty?: string
}

const props = defineProps<{ materialId: string }>()
const rows = ref<StockRow[]>([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    rows.value = (await http.get<{ list: StockRow[] }>('/inventory/stocks', { materialId: props.materialId, groupBy: 'BATCH', pageNo: 1, pageSize: 200 })).list
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <el-table v-loading="loading" :data="rows">
    <el-table-column prop="warehouseName" label="仓库" min-width="140" />
    <el-table-column prop="locationCode" label="库位" width="120" />
    <el-table-column label="批次" width="160"><template #default="{ row }">{{ row.batchNo || '-' }}<ErpBadge v-if="row.frozen" type="danger" :dot="false" class="gap">冻结</ErpBadge></template></el-table-column>
    <el-table-column prop="expireDate" label="到期日期" width="110" />
    <el-table-column label="现存量" width="120" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.onHandQty) }}</span></template></el-table-column>
    <template #empty><ErpEmpty compact description="没有库存" /></template>
  </el-table>
</template>

<style scoped>
.gap { margin-left: var(--erp-space-1); }
</style>
