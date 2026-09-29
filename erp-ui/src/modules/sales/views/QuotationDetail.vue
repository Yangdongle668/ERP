<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction, TableColumn } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatDateTime, formatMoney, formatQty, formatRate } from '@/utils/format'
import { QUOTE_STATUS, quotationApi, submitText, type QuotationDetail, type QuotationLine } from '../api/sales'

defineOptions({ name: 'SalQuotationDetail' })

/** 报价单详情（需求 04-02 4.3～4.6，T5）：审批、发送、修订、未成交、转订单 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<QuotationDetail>()
const activeTab = ref('lines')

async function load() {
  d.value = await quotationApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.revision > 0 ? `${d.value.docNo} R${d.value.revision}` : d.value.docNo)
}
const s = computed(() => d.value?.quoteStatus)
const live = computed(() => (s.value === 'APPROVED' || s.value === 'SENT') && !d.value?.expired)

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'sales:quotation:delete', visible: () => s.value === 'DRAFT', confirm: `确定删除报价单「${d.value?.docNo}」吗？`,
    handler: async () => {
      await quotationApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/sales/quotation')
    } },
  { key: 'lose', label: '未成交', permission: 'sales:quotation:lose', visible: () => s.value === 'APPROVED' || s.value === 'SENT' || s.value === 'EXPIRED',
    handler: () => {
      loseForm.value = {}
      loseVisible.value = true
    } },
  { key: 'revise', label: '修订', permission: 'sales:quotation:revise', visible: () => ['APPROVED', 'SENT', 'EXPIRED'].includes(s.value ?? ''),
    confirm: '修订会生成新版本草稿，原版本标记为“已修订”，确定吗？', handler: async () => {
      const nid = await quotationApi.revise(id.value)
      ElMessage.success('已生成修订版')
      router.push(`/sales/quotation/${nid}/edit`)
    } },
  { key: 'send', label: '标记已发送', permission: 'sales:quotation:send', visible: () => s.value === 'APPROVED',
    confirm: '确认已将报价单发送给客户（打印或下载 PDF 后发送）？', handler: async () => {
      await quotationApi.send(id.value)
      ElMessage.success('已记录发送')
      load()
    } },
  { key: 'edit', label: '编辑', permission: 'sales:quotation:update', visible: () => s.value === 'DRAFT', handler: () => router.push(`/sales/quotation/${id.value}/edit`) },
  { key: 'submit', label: '提交', type: 'primary', permission: 'sales:quotation:submit', visible: () => s.value === 'DRAFT',
    handler: async () => {
      ElMessage.success(submitText((await quotationApi.submit(id.value)).status))
      load()
    } },
  { key: 'toOrder', label: '转销售订单', type: 'primary', permission: 'sales:quotation:to-order', visible: () => live.value || s.value === 'WON', handler: openToOrder }
])

const lineColumns = computed<TableColumn<QuotationLine>[]>(() => [
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 150 },
  { prop: 'materialSpec', label: '规格', minWidth: 140 },
  { prop: 'customerPartNo', label: '客户料号', width: 110 },
  { prop: 'uom', label: '单位', width: 60 },
  { prop: 'minQty', label: '数量阶梯', width: 100, type: 'qty' },
  { prop: 'price', label: d.value?.taxIncluded ? '含税单价' : '不含税单价', width: 110, type: 'price' },
  { prop: 'taxRate', label: '税率', width: 80, type: 'percent' },
  ...(d.value?.costVisible ? [
    { prop: 'costPrice', label: '单位成本', width: 100, type: 'price' } as TableColumn<QuotationLine>,
    { prop: 'marginRate', label: '毛利率', width: 110, slot: true } as TableColumn<QuotationLine>
  ] : []),
  { prop: 'moq', label: 'MOQ', width: 90, type: 'qty' },
  { prop: 'leadTimeDays', label: '交期(天)', width: 80, align: 'right' },
  { prop: 'toolingFee', label: '模具费', width: 100, type: 'amount' },
  { prop: 'remark', label: '备注', minWidth: 120, hidden: true }
])
const asLine = (r: unknown) => r as QuotationLine

// ---------- 未成交 ----------
const loseVisible = ref(false)
const loseForm = ref<{ lostReason?: string; remark?: string }>({})
async function saveLose() {
  if (!loseForm.value.lostReason) return ElMessage.warning('请选择未成交原因')
  await quotationApi.lose(id.value, loseForm.value.lostReason, loseForm.value.remark?.trim() || undefined)
  loseVisible.value = false
  ElMessage.success('已标记未成交')
  load()
}

// ---------- 转订单 ----------
const orderVisible = ref(false)
const orderRows = ref<{ lineId: string; checked: boolean; materialCode?: string; materialName?: string; uom?: string; minQty?: string; price?: string; qty?: string; requiredDate?: string }[]>([])
const converting = ref(false)
function openToOrder() {
  orderRows.value = (d.value?.lines ?? []).map((l) => ({ lineId: l.id!, checked: true, materialCode: l.materialCode, materialName: l.materialName, uom: l.uom,
    minQty: l.minQty, price: l.price, qty: l.minQty && Number(l.minQty) > 0 ? l.minQty : l.moq }))
  orderVisible.value = true
}
async function saveToOrder() {
  const rows = orderRows.value.filter((r) => r.checked)
  if (!rows.length) return ElMessage.warning('请至少选择一行')
  const bad = rows.find((r) => !(Number(r.qty) > 0))
  if (bad) return ElMessage.warning(`物料 ${bad.materialCode}：请填写订单数量`)
  converting.value = true
  try {
    const oid = await quotationApi.toOrder(id.value, rows.map((r) => ({ quotationLineId: r.lineId, qty: r.qty!, requiredDate: r.requiredDate })))
    orderVisible.value = false
    ElMessage.success('已生成销售订单草稿')
    router.push(`/sales/order/${oid}/edit`)
  } finally {
    converting.value = false
  }
}

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d ? (d.revision > 0 ? `${d.docNo} R${d.revision}` : d.docNo) : '报价单'" :status="d?.quoteStatus" :status-map="QUOTE_STATUS" :actions="actions"
                     @back="router.push('/sales/quotation')">
        <template #extra>
          <ErpBadge v-if="d?.expired && s !== 'EXPIRED'" type="info">已过有效期</ErpBadge>
          <ErpBadge v-if="d?.costVisible && d.belowFloor" type="danger">毛利低于底线</ErpBadge>
        </template>
        <template #actions-prefix>
          <ApprovalActions v-if="d" biz-type="SAL_QUOTATION" :biz-id="id" @changed="load" />
          <PrintButton v-if="d && s !== 'DRAFT'" biz-type="SAL_QUOTATION" :ids="[id]" permission="sales:quotation:print" />
        </template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <el-descriptions :column="4">
          <el-descriptions-item label="客户">{{ d.customerName }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ d.contactName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="报价日期">{{ d.docDate }}</el-descriptions-item>
          <el-descriptions-item label="有效期至"><span :class="{ 'text-muted': d.expired }">{{ d.validUntil }}</span></el-descriptions-item>
          <el-descriptions-item label="币别 / 汇率">{{ d.currency }} / {{ formatRate(d.exchangeRate) }}</el-descriptions-item>
          <el-descriptions-item label="报价金额">{{ formatMoney(d.totalAmount, d.currency) }}</el-descriptions-item>
          <el-descriptions-item label="付款条件">{{ d.paymentTermName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="贸易条款"><DictTag v-if="d.tradeTerm" type="sys_trade_term" :value="d.tradeTerm" /><span v-else>-</span></el-descriptions-item>
          <el-descriptions-item label="单价含税">{{ d.taxIncluded ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="业务员">{{ d.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源 RFQ">
            <el-link v-if="d.rfqId" type="primary" underline="never" @click="router.push(`/sales/rfq/${d.rfqId}`)">{{ d.rfqNo }}</el-link><span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="发送时间">{{ d.sentAt ? formatDateTime(d.sentAt, true) : '未发送' }}</el-descriptions-item>
          <el-descriptions-item v-if="d.costVisible && d.minMarginRate != null" label="最低毛利率">
            <span :class="{ 'text-danger': d.belowFloor }">{{ (Number(d.minMarginRate) * 100).toFixed(2) }}%</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="d.lostReason" label="未成交原因"><DictTag type="sal_quote_lost_reason" :value="d.lostReason" /> {{ d.lostRemark }}</el-descriptions-item>
          <el-descriptions-item label="报价条款" :span="2">{{ d.terms || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane :label="`明细(${d.lines.length})`" name="lines">
            <ErpTable :columns="lineColumns" :data="d.lines" storage-key="sal.quotation-lines">
              <template #col-marginRate="{ row }">
                <span v-if="asLine(row).marginRate != null" :class="{ 'text-danger': asLine(row).belowFloor }">{{ (Number(asLine(row).marginRate) * 100).toFixed(2) }}%</span>
                <span v-else class="text-muted">无成本</span>
              </template>
            </ErpTable>
          </el-tab-pane>
          <el-tab-pane :label="`历史版本(${d.revisions.length})`" name="revisions">
            <el-table :data="d.revisions">
              <el-table-column label="单号" width="200">
                <template #default="{ row }">
                  <el-link type="primary" underline="never" @click="router.push(`/sales/quotation/${row.id}`)">{{ row.docNo }}{{ row.revision > 0 ? ` R${row.revision}` : '' }}</el-link>
                </template>
              </el-table-column>
              <el-table-column prop="docDate" label="报价日期" width="120" />
              <el-table-column label="金额" width="140" align="right"><template #default="{ row }">{{ formatMoney(row.totalAmount, d!.currency) }}</template></el-table-column>
              <el-table-column label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.quoteStatus" :map="QUOTE_STATUS" /></template></el-table-column>
              <el-table-column label="单价" min-width="260">
                <template #default="{ row }">
                  <span v-for="l in row.lines" :key="l.id" class="rev-line">{{ l.materialCode }} {{ formatQty(l.minQty) }}：{{ l.price }}</span>
                </template>
              </el-table-column>
              <template #empty><ErpEmpty compact description="没有其他版本" /></template>
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`关联单据(${d.related.length})`" name="related"><RelatedDocs :docs="d.related" /></el-tab-pane>
          <el-tab-pane label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="SAL_QUOTATION" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="SAL_QUOTATION" :biz-id="id" :status-map="QUOTE_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="SAL_QUOTATION" :biz-id="id" editable /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="loseVisible" title="未成交" width="480px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="原因" required><DictSelect v-model="loseForm.lostReason" type="sal_quote_lost_reason" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="loseForm.remark" type="textarea" :rows="3" maxlength="256" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="loseVisible = false">取消</el-button>
        <el-button type="primary" @click="saveLose">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="orderVisible" title="转销售订单" width="960px" :close-on-click-modal="false" append-to-body>
      <el-table :data="orderRows" max-height="440">
        <el-table-column width="50"><template #default="{ row }"><el-checkbox v-model="row.checked" /></template></el-table-column>
        <el-table-column label="物料" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
        <el-table-column label="数量阶梯" width="100" align="right"><template #default="{ row }">{{ formatQty(row.minQty) }}</template></el-table-column>
        <el-table-column prop="price" label="单价" width="100" align="right" />
        <el-table-column label="订单数量" width="150"><template #default="{ row }"><QtyInput v-model="row.qty" :uom="row.uom" /></template></el-table-column>
        <el-table-column label="要求交期" width="170"><template #default="{ row }"><el-date-picker v-model="row.requiredDate" value-format="YYYY-MM-DD" class="w-full" /></template></el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="orderVisible = false">取消</el-button>
        <el-button type="primary" :loading="converting" @click="saveToOrder">生成订单</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
.rev-line { margin-right: var(--erp-space-3); color: var(--erp-color-text-secondary); }
</style>
