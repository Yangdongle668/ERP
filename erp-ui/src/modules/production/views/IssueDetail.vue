<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction, TableColumn } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatDateTime, formatQty } from '@/utils/format'
import { ISSUE_TYPE_OPTIONS, issueApi, labelOf, MATERIAL_DOC_STATUS, type IssueDetail, type IssueLine } from '../api/production'

defineOptions({ name: 'MfgIssueDetail' })

/** 领料单详情（需求 09-03 3.3，T5）：提交后生成出库单；出库单未确认前可撤回 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<IssueDetail>()

async function load() {
  d.value = await issueApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}
const s = computed(() => d.value?.status)
const manual = computed(() => d.value?.issueType !== 'BACKFLUSH')

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'mfg:issue:delete', visible: () => s.value === 'DRAFT', confirm: `确定删除领料单「${d.value?.docNo}」吗？`,
    handler: async () => {
      await issueApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/production/issue')
    } },
  { key: 'withdraw', label: '撤回', permission: 'mfg:issue:submit', visible: () => manual.value && (s.value === 'PENDING_APPROVAL' || s.value === 'APPROVED'),
    confirm: '撤回后作废已生成的出库单（仓库未确认时），领料单回到草稿，确定吗？', handler: async () => {
      await issueApi.withdraw(id.value)
      ElMessage.success('已撤回')
      load()
    } },
  { key: 'edit', label: '编辑', permission: 'mfg:issue:update', visible: () => s.value === 'DRAFT' && d.value?.issueType === 'NORMAL',
    handler: () => router.push(`/production/issue/${id.value}/edit`) },
  { key: 'submit', label: '提交', type: 'primary', permission: 'mfg:issue:submit', visible: () => s.value === 'DRAFT', handler: async () => {
    const r = await issueApi.submit(id.value)
    ElMessage.success(r.status === 'PENDING_APPROVAL' ? '已提交，等待审批' : '已提交，等待仓库确认出库')
    load()
  } }
])

const columns: TableColumn<IssueLine>[] = [
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'code', label: '物料编码', width: 130 },
  { prop: 'name', label: '名称', minWidth: 150 },
  { prop: 'spec', label: '规格', width: 130 },
  { prop: 'requiredQty', label: '应领', width: 90, type: 'qty', uomProp: 'uom' },
  { prop: 'lineIssuedQty', label: '订单已领', width: 90, type: 'qty', uomProp: 'uom' },
  { prop: 'requestQty', label: '申请数量', width: 100, type: 'qty', uomProp: 'uom' },
  { prop: 'issuedQty', label: '实发数量', width: 100, type: 'qty', uomProp: 'uom' },
  { prop: 'uom', label: '单位', width: 60 },
  { prop: 'availableQty', label: '可用库存', width: 100, type: 'qty', uomProp: 'uom', hidden: true },
  { prop: 'remark', label: '备注', minWidth: 120 }
]

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? '领料单'" :status="d?.status" :status-map="MATERIAL_DOC_STATUS" :actions="actions" @back="router.push('/production/issue')">
        <template #extra><ErpBadge v-if="d" :type="d.issueType === 'OVER' ? 'warning' : 'primary'" :dot="false">{{ labelOf(ISSUE_TYPE_OPTIONS, d.issueType) }}</ErpBadge></template>
        <template #actions-prefix>
          <ApprovalActions v-if="d?.issueType === 'OVER'" biz-type="MFG_ISSUE_OVER" :biz-id="id" @changed="load" />
          <PrintButton v-if="d && s !== 'VOIDED'" biz-type="MFG_ISSUE" :ids="[id]" permission="mfg:issue:print" />
        </template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <el-descriptions :column="4">
          <el-descriptions-item label="生产订单">
            <el-link type="primary" underline="never" @click="router.push(`/production/prod-order/${d.prodOrderId}`)">{{ d.prodOrderNo }}</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="产品">{{ d.productCode }} {{ d.productName }}</el-descriptions-item>
          <el-descriptions-item label="发料仓">{{ d.warehouseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="单据日期">{{ d.docDate }}</el-descriptions-item>
          <el-descriptions-item v-if="d.kitQty" label="套数">{{ formatQty(d.kitQty) }}</el-descriptions-item>
          <el-descriptions-item v-if="d.issueType === 'OVER'" label="超领原因"><DictTag type="mfg_over_issue_reason" :value="d.overReason" /> {{ d.overRemark }}</el-descriptions-item>
          <el-descriptions-item v-if="d.reportNo" label="来源报工">{{ d.reportNo }}</el-descriptions-item>
          <el-descriptions-item label="出库单">{{ d.stockOutNos || '-' }}</el-descriptions-item>
          <el-descriptions-item label="领料人">{{ d.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ d.submittedAt ? formatDateTime(d.submittedAt, true) : '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>
      <ErpPanel flush>
        <el-tabs class="detail-tabs">
          <el-tab-pane :label="`明细(${d.lines.length})`"><ErpTable :columns="columns" :data="d.lines" storage-key="mfg.issue-lines" /></el-tab-pane>
          <el-tab-pane v-if="d.issueType === 'OVER'" label="审批记录" lazy><ApprovalTimeline biz-type="MFG_ISSUE_OVER" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" lazy><OperationLogTable biz-type="MFG_ISSUE" :biz-id="id" :status-map="MATERIAL_DOC_STATUS" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
</style>
