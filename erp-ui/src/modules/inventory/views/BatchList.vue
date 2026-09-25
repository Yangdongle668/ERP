<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { formatQty } from '@/utils/format'
import { batchApi, BIZ_TYPE_OPTIONS, docRoute, labelOf, type BatchDetail, type BatchRow, type SerialRow } from '../api/inventory'

defineOptions({ name: 'InvBatchList' })

/** 批次与序列号（需求 08-07）：批次档案、库存分布、追溯流水；冻结/解冻；序列号查询 */
const route = useRoute()
const router = useRouter()
const tab = ref('batch')
const reasonRef = ref<{ open: (o: { title?: string; tip?: string }) => Promise<string | undefined> }>()

type BQuery = { materialId?: string; batchNo?: string; supplierBatchNo?: string; expiry?: string; frozen?: boolean; hasStock?: boolean }
const batches = useListPage<BQuery, BatchRow>({
  api: (q) => batchApi.page(q),
  defaultQuery: () => ({ hasStock: true, materialId: typeof route.query.materialId === 'string' ? route.query.materialId : undefined })
})
const bFields: SearchField[] = [
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'batchNo', label: '批次号', upper: true },
  { prop: 'supplierBatchNo', label: '供应商批号' },
  { prop: 'expiry', label: '有效期', type: 'select', options: [{ value: 'EXPIRED', label: '已过期' }, { value: 'SOON', label: '临期' }, { value: 'NORMAL', label: '正常' }] },
  { prop: 'frozen', label: '冻结', type: 'select', options: [{ value: true, label: '已冻结' }, { value: false, label: '未冻结' }] },
  { prop: 'hasStock', label: '只看有库存', type: 'slot' }
]
const bColumns: TableColumn<BatchRow>[] = [
  { prop: 'batchNo', label: '批次号', width: 150, type: 'link', onClick: (r) => openDetail(r) },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 150 },
  { prop: 'materialSpec', label: '规格', minWidth: 130, hidden: true },
  { prop: 'supplierBatchNo', label: '供应商批号', width: 120 },
  { prop: 'productionDate', label: '生产日期', width: 100, type: 'date' },
  { prop: 'expireDate', label: '到期日期', width: 100, type: 'date' },
  { prop: 'remainingDays', label: '剩余天数', width: 90, slot: true, align: 'right' },
  { prop: 'firstInDate', label: '首次入库', width: 100, type: 'date' },
  { prop: 'onHandQty', label: '现存量', width: 100, type: 'qty', uomProp: 'uom' },
  { prop: 'availableQty', label: '可用量', width: 100, type: 'qty', uomProp: 'uom' },
  { prop: 'frozen', key: 'flags', label: '标记', width: 130, slot: true },
  { prop: 'sourceNo', label: '来源单号', width: 140 }
]

async function freeze(r: BatchRow, on: boolean) {
  const reason = await reasonRef.value?.open({ title: on ? '冻结原因' : '解冻原因', tip: on ? '冻结后该批次不能出库、不计入可用量' : undefined })
  if (!reason) return
  await (on ? batchApi.freeze(r.id, reason) : batchApi.unfreeze(r.id, reason))
  ElMessage.success(on ? '已冻结' : '已解冻')
  batches.load()
}

// ---------- 详情抽屉 ----------
const drawer = ref(false)
const detail = ref<BatchDetail>()
async function openDetail(r: BatchRow) {
  detail.value = await batchApi.get(r.id)
  drawer.value = true
}

// ---------- 序列号 ----------
type SQuery = { materialId?: string; serialNo?: string; status?: string }
const serials = useListPage<SQuery, SerialRow>({ api: (q) => batchApi.serials(q), immediate: false })
const sFields: SearchField[] = [
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'serialNo', label: '序列号' },
  { prop: 'status', label: '状态', type: 'select', options: [{ value: 'IN_STOCK', label: '在库' }, { value: 'OUT', label: '已出库' }] }
]
const sColumns: TableColumn<SerialRow>[] = [
  { prop: 'serialNo', label: '序列号', width: 180, type: 'link', onClick: (r) => openHistory(r) },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 150 },
  { prop: 'batchNo', label: '批次', width: 130 },
  { prop: 'status', label: '状态', width: 80, formatter: (r) => (r.status === 'IN_STOCK' ? '在库' : '已出库') },
  { prop: 'warehouseName', label: '仓库', width: 110 },
  { prop: 'locationCode', label: '库位', width: 90 },
  { prop: 'lastDocNo', label: '最近单据', width: 150 }
]
const history = ref<{ txnId: string; direction: string; docNo: string; createdAt: string }[]>([])
const historyOf = ref<SerialRow>()
async function openHistory(r: SerialRow) {
  historyOf.value = r
  history.value = await batchApi.serialHistory(r.id)
}
watch(tab, (t) => {
  if (t === 'serial' && !serials.list.value.length) serials.load()
})

const asB = (r: unknown) => r as BatchRow
</script>

<template>
  <ErpPage description="批次档案在首次入库时建立；冻结的批次不能出库、不计入可用量，品质冻结的批次由品质解冻">
    <ErpPanel flush>
      <el-tabs v-model="tab" class="tabs">
        <el-tab-pane label="批次" name="batch">
          <ErpSearchForm v-model="batches.query" :fields="bFields" :loading="batches.loading.value" @search="batches.search" @reset="batches.reset">
            <template #field-materialId><MaterialSelect v-model="batches.query.materialId" placeholder="全部" class="w200" /></template>
            <template #field-hasStock><el-switch v-model="batches.query.hasStock" /></template>
          </ErpSearchForm>
          <ErpTable :columns="bColumns" :data="batches.list.value" :loading="batches.loading.value" storage-key="inv.batch" :actions-width="110" @refresh="batches.load">
            <template #col-remainingDays="{ row }">
              <span v-if="asB(row).remainingDays !== undefined && asB(row).remainingDays !== null"
                    :class="['num', { 'text-danger': (asB(row).remainingDays ?? 0) < 0, 'text-warning': (asB(row).remainingDays ?? 0) >= 0 && (asB(row).remainingDays ?? 0) <= 30 }]">
                {{ asB(row).remainingDays }}</span>
            </template>
            <template #col-frozen="{ row }">
              <ErpBadge v-if="asB(row).frozen" type="danger">冻结{{ asB(row).frozenByModule === 'quality' ? '（品质）' : '' }}</ErpBadge>
              <ErpBadge v-if="asB(row).concession" type="warning">特采</ErpBadge>
            </template>
            <template #actions="{ row }">
              <RowActions :actions="[
                { label: '冻结', permission: 'inv:batch:freeze', visible: !asB(row).frozen, handler: () => freeze(asB(row), true) },
                { label: '解冻', permission: 'inv:batch:freeze', visible: asB(row).frozen, handler: () => freeze(asB(row), false) },
                { label: '追溯', handler: () => openDetail(asB(row)) }
              ]" />
            </template>
          </ErpTable>
          <ErpPagination v-model:page-no="batches.query.pageNo" v-model:page-size="batches.query.pageSize" :total="batches.total.value" @change="batches.load" />
        </el-tab-pane>

        <el-tab-pane label="序列号" name="serial">
          <ErpSearchForm v-model="serials.query" :fields="sFields" :loading="serials.loading.value" @search="serials.search" @reset="serials.reset">
            <template #field-materialId><MaterialSelect v-model="serials.query.materialId" placeholder="全部" class="w200" /></template>
          </ErpSearchForm>
          <ErpTable :columns="sColumns" :data="serials.list.value" :loading="serials.loading.value" storage-key="inv.serial" @refresh="serials.load" />
          <ErpPagination v-model:page-no="serials.query.pageNo" v-model:page-size="serials.query.pageSize" :total="serials.total.value" @change="serials.load" />
          <template v-if="historyOf">
            <div class="group-title">序列号 {{ historyOf.serialNo }} 的流转</div>
            <el-table :data="history">
              <el-table-column label="方向" width="80"><template #default="{ row }">{{ row.direction === 'IN' ? '入库' : '出库' }}</template></el-table-column>
              <el-table-column prop="docNo" label="单号" width="180" />
              <el-table-column prop="createdAt" label="时间" width="170" />
            </el-table>
          </template>
        </el-tab-pane>
      </el-tabs>
    </ErpPanel>

    <el-drawer v-model="drawer" :title="detail ? `批次 ${detail.batch.batchNo} - ${detail.batch.materialCode} ${detail.batch.materialName}` : ''" size="760px" append-to-body>
      <template v-if="detail">
        <el-descriptions :column="3" size="small">
          <el-descriptions-item label="供应商批号">{{ detail.batch.supplierBatchNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="生产日期">{{ detail.batch.productionDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="到期日期">{{ detail.batch.expireDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="首次入库">{{ detail.batch.firstInDate }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ detail.batch.sourceNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="冻结原因">{{ detail.batch.frozenReason || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="group-title">库存分布</div>
        <el-table :data="detail.distribution">
          <el-table-column prop="warehouseName" label="仓库" min-width="120" />
          <el-table-column prop="locationCode" label="库位" width="100" />
          <el-table-column label="数量" width="120" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.qty) }}</span></template></el-table-column>
        </el-table>
        <div class="group-title">追溯流水</div>
        <el-table :data="detail.txns" max-height="360">
          <el-table-column prop="bizDate" label="日期" width="100" />
          <el-table-column label="单号" width="160">
            <template #default="{ row }">
              <el-link v-if="docRoute(row.docType, row.docId)" type="primary" underline="never" @click="drawer = false; router.push(docRoute(row.docType, row.docId)!)">{{ row.docNo }}</el-link>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="110"><template #default="{ row }">{{ labelOf(BIZ_TYPE_OPTIONS, row.bizType) }}</template></el-table-column>
          <el-table-column prop="warehouseName" label="仓库" width="100" />
          <el-table-column label="数量" width="100" align="right">
            <template #default="{ row }"><span :class="['num', row.direction === 'OUT' ? 'text-danger' : 'text-success']">{{ row.direction === 'OUT' ? '-' : '+' }}{{ formatQty(row.qty) }}</span></template>
          </el-table-column>
          <el-table-column label="" width="60"><template #default="{ row }"><ErpBadge v-if="row.reversal" type="info">冲销</ErpBadge></template></el-table-column>
        </el-table>
      </template>
    </el-drawer>
    <ReasonDialog ref="reasonRef" />
  </ErpPage>
</template>

<style scoped>
.tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
</style>
