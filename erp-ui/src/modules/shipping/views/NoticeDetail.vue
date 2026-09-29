<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatAmount, formatDateTime, formatQty } from '@/utils/format'
import { NOTICE_STATUS, noticeApi, OQC_RESULT, PICKING_STATUS, SHIPMENT_STATUS, type NoticeDetail, type PackingView } from '../api/shipping'

defineOptions({ name: 'ShpNoticeDetail' })

/** 出货通知详情（需求 11-01 3.3，T5）：按状态显示反审核、装箱、申请 OQC、生成出货单、关闭 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<NoticeDetail>()
const packing = ref<PackingView>()
const tab = ref('lines')

async function load() {
  d.value = await noticeApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
  packing.value = d.value.noticeStatus === 'DRAFT' || d.value.noticeStatus === 'PENDING' ? undefined : await noticeApi.packing(id.value).catch(() => undefined)
}
onMounted(load)
const s = computed(() => d.value?.noticeStatus)
const closable = computed(() => ['APPROVED', 'PICKING', 'PACKED', 'OQC', 'READY'].includes(s.value ?? ''))

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', type: 'danger', permission: 'shp:notice:delete', visible: () => s.value === 'DRAFT', confirm: '删除后释放订单已通知数量，确定删除吗？',
    handler: async () => { await noticeApi.remove(id.value); ElMessage.success('已删除'); tabs.remove([tabKeyOf(route)]); router.push('/shipping/notice') } },
  { key: 'unapprove', label: '反审核', permission: 'shp:notice:unapprove', visible: () => s.value === 'APPROVED' && !d.value?.pickingStarted,
    confirm: '反审核将作废未开始的拣货单，确定吗？', handler: async () => { await noticeApi.unapprove(id.value); ElMessage.success('已反审核'); load() } },
  { key: 'close', label: '关闭', permission: 'shp:notice:close', visible: () => closable.value, reasonRequired: true, reasonTitle: '关闭原因',
    confirm: '关闭后拣货单作废，未出货数量释放回订单', handler: async (reason) => { await noticeApi.close(id.value, reason!); ElMessage.success('已关闭'); load() } },
  { key: 'pack', label: '装箱', permission: 'shp:packing:pack',
    visible: () => !!d.value?.packingEnabled && ((s.value === 'PICKING' && !!d.value?.pickingDone) || s.value === 'PACKED'),
    handler: () => router.push({ path: '/shipping/packing', query: { noticeId: id.value } }) },
  { key: 'oqc', label: '申请 OQC', permission: 'shp:packing:pack', visible: () => s.value === 'PACKED' && !!d.value?.oqcRequired,
    handler: async () => { await noticeApi.requestOqc(id.value); ElMessage.success('已申请 OQC'); load() } },
  { key: 'edit', label: '编辑', permission: 'shp:notice:update', visible: () => s.value === 'DRAFT', handler: () => router.push(`/shipping/notice/${id.value}/edit`) },
  { key: 'submit', label: '提交', type: 'primary', permission: 'shp:notice:submit', visible: () => s.value === 'DRAFT', handler: submit },
  { key: 'ship', label: '生成出货单', type: 'primary', permission: 'shp:shipment:create', visible: () => !!d.value?.canShip, handler: openGenerate }
])
async function submit() {
  const r = await noticeApi.submit(id.value)
  if (r.warnings.length) ElNotification({ type: 'warning', title: '提交提示', message: r.warnings.join('；'), duration: 8000 })
  ElMessage.success('已提交')
  load()
}

// 生成出货单：启用装箱时按箱勾选（默认全部未出货箱）
const genDialog = ref(false)
const genCartons = ref<string[]>([])
const freeCartons = computed(() => (packing.value?.cartons ?? []).filter((c) => !c.shipmentId))
async function openGenerate() {
  if (!freeCartons.value.length) return generate()
  genCartons.value = freeCartons.value.map((c) => c.id)
  genDialog.value = true
}
async function generate() {
  if (genDialog.value && !genCartons.value.length) return ElMessage.warning('请至少勾选一箱')
  const sid = await noticeApi.generate(id.value, { cartonIds: genDialog.value ? genCartons.value : undefined })
  genDialog.value = false
  ElMessage.success('已生成出货单')
  router.push(`/shipping/shipment/${sid}`)
}
const cartonText = (c: { cartonNo: number; lines: { materialCode?: string; batchNo?: string; qty: string }[] }) =>
  `${c.cartonNo}#  ${c.lines.map((l) => `${l.materialCode ?? ''}${l.batchNo ? ` / ${l.batchNo}` : ''} × ${formatQty(l.qty)}`).join('，')}`

async function openPicking() {
  const p = d.value?.pickings.filter((x) => x.status !== 'CANCELED').pop()
  if (p) router.push(`/shipping/picking/${p.id}`)
  else ElMessageBox.alert('没有有效的拣货单', '拣货')
}
const steps = [
  { status: 'DRAFT', label: '草稿' }, { status: 'APPROVED', label: '已审核' }, { status: 'PICKING', label: '拣货' }, { status: 'PACKED', label: '装箱' },
  { status: 'READY', label: '待出货' }, { status: 'SHIPPED', label: '已出货' }
]
const stepCurrent = computed(() => (s.value === 'PENDING' ? 'DRAFT' : s.value === 'OQC' ? 'PACKED' : s.value))
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? '出货通知'" :status="d?.noticeStatus" :status-map="NOTICE_STATUS" :actions="actions" @back="router.push('/shipping/notice')">
        <template #actions-prefix>
          <ApprovalActions v-if="d" biz-type="SHP_NOTICE" :biz-id="id" @changed="load" />
          <PrintButton v-if="d" biz-type="SHP_NOTICE" :ids="[id]" permission="shp:notice:print" />
        </template>
      </DocPageHeader>
    </template>
    <template v-if="d">
      <ErpPanel>
        <DocSteps :steps="steps" :current="stepCurrent" :terminal="{ CLOSED: '已关闭', VOIDED: '已作废' }" />
        <el-alert v-if="d.creditWarning || d.prepaymentUnpaid" type="warning" :closable="false" class="gap-b"
                  :title="[d.creditWarning ? '信用预警' : '', d.prepaymentUnpaid ? '出货前款项未收齐' : ''].filter(Boolean).join('；')" />
        <el-descriptions :column="4">
          <el-descriptions-item label="客户">{{ d.customerName }}</el-descriptions-item>
          <el-descriptions-item label="出货日期">{{ d.shipDate }}</el-descriptions-item>
          <el-descriptions-item label="运输方式"><DictTag type="shp_transport_mode" :value="d.transportMode" /></el-descriptions-item>
          <el-descriptions-item label="贸易条款"><DictTag v-if="d.tradeTerm" type="sys_trade_term" :value="d.tradeTerm" /><span v-else>-</span></el-descriptions-item>
          <el-descriptions-item label="起运港">{{ d.portOfLoading || '-' }}</el-descriptions-item>
          <el-descriptions-item label="目的港">{{ d.portOfDestination || '-' }}</el-descriptions-item>
          <el-descriptions-item label="出货仓">{{ d.warehouseName }}</el-descriptions-item>
          <el-descriptions-item label="货代">{{ d.forwarderName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="收货地址" :span="2">{{ d.shipToText }}</el-descriptions-item>
          <el-descriptions-item label="通知方">{{ d.notifyParty || '-' }}</el-descriptions-item>
          <el-descriptions-item label="OQC">
            <StatusTag v-if="d.oqcResult" :value="d.oqcResult" :map="OQC_RESULT" /><span v-else>{{ d.oqcRequired ? '需要' : '免检' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="金额">{{ d.priceVisible ? `${d.currency} ${formatAmount(d.totalAmount)}` : '***' }}</el-descriptions-item>
          <el-descriptions-item label="船务">{{ d.ownerName }}</el-descriptions-item>
          <el-descriptions-item label="审核时间">{{ d.approvedAt ? formatDateTime(d.approvedAt) : '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="d.closeReason" label="关闭原因">{{ d.closeReason }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="4"><span class="pre">{{ d.remark || '-' }}</span></el-descriptions-item>
        </el-descriptions>
      </ErpPanel>
      <ErpPanel flush>
        <el-tabs v-model="tab" class="detail-tabs">
          <el-tab-pane :label="`明细(${d.lines.length})`" name="lines">
            <el-table :data="d.lines">
              <el-table-column prop="lineNo" label="行" width="50" />
              <el-table-column label="订单" width="170">
                <template #default="{ row }">
                  <el-link type="primary" underline="never" @click="router.push(`/sales/order/${row.orderId}`)">{{ row.orderNo }}</el-link> 行 {{ row.orderLineNo }}
                </template>
              </el-table-column>
              <el-table-column prop="customerPoNo" label="客户 PO" width="110" />
              <el-table-column label="物料" min-width="180"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
              <el-table-column prop="customerPartNo" label="客户料号" width="110" />
              <el-table-column label="通知数量" width="110" align="right"><template #default="{ row }">{{ formatQty(row.qty) }} {{ row.uom }}</template></el-table-column>
              <el-table-column label="已拣货" width="90" align="right"><template #default="{ row }">{{ formatQty(row.pickedQty) }}</template></el-table-column>
              <el-table-column label="缺货" width="80" align="right"><template #default="{ row }">{{ Number(row.shortageQty) ? formatQty(row.shortageQty) : '' }}</template></el-table-column>
              <el-table-column label="已装箱" width="90" align="right"><template #default="{ row }">{{ formatQty(row.packedQty) }}</template></el-table-column>
              <el-table-column label="已出货" width="90" align="right"><template #default="{ row }">{{ formatQty(row.shippedQty) }}</template></el-table-column>
              <el-table-column label="OQC" width="60"><template #default="{ row }">{{ row.oqcRequired ? '需要' : '' }}</template></el-table-column>
              <el-table-column label="单价" width="90" align="right"><template #default="{ row }">{{ d?.priceVisible ? row.priceInclTax : '***' }}</template></el-table-column>
              <el-table-column label="金额" width="110" align="right"><template #default="{ row }">{{ d?.priceVisible ? formatAmount(row.totalAmount) : '***' }}</template></el-table-column>
              <el-table-column prop="remark" label="备注" min-width="120" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`拣货单(${d.pickings.length})`" name="pickings">
            <el-table :data="d.pickings">
              <el-table-column label="单号" width="170">
                <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/shipping/picking/${row.id}`)">{{ row.docNo }}</el-link></template>
              </el-table-column>
              <el-table-column label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status" :map="PICKING_STATUS" /></template></el-table-column>
              <el-table-column prop="date" label="日期" width="110" />
              <el-table-column label="" min-width="100" />
              <template #empty><ErpEmpty compact :description="d.pickingEnabled ? '审核后生成拣货单' : '未启用拣货单'" /></template>
            </el-table>
            <div v-if="d.pickings.length" class="tab-actions"><el-button v-perm="'shp:picking:query'" link type="primary" @click="openPicking">打开当前拣货单</el-button></div>
          </el-tab-pane>
          <el-tab-pane v-if="packing" :label="`装箱清单(${packing.cartons.length})`" name="cartons">
            <el-table :data="packing.cartons">
              <el-table-column prop="cartonNo" label="箱号" width="70" />
              <el-table-column label="内容" min-width="260">
                <template #default="{ row }">
                  <div v-for="l in row.lines" :key="l.id">{{ l.materialCode }} {{ l.batchNo ? `批次 ${l.batchNo}` : '' }} × {{ formatQty(l.qty) }}</div>
                </template>
              </el-table-column>
              <el-table-column label="尺寸 cm" width="130"><template #default="{ row }">{{ row.lengthCm ? `${row.lengthCm}×${row.widthCm}×${row.heightCm}` : '-' }}</template></el-table-column>
              <el-table-column prop="grossWeightKg" label="毛重" width="80" align="right" />
              <el-table-column prop="netWeightKg" label="净重" width="80" align="right" />
              <el-table-column prop="cbm" label="CBM" width="80" align="right" />
              <el-table-column label="出货单" width="150">
                <template #default="{ row }">
                  <el-link v-if="row.shipmentId" type="primary" underline="never" @click="router.push(`/shipping/shipment/${row.shipmentId}`)">{{ row.shipmentNo }}</el-link>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`出货单(${d.shipments.length})`" name="shipments">
            <el-table :data="d.shipments">
              <el-table-column label="单号" width="170">
                <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/shipping/shipment/${row.id}`)">{{ row.docNo }}</el-link></template>
              </el-table-column>
              <el-table-column label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status" :map="SHIPMENT_STATUS" /></template></el-table-column>
              <el-table-column prop="date" label="出货日期" width="110" />
              <el-table-column label="数量" width="110" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
              <el-table-column label="" min-width="100" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="SHP_NOTICE" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="SHP_NOTICE" :biz-id="id" :status-map="NOTICE_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="SHP_NOTICE" :biz-id="id" :editable="s !== 'CLOSED' && s !== 'VOIDED'" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="genDialog" title="生成出货单：选择本次出货的箱" width="640px">
      <el-checkbox-group v-model="genCartons" class="carton-list">
        <el-checkbox v-for="c in freeCartons" :key="c.id" :value="c.id">{{ cartonText(c) }}</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <span class="hint">已选 {{ genCartons.length }} / {{ freeCartons.length }} 箱</span>
        <el-button @click="genDialog = false">取消</el-button>
        <el-button type="primary" @click="generate">生成</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.pre { white-space: pre-wrap; }
.gap-b { margin-bottom: var(--erp-space-3); }
.tab-actions { padding: var(--erp-space-2) 0; }
.carton-list { display: flex; flex-direction: column; gap: var(--erp-space-1); max-height: 420px; overflow: auto; }
.hint { margin-right: var(--erp-space-3); color: var(--erp-color-text-secondary); }
</style>
