<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatDateTime, formatQty } from '@/utils/format'
import { DISPOSITION_STATUS, labelOf, REPORT_KIND_OPTIONS, REPORT_STATUS, reportApi, type ReportDetail } from '../api/production'

defineOptions({ name: 'MfgReportDetail' })

/** 报工单详情（需求 09-04 3.3，T5）：审核 / 反审核；显示不良明细处置情况与倒冲领料单 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<ReportDetail>()

async function load() {
  d.value = await reportApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}
const s = computed(() => d.value?.status)
const normal = computed(() => d.value?.reportKind === 'NORMAL')

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'mfg:report:delete', visible: () => s.value === 'DRAFT', confirm: `确定删除报工单「${d.value?.docNo}」吗？`,
    handler: async () => {
      await reportApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/production/report')
    } },
  { key: 'unapprove', label: '反审核', permission: 'mfg:report:unapprove', visible: () => s.value === 'APPROVED' && normal.value,
    confirm: '反审核后扣回工序数量，未确认的倒冲出库单作废，确定吗？', handler: async () => {
      await reportApi.unapprove(id.value)
      ElMessage.success('已反审核')
      load()
    } },
  { key: 'edit', label: '编辑', permission: 'mfg:report:update', visible: () => s.value === 'DRAFT' && normal.value,
    handler: () => router.push(`/production/report/${id.value}/edit`) },
  { key: 'approve', label: '审核', type: 'primary', permission: 'mfg:report:approve', visible: () => s.value === 'DRAFT', handler: async () => {
    await reportApi.approve(id.value)
    ElMessage.success('已审核')
    load()
  } }
])

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? '报工单'" :status="d?.status" :status-map="REPORT_STATUS" :actions="actions" @back="router.push('/production/report')">
        <template #extra><ErpBadge v-if="d && !normal" type="warning" :dot="false">{{ labelOf(REPORT_KIND_OPTIONS, d.reportKind) }}</ErpBadge></template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <el-descriptions :column="4">
          <el-descriptions-item label="生产订单">
            <el-link type="primary" underline="never" @click="router.push(`/production/prod-order/${d.prodOrderId}`)">{{ d.prodOrderNo }}</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="产品">{{ d.materialCode }} {{ d.materialName }}</el-descriptions-item>
          <el-descriptions-item label="工序">{{ d.operationSeq }} {{ d.operation }}</el-descriptions-item>
          <el-descriptions-item label="工作中心">{{ d.workCenterName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="工单">{{ d.workOrderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="报工日期">{{ d.reportDate }} <DictTag v-if="d.shift" type="mfg_shift" :value="d.shift" /></el-descriptions-item>
          <el-descriptions-item label="合格 / 不良 / 报废">
            <span class="num">{{ formatQty(d.goodQty) }} / {{ formatQty(d.defectQty) }} / {{ formatQty(d.scrapQty) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="报废原因"><DictTag v-if="d.scrapReason" type="mfg_scrap_reason" :value="d.scrapReason" /><span v-else>-</span></el-descriptions-item>
          <el-descriptions-item label="工时">{{ formatQty(d.workHours, 2) }} h{{ d.machineHours ? `（机器 ${formatQty(d.machineHours, 2)} h）` : '' }}</el-descriptions-item>
          <el-descriptions-item label="模具">{{ d.toolingCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="批号">{{ d.batchNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审核">{{ d.approvedAt ? `${d.approvedByName ?? ''} ${formatDateTime(d.approvedAt, true)}` : '-' }}</el-descriptions-item>
          <el-descriptions-item label="倒冲领料">{{ d.backflushNos.length ? d.backflushNos.join('、') : '-' }}</el-descriptions-item>
          <el-descriptions-item label="登记人">{{ d.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>
      <ErpPanel flush>
        <el-tabs class="detail-tabs">
          <el-tab-pane :label="`不良明细(${d.defects.length})`">
            <el-table :data="d.defects">
              <el-table-column label="不良代码" width="140"><template #default="{ row }"><DictTag type="mfg_defect_code" :value="row.defectCode" /></template></el-table-column>
              <el-table-column label="数量" width="90" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
              <el-table-column prop="position" label="位置" width="120" />
              <el-table-column prop="description" label="描述" min-width="180" />
              <el-table-column label="返修合格" width="90" align="right"><template #default="{ row }">{{ formatQty(row.repairedQty) }}</template></el-table-column>
              <el-table-column label="报废" width="80" align="right"><template #default="{ row }">{{ formatQty(row.scrappedQty) }}</template></el-table-column>
              <el-table-column prop="ncrNo" label="NCR" width="130" />
              <el-table-column label="处置" width="90"><template #default="{ row }"><StatusTag :value="row.disposition" :map="DISPOSITION_STATUS" /></template></el-table-column>
              <template #empty><ErpEmpty compact description="没有不良明细" /></template>
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`作业人员(${d.operators.length})`">
            <el-table :data="d.operators">
              <el-table-column label="人员" min-width="160"><template #default="{ row }">{{ row.userName || row.operatorName }}</template></el-table-column>
              <el-table-column label="工时(h)" width="120" align="right"><template #default="{ row }">{{ formatQty(row.hours, 2) }}</template></el-table-column>
              <template #empty><ErpEmpty compact description="没有登记作业人员" /></template>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="操作日志" lazy><OperationLogTable biz-type="MFG_REPORT" :biz-id="id" :status-map="REPORT_STATUS" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
</style>
