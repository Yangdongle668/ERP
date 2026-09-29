<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatAmount, formatDateTime, formatQty } from '@/utils/format'
import { forwarderApi, LOGISTICS_STATUS, SHIPMENT_STATUS, shipmentApi, type ForwarderOption, type ShipmentDetail } from '../api/shipping'
import LogisticsDialogs from '../components/LogisticsDialogs.vue'

defineOptions({ name: 'ShpShipmentDetail' })

/** 出货单详情（需求 11-03 3.2 / 3.3，T5）：草稿可改单头、提交出库；待出库可撤回；已出货登记物流、签收；生成单证 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<ShipmentDetail>()
const tab = ref('lines')
const forwarders = ref<ForwarderOption[]>([])
const dialogs = ref<InstanceType<typeof LogisticsDialogs>>()

async function load() {
  d.value = await shipmentApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}
onMounted(async () => {
  forwarders.value = await forwarderApi.options().catch(() => [])
  load()
})
const s = computed(() => d.value?.shipmentStatus)
const shipped = computed(() => s.value === 'SHIPPED' || s.value === 'COMPLETED')
const docReady = computed(() => !!s.value && !['DRAFT', 'PENDING', 'VOIDED'].includes(s.value))

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', type: 'danger', permission: 'shp:shipment:delete', visible: () => s.value === 'DRAFT', confirm: '删除后箱可重新出货，确定删除吗？',
    handler: async () => { await shipmentApi.remove(id.value); ElMessage.success('已删除'); tabs.remove([tabKeyOf(route)]); router.push('/shipping/shipment') } },
  { key: 'void', label: '作废', permission: 'shp:shipment:delete', visible: () => s.value === 'DRAFT', reasonRequired: true, reasonTitle: '作废原因',
    handler: async (reason) => { await shipmentApi.void(id.value, reason!); ElMessage.success('已作废'); load() } },
  { key: 'withdraw', label: '撤回', permission: 'shp:shipment:withdraw', visible: () => s.value === 'SUBMITTED', confirm: '撤回将作废未确认的出库单，确定吗？',
    handler: async () => { await shipmentApi.withdraw(id.value); ElMessage.success('已撤回'); load() } },
  { key: 'pl', label: '生成 Packing List', permission: 'shp:document:create', visible: () => docReady.value && !d.value?.packingList, handler: createPl },
  { key: 'inv', label: '生成 Invoice', permission: 'shp:document:create', visible: () => docReady.value && !d.value?.invoice, handler: createInvoice },
  { key: 'customs', label: '生成报关资料', permission: 'shp:document:create', visible: () => docReady.value && !d.value?.customs && !!d.value?.foreignCustomer,
    handler: async () => { const c = await shipmentApi.createCustoms(id.value); router.push(`/shipping/customs/${c}`) } },
  { key: 'logistics', label: '登记物流', permission: 'shp:logistics:update', visible: () => shipped.value, handler: () => d.value && dialogs.value?.openLogistics(d.value) },
  { key: 'event', label: '更新物流状态', permission: 'shp:logistics:update', visible: () => shipped.value, handler: () => d.value && dialogs.value?.openEvent(d.value) },
  { key: 'sign', label: '登记签收', permission: 'shp:logistics:update', visible: () => s.value === 'SHIPPED' || (s.value === 'COMPLETED' && !d.value?.signedAt),
    handler: () => d.value && dialogs.value?.openSign(d.value) },
  { key: 'edit', label: '编辑', permission: 'shp:shipment:update', visible: () => s.value === 'DRAFT', handler: openEdit },
  { key: 'submit', label: '提交', type: 'primary', permission: 'shp:shipment:submit', visible: () => s.value === 'DRAFT', handler: submit }
])
async function submit() {
  const r = await shipmentApi.submit(id.value)
  if (r.warnings.length) ElNotification({ type: 'warning', title: '提交提示', message: r.warnings.join('；'), duration: 8000 })
  ElMessage.success('已提交，等待仓库确认出库')
  load()
}
async function createPl() {
  const pl = await shipmentApi.createPackingList(id.value)
  router.push(`/shipping/packing-list/${pl}`)
}
async function createInvoice() {
  const inv = await shipmentApi.createInvoice(id.value)
  router.push(`/shipping/invoice/${inv}`)
}

// 编辑单头（草稿）
const editDialog = ref(false)
const ef = ref<{ shipDate?: string; forwarderId?: string; containerNo?: string; sealNo?: string; remark?: string }>({})
function openEdit() {
  const x = d.value!
  ef.value = { shipDate: x.shipDate, forwarderId: x.forwarderId, containerNo: x.containerNo, sealNo: x.sealNo, remark: x.remark }
  editDialog.value = true
}
async function saveEdit() {
  if (!ef.value.shipDate) return ElMessage.warning('请选择出货日期')
  await shipmentApi.update(id.value, { ...ef.value, forwarderId: ef.value.forwarderId || undefined })
  editDialog.value = false
  ElMessage.success('已保存')
  load()
}
const steps = [
  { status: 'DRAFT', label: '草稿' }, { status: 'SUBMITTED', label: '待出库' }, { status: 'SHIPPED', label: '已出货' }, { status: 'COMPLETED', label: '已完成' }
]
const stepCurrent = computed(() => (s.value === 'PENDING' ? 'DRAFT' : s.value))
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? '出货单'" :status="d?.shipmentStatus" :status-map="SHIPMENT_STATUS" :actions="actions" @back="router.push('/shipping/shipment')">
        <template #actions-prefix>
          <ApprovalActions v-if="d" biz-type="SHP_SHIPMENT" :biz-id="id" @changed="load" />
          <PrintButton v-if="d" biz-type="SHP_SHIPMENT" :ids="[id]" permission="shp:shipment:print" />
        </template>
      </DocPageHeader>
    </template>
    <template v-if="d">
      <ErpPanel>
        <DocSteps :steps="steps" :current="stepCurrent" :terminal="{ VOIDED: '已作废' }" />
        <el-alert v-if="d.creditWarning || d.prepaymentUnpaid" type="warning" :closable="false" class="gap-b"
                  :title="[d.creditWarning ? '信用预警' : '', d.prepaymentUnpaid ? '出货前款项未收齐' : ''].filter(Boolean).join('；')" />
        <el-descriptions :column="4">
          <el-descriptions-item label="客户">{{ d.customerName }}</el-descriptions-item>
          <el-descriptions-item label="出货通知">
            <el-link type="primary" underline="never" @click="router.push(`/shipping/notice/${d.noticeId}`)">{{ d.noticeNo }}</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="出货日期">{{ d.shipDate }}</el-descriptions-item>
          <el-descriptions-item label="运输方式"><DictTag type="shp_transport_mode" :value="d.transportMode" /></el-descriptions-item>
          <el-descriptions-item label="出货仓">{{ d.warehouseName }}</el-descriptions-item>
          <el-descriptions-item label="出库单">
            <el-link v-if="d.stockOutId" type="primary" underline="never" @click="router.push(`/inventory/stock-out/${d.stockOutId}`)">查看</el-link><span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="金额">
            {{ d.priceVisible ? `${d.currency} ${formatAmount(d.totalAmount)}（汇率 ${d.exchangeRate ?? '-'}）` : '***' }}
          </el-descriptions-item>
          <el-descriptions-item label="数量 / 箱数">{{ formatQty(d.totalQty) }} / {{ d.cartonCount ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="毛重 / 净重 / CBM">{{ d.grossWeight ?? '-' }} / {{ d.netWeight ?? '-' }} / {{ d.cbm ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="贸易条款">{{ d.tradeTerm || '-' }}</el-descriptions-item>
          <el-descriptions-item label="港口">{{ d.portOfLoading || '-' }} → {{ d.portOfDestination || '-' }}</el-descriptions-item>
          <el-descriptions-item label="货代">{{ d.forwarderName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="柜号 / 封条">{{ d.containerNo || '-' }} / {{ d.sealNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提单">{{ d.blNo || '-' }} {{ d.blDate ?? '' }}</el-descriptions-item>
          <el-descriptions-item label="ETD / ETA">{{ d.etd ?? '-' }} / {{ d.eta ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="物流状态">
            <StatusTag v-if="d.logisticsStatus" :value="d.logisticsStatus" :map="LOGISTICS_STATUS" /><span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="签收">{{ d.signedAt ? `${formatDateTime(d.signedAt)} ${d.signedBy ?? ''}` : '-' }}</el-descriptions-item>
          <el-descriptions-item label="单证">
            <el-link v-if="d.packingList" type="primary" underline="never" @click="router.push(`/shipping/packing-list/${d.packingList.id}`)">{{ d.packingList.no }}</el-link>
            <el-link v-if="d.invoice" type="primary" underline="never" class="gap-l" @click="router.push(`/shipping/invoice/${d.invoice.id}`)">{{ d.invoice.no }}</el-link>
            <el-link v-if="d.customs" type="primary" underline="never" class="gap-l" @click="router.push(`/shipping/customs/${d.customs.id}`)">{{ d.customs.no }}</el-link>
            <span v-if="!d.packingList && !d.invoice && !d.customs">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="船务">{{ d.ownerName }}</el-descriptions-item>
          <el-descriptions-item label="收货地址" :span="3">{{ d.shipToText }}</el-descriptions-item>
          <el-descriptions-item v-if="d.voidReason" label="作废原因" :span="4">{{ d.voidReason }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>
      <ErpPanel flush>
        <el-tabs v-model="tab" class="detail-tabs">
          <el-tab-pane :label="`明细(${d.lines.length})`" name="lines">
            <el-table :data="d.lines">
              <el-table-column prop="lineNo" label="行" width="50" />
              <el-table-column label="订单" width="160">
                <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/sales/order/${row.orderId}`)">{{ row.orderNo }}</el-link></template>
              </el-table-column>
              <el-table-column prop="customerPoNo" label="客户 PO" width="110" />
              <el-table-column label="物料" min-width="180"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
              <el-table-column prop="batchNo" label="批次" width="140" />
              <el-table-column label="数量" width="110" align="right"><template #default="{ row }">{{ formatQty(row.qty) }} {{ row.uom }}</template></el-table-column>
              <el-table-column label="出库数量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.outQty) }}</template></el-table-column>
              <el-table-column label="含税单价" width="100" align="right"><template #default="{ row }">{{ d?.priceVisible ? row.priceInclTax : '***' }}</template></el-table-column>
              <el-table-column label="价税合计" width="120" align="right"><template #default="{ row }">{{ d?.priceVisible ? formatAmount(row.totalAmount) : '***' }}</template></el-table-column>
              <el-table-column prop="serialNos" label="序列号" min-width="120" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane v-if="d.cartons.length" :label="`装箱清单(${d.cartons.length})`" name="cartons">
            <el-table :data="d.cartons">
              <el-table-column prop="cartonNo" label="箱号" width="70" />
              <el-table-column label="内容" min-width="260">
                <template #default="{ row }">
                  <div v-for="l in row.lines" :key="l.id">{{ l.materialCode }} {{ l.batchNo ? `批次 ${l.batchNo}` : '' }} × {{ formatQty(l.qty) }}</div>
                </template>
              </el-table-column>
              <el-table-column prop="grossWeightKg" label="毛重" width="80" align="right" />
              <el-table-column prop="netWeightKg" label="净重" width="80" align="right" />
              <el-table-column prop="cbm" label="CBM" width="80" align="right" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`物流记录(${d.events.length})`" name="events">
            <el-table :data="d.events">
              <el-table-column label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.logisticsStatus" :map="LOGISTICS_STATUS" /></template></el-table-column>
              <el-table-column label="时间" width="150"><template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template></el-table-column>
              <el-table-column prop="location" label="地点" width="140" />
              <el-table-column prop="remark" label="备注" min-width="160" />
              <el-table-column prop="operatorName" label="登记人" width="90" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="SHP_SHIPMENT" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="SHP_SHIPMENT" :biz-id="id" :status-map="SHIPMENT_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="SHP_SHIPMENT" :biz-id="id" :editable="s !== 'VOIDED'" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="editDialog" title="编辑出货单" width="520px">
      <el-form label-width="90px">
        <el-form-item label="出货日期" required><el-date-picker v-model="ef.shipDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
        <el-form-item label="货代">
          <el-select v-model="ef.forwarderId" clearable filterable class="w-full">
            <el-option v-for="f in forwarders" :key="f.id" :value="f.id" :label="`${f.code} ${f.name}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="柜号"><el-input v-model="ef.containerNo" maxlength="32" /></el-form-item>
        <el-form-item label="封条号"><el-input v-model="ef.sealNo" maxlength="32" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="ef.remark" type="textarea" :rows="2" maxlength="1000" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
    <LogisticsDialogs ref="dialogs" :forwarders="forwarders" @changed="load" />
  </ErpPage>
</template>

<style scoped>
.gap-b { margin-bottom: var(--erp-space-3); }
.gap-l { margin-left: var(--erp-space-2); }
</style>
