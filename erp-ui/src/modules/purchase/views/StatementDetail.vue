<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction, TableColumn } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatDateTime, formatMoney } from '@/utils/format'
import { submitText } from '../api/common'
import { LINE_TYPE_OPTIONS, STATEMENT_STATUS, statementApi, type StatementDetail, type StatementLine } from '../api/statement'

defineOptions({ name: 'PurStatementDetail' })

/** 对账单详情（需求 07-09 4.3，T5） */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<StatementDetail>()
const activeTab = ref('lines')

async function load() {
  d.value = await statementApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}

const s = computed(() => d.value?.status)
const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'pur:statement:delete', visible: () => s.value === 'DRAFT', confirm: `确定删除对账单「${d.value?.docNo}」吗？删除后不可恢复。`,
    handler: async () => {
      await statementApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/purchase/statement')
    } },
  { key: 'void', label: '作废', permission: 'pur:statement:delete', visible: () => s.value === 'DRAFT', reasonRequired: true, reasonTitle: '作废原因',
    confirm: '作废后单据不可恢复，明细释放后可重新对账。', handler: (reason) => run(statementApi.void(id.value, reason!), '已作废') },
  { key: 'unapprove', label: '反审核', permission: 'pur:statement:update', visible: () => s.value === 'APPROVED', reasonRequired: true, reasonTitle: '反审核原因',
    confirm: '反审核后单据回到草稿状态，可以修改。', handler: (reason) => run(statementApi.unapprove(id.value, reason!), '已反审核') },
  { key: 'unconfirm', label: '取消确认', permission: 'pur:statement:unconfirm', visible: () => s.value === 'COMPLETED', reasonRequired: true, reasonTitle: '取消确认原因',
    confirm: '取消确认后对账单回到“待供应商确认”，财务未付款的应付单同时撤回。', handler: (reason) => run(statementApi.unconfirm(id.value, reason!), '已取消确认') },
  { key: 'edit', label: '编辑', permission: 'pur:statement:update', visible: () => s.value === 'DRAFT', handler: () => router.push(`/purchase/statement/${id.value}/edit`) },
  { key: 'submit', label: '提交', type: 'primary', permission: 'pur:statement:submit', visible: () => s.value === 'DRAFT',
    handler: async () => {
      ElMessage.success(submitText((await statementApi.submit(id.value)).status))
      load()
    } },
  { key: 'confirm', label: '供应商已确认', type: 'primary', permission: 'pur:statement:confirm', visible: () => s.value === 'APPROVED', handler: openConfirm }
])

async function run(p: Promise<unknown>, msg: string) {
  await p
  ElMessage.success(msg)
  load()
}

const steps = [
  { status: 'DRAFT', label: '草稿' },
  { status: 'PENDING_APPROVAL', label: '待审批' },
  { status: 'APPROVED', label: '待供应商确认' },
  { status: 'COMPLETED', label: '已确认' }
]

const columns = computed<TableColumn<StatementLine>[]>(() => [
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'lineType', label: '类型', width: 80, type: 'enum', options: LINE_TYPE_OPTIONS },
  { prop: 'sourceNo', label: '来源单据', width: 150 },
  { prop: 'orderNo', label: '订单号', width: 150 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 150 },
  { prop: 'bizDate', label: '日期', width: 110, type: 'date' },
  { prop: 'qty', label: '数量', width: 100, type: 'qty' },
  ...(d.value?.priceVisible ? [
    { prop: 'priceInclTax', label: '含税单价', width: 100, type: 'price' } as TableColumn<StatementLine>,
    { prop: 'taxRate', label: '税率', width: 90, type: 'percent' } as TableColumn<StatementLine>,
    { prop: 'amount', label: '不含税', width: 110, type: 'amount', summary: true } as TableColumn<StatementLine>,
    { prop: 'taxAmount', label: '税额', width: 100, type: 'amount', summary: true } as TableColumn<StatementLine>,
    { prop: 'totalAmount', label: '价税合计', width: 120, type: 'amount', summary: true } as TableColumn<StatementLine>
  ] : []),
  { prop: 'remark', label: '备注', minWidth: 120 }
])

// ---------- 供应商确认 ----------
const confirmVisible = ref(false)
const confirmer = ref('')
const confirmedAt = ref<string>()
const confirmFiles = ref<string[]>([])
const confirming = ref(false)
function openConfirm() {
  confirmer.value = ''
  confirmedAt.value = undefined
  confirmFiles.value = []
  confirmVisible.value = true
}
async function submitConfirm() {
  if (!confirmer.value.trim()) return ElMessage.warning('请填写供应商确认人')
  confirming.value = true
  try {
    await statementApi.confirm(id.value, { confirmer: confirmer.value.trim(), confirmedAt: confirmedAt.value, fileIds: confirmFiles.value })
    confirmVisible.value = false
    ElMessage.success('已确认，财务将生成应付')
    load()
  } finally {
    confirming.value = false
  }
}

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? '对账单'" :status="d?.status" :status-map="STATEMENT_STATUS" :actions="actions" @back="router.push('/purchase/statement')">
        <template #actions-prefix>
          <ApprovalActions v-if="d" biz-type="PUR_STATEMENT" :biz-id="id" @changed="load" />
          <PrintButton v-if="d && d.status !== 'VOIDED'" biz-type="PUR_STATEMENT" :ids="[id]" permission="pur:statement:print" label="打印对账单" />
        </template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <DocSteps :steps="steps" :current="d.status" :terminal="{ VOIDED: '已作废' }" />
        <el-descriptions :column="4" class="head">
          <el-descriptions-item label="供应商">
            <el-link type="primary" underline="never" @click="router.push(`/purchase/supplier/${d.supplierId}`)">{{ d.supplierName }}</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="区间">{{ d.periodFrom }} ~ {{ d.periodTo }}</el-descriptions-item>
          <el-descriptions-item label="币别">{{ d.currency }}</el-descriptions-item>
          <el-descriptions-item label="采购员">{{ d.ownerName || '-' }}</el-descriptions-item>
          <template v-if="d.priceVisible">
            <el-descriptions-item label="货款">{{ formatMoney(d.goodsAmount, d.currency) }}</el-descriptions-item>
            <el-descriptions-item label="退货"><span class="text-danger">{{ formatMoney(d.returnAmount, d.currency) }}</span></el-descriptions-item>
            <el-descriptions-item label="调整">{{ formatMoney(d.adjustAmount, d.currency) }}</el-descriptions-item>
            <el-descriptions-item label="对账总额"><strong>{{ formatMoney(d.totalAmount, d.currency) }}</strong></el-descriptions-item>
          </template>
          <el-descriptions-item label="供应商确认">
            {{ d.supplierConfirmedAt ? `${d.supplierConfirmer ?? ''} ${formatDateTime(d.supplierConfirmedAt, true)}` : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane :label="`明细(${d.lines.length})`" name="lines"><ErpTable :columns="columns" :data="d.lines" storage-key="pur.statement-lines" /></el-tab-pane>
          <el-tab-pane label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="PUR_STATEMENT" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="PUR_STATEMENT" :biz-id="id" :status-map="STATEMENT_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="PUR_STATEMENT" :biz-id="id" editable /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="confirmVisible" title="供应商已确认" width="640px" :close-on-click-modal="false" append-to-body>
      <el-form label-width="110px">
        <el-form-item label="确认人" required><el-input v-model="confirmer" maxlength="64" placeholder="供应商签字人" /></el-form-item>
        <el-form-item label="确认时间"><el-date-picker v-model="confirmedAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="默认当前时间" /></el-form-item>
        <el-form-item label="签字对账单" required>
          <AttachmentUpload v-model="confirmFiles" biz-type="PUR_STATEMENT" :biz-id="id" multiple />
          <div class="form-tip">请上传供应商签字/盖章的对账单扫描件</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="primary" :loading="confirming" @click="submitConfirm">确认</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.head { margin-top: var(--erp-space-4); }
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
</style>
