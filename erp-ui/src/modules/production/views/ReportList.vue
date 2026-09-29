<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import type { RowAction, SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { joinList, labelOf, pct, REPORT_KIND_OPTIONS, REPORT_STATUS, reportApi, optionsOf, type ReportRow } from '../api/production'

defineOptions({ name: 'MfgReportList' })

/** 报工记录（需求 09-04 3.1，T1）：按订单、工序、班次、人员查询；草稿可批量审核，已审核可反审核 */
const router = useRouter()

type Query = { docNo?: string; prodOrderNo?: string; materialId?: string; operationSeq?: number; dates?: [string, string]; shift?: string; operatorId?: string; statuses?: string[]; reportKind?: string }
const { query, list, total, loading, selection, load, search, reset, onSelectionChange } = useListPage<Query, ReportRow>({
  api: (q) => {
    const { statuses, dates, ...rest } = q
    return reportApi.page({ ...rest, statuses: joinList(statuses), dateFrom: dates?.[0], dateTo: dates?.[1] } as never)
  },
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'docNo', label: '报工单号', upper: true },
  { prop: 'prodOrderNo', label: '生产订单', upper: true },
  { prop: 'materialId', label: '产品', type: 'slot' },
  { prop: 'operationSeq', label: '工序号', type: 'number' },
  { prop: 'dates', label: '报工日期', type: 'daterange' },
  { prop: 'shift', label: '班次', type: 'dict', dictType: 'mfg_shift' },
  { prop: 'operatorId', label: '作业人员', type: 'user' },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(REPORT_STATUS), multiple: true },
  { prop: 'reportKind', label: '类型', type: 'select', options: REPORT_KIND_OPTIONS }
]
const columns: TableColumn<ReportRow>[] = [
  { prop: 'docNo', label: '报工单号', width: 150, type: 'link', onClick: (r) => router.push(`/production/report/${r.id}`) },
  { prop: 'reportDate', label: '日期', width: 100, type: 'date' },
  { prop: 'shift', label: '班次', width: 70, type: 'dict', dictType: 'mfg_shift' },
  { prop: 'prodOrderNo', label: '生产订单', width: 150, type: 'link', onClick: (r) => router.push(`/production/prod-order/${r.prodOrderId}`) },
  { prop: 'materialCode', label: '产品编码', width: 120 },
  { prop: 'materialName', label: '产品名称', minWidth: 130 },
  { prop: 'operationSeq', label: '工序号', width: 70 },
  { prop: 'operation', label: '工序', width: 100 },
  { prop: 'workCenterName', label: '工作中心', width: 110, hidden: true },
  { prop: 'workOrderNo', label: '工单', width: 140, hidden: true },
  { prop: 'reportKind', label: '类型', width: 80, formatter: (r) => labelOf(REPORT_KIND_OPTIONS, r.reportKind) },
  { prop: 'goodQty', label: '合格', width: 80, type: 'qty', summary: true },
  { prop: 'defectQty', label: '不良', width: 70, type: 'qty', summary: true },
  { prop: 'scrapQty', label: '报废', width: 70, type: 'qty', summary: true },
  { prop: 'yieldRate', label: '良率', width: 70, formatter: (r) => pct(r.yieldRate) },
  { prop: 'workHours', label: '工时', width: 70, type: 'qty', precision: 2, summary: true },
  { prop: 'stdHours', label: '标准工时', width: 80, type: 'qty', precision: 2 },
  { prop: 'efficiency', label: '效率', width: 70, formatter: (r) => pct(r.efficiency) },
  { prop: 'operators', label: '作业人员', width: 140 },
  { prop: 'toolingCode', label: '模具', width: 100, hidden: true },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: REPORT_STATUS }
]
const rowActions = (r: ReportRow): RowAction[] => [
  { label: '审核', permission: 'mfg:report:approve', visible: r.status === 'DRAFT', handler: async () => {
    await reportApi.approve(r.id)
    ElMessage.success('已审核')
    load()
  } },
  { label: '反审核', permission: 'mfg:report:unapprove', visible: r.status === 'APPROVED' && r.reportKind === 'NORMAL',
    confirm: '反审核后扣回工序数量，未确认的倒冲出库单作废，确定吗？', handler: async () => {
      await reportApi.unapprove(r.id)
      ElMessage.success('已反审核')
      load()
    } }
]
const asRow = (r: unknown) => r as ReportRow

async function batchApprove() {
  const ids = selection.value.filter((r) => r.status === 'DRAFT').map((r) => r.id)
  if (!ids.length) return ElMessage.warning('请勾选草稿状态的报工单')
  const r = await reportApi.batchApprove(ids)
  if (r.errors.length) ElNotification({ type: 'warning', title: `成功 ${r.success} 张，失败 ${r.errors.length} 张`, message: r.errors.join('；'), duration: 10000 })
  else ElMessage.success(`已审核 ${r.success} 张`)
  load()
}
</script>

<template>
  <ErpPage description="报工：按工序登记合格、不良、报废和工时；审核后更新工序进度、倒冲物料、触发 IPQC">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" selection storage-key="mfg.report" :actions-width="120"
                @selection-change="onSelectionChange" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'mfg:report:create'" type="primary" icon="Plus" @click="router.push('/production/report/new')">快速报工</el-button>
          <el-button v-perm="'mfg:report:approve'" @click="batchApprove">批量审核</el-button>
        </template>
        <template #actions="{ row }"><RowActions :actions="rowActions(asRow(row))" /></template>
        <template #empty>
          <el-button v-perm="'mfg:report:create'" icon="Plus" @click="router.push('/production/report/new')">快速报工</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
