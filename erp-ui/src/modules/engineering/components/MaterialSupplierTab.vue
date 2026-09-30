<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { http, type PageResult } from '@/api/http'
import { useUserStore } from '@/stores/user'
import { formatPrice, formatQty } from '@/utils/format'

/** 物料详情“供应商”页签：资材模块的可供供应商（货源）与有效价格（价格需要 pur:price:query + pur:price:view 权限） */
interface SupplierRow {
  id: string; supplierId: string; supplierCode: string; supplierName: string; supplierPartNo?: string; supplyStatus: string; isDefault: boolean
  leadTimeDays?: number; moq?: string; mpq?: string; quotaPct?: string
}
interface PriceRow {
  id: string; supplierName: string; currency: string; minQty: string; priceInclTax: string; effectiveFrom: string; effectiveTo?: string
}

const props = defineProps<{ materialId: string }>()
const router = useRouter()
const me = useUserStore()
const suppliers = ref<SupplierRow[]>([])
const prices = ref<PriceRow[]>([])
const canPrice = me.hasPermission('pur:price:query') && me.hasPermission('pur:price:view')
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    suppliers.value = await http.get<SupplierRow[]>('/purchase/supplier-materials', { materialId: props.materialId })
    if (canPrice) prices.value = (await http.get<PageResult<PriceRow>>('/purchase/prices', { materialId: props.materialId, pageNo: 1, pageSize: 100 })).list
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading">
    <el-table :data="suppliers">
      <el-table-column label="供应商" min-width="200">
        <template #default="{ row }">
          <el-link type="primary" underline="never" @click="router.push(`/purchase/supplier/${row.supplierId}`)">{{ row.supplierCode }} {{ row.supplierName }}</el-link>
          <ErpBadge v-if="row.isDefault" type="primary" :dot="false" class="gap">默认</ErpBadge>
        </template>
      </el-table-column>
      <el-table-column prop="supplierPartNo" label="供应商料号" width="140" />
      <el-table-column label="采购提前期" width="100" align="right"><template #default="{ row }">{{ row.leadTimeDays ?? '-' }}</template></el-table-column>
      <el-table-column label="MOQ" width="100" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.moq) }}</span></template></el-table-column>
      <el-table-column label="MPQ" width="100" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.mpq) }}</span></template></el-table-column>
      <el-table-column label="配额 %" width="80" align="right"><template #default="{ row }">{{ row.quotaPct ?? '-' }}</template></el-table-column>
      <template #empty><ErpEmpty compact description="没有可供供应商" /></template>
    </el-table>
    <template v-if="canPrice">
      <h4 class="sub">有效价格</h4>
      <el-table :data="prices">
        <el-table-column prop="supplierName" label="供应商" min-width="180" />
        <el-table-column prop="currency" label="币别" width="70" />
        <el-table-column label="起订量" width="100" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.minQty) }}</span></template></el-table-column>
        <el-table-column label="含税单价" width="120" align="right"><template #default="{ row }"><span class="num">{{ formatPrice(row.priceInclTax) }}</span></template></el-table-column>
        <el-table-column prop="effectiveFrom" label="生效日期" width="110" />
        <el-table-column label="失效日期" width="110"><template #default="{ row }">{{ row.effectiveTo ?? '长期' }}</template></el-table-column>
        <template #empty><ErpEmpty compact description="没有有效价格" /></template>
      </el-table>
    </template>
  </div>
</template>

<style scoped>
.gap { margin-left: var(--erp-space-1); }
.sub { margin: var(--erp-space-5) 0 var(--erp-space-2); font-size: var(--erp-font-size-body); font-weight: var(--erp-font-weight-semibold); }
</style>
