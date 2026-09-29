<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatQty } from '@/utils/format'
import { BALANCE_TYPE, mrpApi, num, type Balance } from '../api/pmc'

defineOptions({ name: 'PmcMrpBalance' })

/** 供需平衡（需求 06-03 4.2）：按日期列出期初、需求、供给与预计结存，解释建议的来源；点击父件需求跳到父件 */
const route = useRoute()
const router = useRouter()
const materialId = ref<string>(typeof route.query.materialId === 'string' ? route.query.materialId : '')
const runId = typeof route.query.runId === 'string' ? route.query.runId : undefined
const b = ref<Balance>()
const loading = ref(false)
async function load() {
  if (!materialId.value) return
  loading.value = true
  try {
    b.value = await mrpApi.balance(materialId.value, runId)
  } finally {
    loading.value = false
  }
}
onMounted(load)
function toParent(id?: string) {
  if (!id) return
  materialId.value = id
  router.replace({ query: { ...route.query, materialId: id } })
  load()
}
const projClass = (v: string, ss: string) => (num(v) < 0 ? 'text-danger' : num(v) < num(ss) ? 'low' : '')
</script>

<template>
  <ErpPage description="供需平衡：期初可用 + 供给 − 需求 = 预计结存（低于安全库存橙色，小于 0 红色）">
    <ErpPanel>
      <el-form inline @submit.prevent>
        <el-form-item label="物料"><MaterialSelect v-model="materialId" class="mat" @update:model-value="load" /></el-form-item>
        <el-form-item v-if="b?.runNo"><span class="text-muted">运算 {{ b.runNo }}，安全库存 {{ formatQty(b.safetyStock) }} {{ b.baseUom }}</span></el-form-item>
      </el-form>
      <el-table v-loading="loading" :data="b?.rows ?? []">
        <el-table-column prop="date" label="日期" width="110" />
        <el-table-column label="类型" width="120"><template #default="{ row }">{{ BALANCE_TYPE[row.type] ?? row.type }}</template></el-table-column>
        <el-table-column label="单号 / 来源" min-width="200">
          <template #default="{ row }">
            {{ row.docNo || '-' }}
            <el-link v-if="row.parentMaterialId" type="primary" underline="never" @click="toParent(row.parentMaterialId)">父件 {{ row.parentCode }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="需求" width="120" align="right"><template #default="{ row }">{{ num(row.demandQty) ? formatQty(row.demandQty) : '' }}</template></el-table-column>
        <el-table-column label="供给" width="120" align="right"><template #default="{ row }">{{ num(row.supplyQty) ? formatQty(row.supplyQty) : '' }}</template></el-table-column>
        <el-table-column label="预计结存" width="120" align="right">
          <template #default="{ row }"><span :class="projClass(row.projectedQty, row.safetyStock)">{{ formatQty(row.projectedQty) }}</span></template>
        </el-table-column>
        <template #empty><ErpEmpty compact description="选择物料查看最近一次运算的供需平衡" /></template>
      </el-table>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.mat { width: 280px; }
.low { color: var(--erp-color-warning); }
</style>
