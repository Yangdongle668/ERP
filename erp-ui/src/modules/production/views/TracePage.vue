<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { formatQty } from '@/utils/format'
import { traceApi, type TraceResult, type TraceTreeNode } from '../api/production'

defineOptions({ name: 'MfgTracePage' })

/**
 * 生产追溯（需求 09-07，T8）：反向——输入产品批次查投入的物料批次（半成品继续展开）；
 * 正向——输入原材料批次查用到它的产品批次（召回范围）。
 */
const router = useRouter()
const direction = ref<'BACKWARD' | 'FORWARD'>('BACKWARD')
const materialId = ref<string>()
const batchNo = ref('')
const result = ref<TraceResult>()
const loading = ref(false)

async function run() {
  if (!materialId.value || !batchNo.value.trim()) return ElMessage.warning('请选择物料并输入批次号')
  loading.value = true
  try {
    result.value = direction.value === 'BACKWARD'
      ? await traceApi.backward(materialId.value, batchNo.value.trim())
      : await traceApi.forward(materialId.value, batchNo.value.trim())
  } finally {
    loading.value = false
  }
}
const exportParams = () => ({ direction: direction.value, materialId: materialId.value, batchNo: batchNo.value.trim() })
const asNode = (n: unknown) => n as TraceTreeNode
</script>

<template>
  <ErpPage description="按批次追溯：反向查产品用了哪些物料批次，正向查物料批次用在哪些产品批次（召回清单）">
    <ErpPanel>
      <el-form inline @submit.prevent>
        <el-form-item label="方向">
          <el-radio-group v-model="direction">
            <el-radio-button value="BACKWARD">反向（产品 → 物料）</el-radio-button>
            <el-radio-button value="FORWARD">正向（物料 → 产品）</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="direction === 'BACKWARD' ? '产品' : '物料'"><MaterialSelect v-model="materialId" class="w-select" /></el-form-item>
        <el-form-item label="批次号"><el-input v-model="batchNo" clearable placeholder="批次号" @keyup.enter="run" /></el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="run">追溯</el-button>
          <ExportButton v-if="result" url="/production/trace/export" :params="exportParams" filename="生产追溯" permission="mfg:trace:query" />
        </el-form-item>
      </el-form>
    </ErpPanel>

    <ErpPanel v-if="result" :title="`涉及生产订单 ${result.orderCount} 张，${result.direction === 'BACKWARD' ? '物料' : '产品'}批次 ${result.batchCount} 个`">
      <el-alert v-for="n in result.notes" :key="n" :title="n" type="info" :closable="false" show-icon class="note" />
      <el-tree :data="[result.root]" node-key="key" default-expand-all :expand-on-click-node="false" :props="{ children: 'children', label: 'materialCode' }">
        <template #default="{ data: raw }">
          <span class="node">
            <span class="code">{{ asNode(raw).materialCode }}</span>
            <span>{{ asNode(raw).materialName }}</span>
            <el-tag v-if="asNode(raw).batchNo" size="small" type="primary">批次 {{ asNode(raw).batchNo }}</el-tag>
            <el-tag v-else-if="!asNode(raw).batchTracked" size="small" type="info">未启用批次</el-tag>
            <span v-if="asNode(raw).qty" class="num qty">× {{ formatQty(asNode(raw).qty) }}</span>
            <el-link v-if="asNode(raw).prodOrderId" type="primary" underline="never" @click="router.push(`/production/prod-order/${asNode(raw).prodOrderId}`)">
              {{ asNode(raw).prodOrderNo }}
            </el-link>
            <span v-if="asNode(raw).supplierBatchNo" class="muted">供应商批号 {{ asNode(raw).supplierBatchNo }}</span>
            <span v-if="asNode(raw).batchSourceNo" class="muted">来源 {{ asNode(raw).batchSourceNo }}</span>
            <el-tag v-if="asNode(raw).concession" size="small" type="warning">让步接收</el-tag>
          </span>
        </template>
      </el-tree>
    </ErpPanel>
    <ErpPanel v-if="result" :title="`出货记录（${result.shipments.length}）`" flush>
      <el-table :data="result.shipments">
        <el-table-column label="产品" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
        <el-table-column prop="batchNo" label="批次" width="150" />
        <el-table-column label="出货单" width="170">
          <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/shipping/shipment/${row.shipmentId}`)">{{ row.shipmentNo }}</el-link></template>
        </el-table-column>
        <el-table-column prop="shipDate" label="出货日期" width="110" />
        <el-table-column prop="customerName" label="客户" min-width="160" />
        <el-table-column label="数量" width="110" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.qty) }}</span></template></el-table-column>
        <template #empty><ErpEmpty compact description="这些批次还没有出货" /></template>
      </el-table>
    </ErpPanel>
    <ErpPanel v-else><ErpEmpty description="选择物料、输入批次号后追溯" /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
.w-select { width: 260px; }
.note { margin-bottom: var(--erp-space-2); }
.node { display: inline-flex; align-items: center; gap: var(--erp-space-2); }
.code { color: var(--erp-color-text-secondary); }
.qty { color: var(--erp-color-text); }
.muted { color: var(--erp-color-text-tertiary); }
</style>
