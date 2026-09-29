<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatQty } from '@/utils/format'
import { INSP_RESULT, INSPECT_TYPE, ncrApi, reportApi, type BatchInspection, type NcrRow, type TraceResult } from '../api/quality'

defineOptions({ name: 'QcTracePage' })

/**
 * 质量追溯（需求 10-07 第 1 节）：在生产追溯的基础上叠加检验记录、特采 / 冻结标记。
 * 反向：成品批次 → 原材料；正向：原材料批次 → 成品 → 库存分布与出货（召回清单），可一键冻结在库批次（需关联 NCR）。
 */
const router = useRouter()
const direction = ref<'backward' | 'forward'>('backward')
const materialId = ref<string>()
const batchNo = ref('')
const loading = ref(false)
const r = ref<TraceResult>()

async function trace() {
  if (!materialId.value || !batchNo.value.trim()) return ElMessage.warning('请选择物料并填写批次')
  loading.value = true
  try {
    r.value = direction.value === 'backward' ? await reportApi.backward(materialId.value, batchNo.value.trim()) : await reportApi.forward(materialId.value, batchNo.value.trim())
  } finally {
    loading.value = false
  }
}
const inStock = computed(() => (r.value?.affected ?? []).filter((b) => Number(b.onHandQty) > 0 && !b.frozen))
const ncrs = ref<NcrRow[]>([])
const freezeVisible = ref(false)
const ncrId = ref<string>()
async function openFreeze() {
  if (!inStock.value.length) return ElMessage.info('没有需要冻结的在库批次')
  ncrs.value = (await ncrApi.page({ pageNo: 1, pageSize: 100, statuses: 'OPEN' })).list
  ncrId.value = undefined
  freezeVisible.value = true
}
async function freeze() {
  if (!ncrId.value) return ElMessage.warning('冻结批次需要关联 NCR')
  const res = await reportApi.freeze(ncrId.value, inStock.value.map((b) => ({ materialId: b.materialId, batchNo: b.batchNo })))
  freezeVisible.value = false
  if (res.skipped.length) ElMessageBox.alert(res.skipped.join('\n'), `已冻结 ${res.frozen} 个批次，跳过 ${res.skipped.length} 个`)
  else ElMessage.success(`已冻结 ${res.frozen} 个批次`)
  trace()
}
const insText = (x: BatchInspection) => `${INSPECT_TYPE[x.inspectType] ?? x.inspectType} ${x.docNo}`
const exportUrl = computed(() => `/quality/trace/forward/export`)
</script>

<template>
  <ErpPage description="反向追溯（客诉 / 成品批次 → 原材料）与正向追溯（问题原材料批次 → 成品 → 客户），每个节点显示检验记录">
    <ErpPanel>
      <div class="bar">
        <el-radio-group v-model="direction">
          <el-radio-button value="backward">反向（成品 → 原材料）</el-radio-button>
          <el-radio-button value="forward">正向（原材料 → 成品 / 客户）</el-radio-button>
        </el-radio-group>
        <MaterialSelect v-model="materialId" class="mat" />
        <el-input v-model="batchNo" placeholder="批次号" class="batch" @keyup.enter="trace" />
        <el-button type="primary" :loading="loading" @click="trace">追溯</el-button>
      </div>
    </ErpPanel>

    <template v-if="r">
      <ErpPanel :title="`${r.materialCode} ${r.materialName ?? ''} 批次 ${r.batchNo}`">
        <template #extra><ErpBadge v-if="r.frozen" type="danger" :dot="false">已冻结</ErpBadge></template>
        <div class="chips">
          <span class="muted">本批次检验：</span>
          <span v-if="!r.inspections.length" class="muted">无</span>
          <span v-for="x in r.inspections" :key="x.inspectionId" class="chip">
            <el-link type="primary" underline="never" @click="router.push(`/quality/inspection/${x.inspectionId}`)">{{ insText(x) }}</el-link>
            <StatusTag v-if="x.result" :value="x.result" :map="INSP_RESULT" />
            <el-link v-if="x.ncrId" type="danger" underline="never" @click="router.push(`/quality/ncr/${x.ncrId}`)">{{ x.ncrNo }}</el-link>
          </span>
        </div>
      </ErpPanel>

      <ErpPanel :title="direction === 'backward' ? '投入的原材料 / 半成品批次' : '使用该批次的产品批次'">
        <el-table :data="r.nodes" border>
          <el-table-column label="层级" prop="level" width="60" align="center" />
          <el-table-column label="生产订单" width="150">
            <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/production/prod-order/${row.prodOrderId}`)">{{ row.prodOrderNo }}</el-link></template>
          </el-table-column>
          <el-table-column label="产品" min-width="160"><template #default="{ row }">{{ row.productCode }} {{ row.productName }}</template></el-table-column>
          <el-table-column label="产品批次" width="150">
            <template #default="{ row }">{{ row.productBatchNo || '-' }}
              <ErpBadge v-if="row.productConcession" type="warning" :dot="false">特采</ErpBadge><ErpBadge v-if="row.productFrozen" type="danger" :dot="false">冻结</ErpBadge>
            </template>
          </el-table-column>
          <el-table-column label="产品检验" min-width="170">
            <template #default="{ row }">
              <div v-for="x in (row.productInspections as BatchInspection[])" :key="x.inspectionId">{{ insText(x) }} <StatusTag v-if="x.result" :value="x.result" :map="INSP_RESULT" /></div>
            </template>
          </el-table-column>
          <el-table-column label="投入物料" min-width="160"><template #default="{ row }">{{ row.componentCode }} {{ row.componentName }}</template></el-table-column>
          <el-table-column label="投入批次" width="150">
            <template #default="{ row }">{{ row.componentBatchNo || '-' }}
              <ErpBadge v-if="row.componentConcession" type="warning" :dot="false">特采</ErpBadge><ErpBadge v-if="row.componentFrozen" type="danger" :dot="false">冻结</ErpBadge>
            </template>
          </el-table-column>
          <el-table-column label="投入检验" min-width="170">
            <template #default="{ row }">
              <div v-for="x in (row.componentInspections as BatchInspection[])" :key="x.inspectionId">{{ insText(x) }} <StatusTag v-if="x.result" :value="x.result" :map="INSP_RESULT" /></div>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
        </el-table>
      </ErpPanel>

      <ErpPanel v-if="direction === 'forward'" title="受影响批次（库存分布与出货）">
        <template #extra>
          <ExportButton :url="exportUrl" :params="() => ({ materialId, batchNo })" permission="qc:trace:query" filename="召回清单" label="导出召回清单" />
          <el-button v-perm="'qc:ncr:create'" type="danger" @click="openFreeze">冻结全部在库批次</el-button>
        </template>
        <el-table :data="r.affected" border>
          <el-table-column label="物料" min-width="180"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
          <el-table-column label="批次" width="150">
            <template #default="{ row }">{{ row.batchNo }} <ErpBadge v-if="row.frozen" type="danger" :dot="false">冻结</ErpBadge></template>
          </el-table-column>
          <el-table-column label="在库数量" width="110" align="right"><template #default="{ row }">{{ formatQty(row.onHandQty) }}</template></el-table-column>
          <el-table-column label="库存分布" min-width="200">
            <template #default="{ row }"><div v-for="w in row.warehouses" :key="w.warehouseId">{{ w.warehouseName }} {{ formatQty(w.qty) }}</div></template>
          </el-table-column>
          <el-table-column label="已出货" width="110" align="right"><template #default="{ row }">{{ formatQty(row.shippedQty) }}</template></el-table-column>
          <el-table-column label="出货记录" min-width="220">
            <template #default="{ row }"><div v-for="s in row.shipments" :key="s.docNo">{{ s.bizDate }} {{ s.docNo }} {{ s.sourceNo ?? '' }} × {{ formatQty(s.qty) }}</div></template>
          </el-table-column>
        </el-table>
      </ErpPanel>
    </template>
    <ErpPanel v-else><ErpEmpty description="选择物料、填写批次后点击“追溯”" /></ErpPanel>

    <el-dialog v-model="freezeVisible" title="冻结全部在库批次" width="480px" append-to-body>
      <p>将冻结 {{ inStock.length }} 个在库批次，冻结来源为品质（仓库不能手工解冻）。</p>
      <el-form label-width="80px">
        <el-form-item label="关联 NCR" required>
          <el-select v-model="ncrId" filterable><el-option v-for="n in ncrs" :key="n.id" :value="n.id" :label="`${n.docNo} ${n.materialCode ?? ''} ${n.batchNo ?? ''}`" /></el-select>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="freezeVisible = false">取消</el-button><el-button type="danger" @click="freeze">冻结</el-button></template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.bar { display: flex; align-items: center; gap: var(--erp-space-3); flex-wrap: wrap; }
.mat { width: 280px; }
.batch { width: 200px; }
.chips { display: flex; flex-wrap: wrap; align-items: center; gap: var(--erp-space-3); }
.chip { display: inline-flex; align-items: center; gap: var(--erp-space-1); }
.muted { color: var(--erp-color-text-secondary); }
</style>
