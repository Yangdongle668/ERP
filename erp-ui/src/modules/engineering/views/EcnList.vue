<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { download } from '@/api/http'
import { toDateString } from '@/utils/format'
import { ecnApi, ECN_STATUS, ECN_STATUS_OPTIONS, MODE_OPTIONS, type EcnQuery, type EcnRow } from '../api/ecn'
import { labelOf } from '../api/material'

defineOptions({ name: 'EngEcnList' })

/** ECN 列表（需求 05-05 3.1，T1）：默认非关闭、非作废，单据日期近 6 个月 */
const router = useRouter()
type Query = Omit<EcnQuery, 'pageNo' | 'pageSize' | 'statuses' | 'dateFrom' | 'dateTo'> & { statuses?: string[]; dates?: string[] }
function sixMonths(): string[] {
  const from = new Date()
  from.setMonth(from.getMonth() - 6)
  return [toDateString(from), toDateString(new Date())]
}
const { query, list, total, loading, load, search, reset } = useListPage<Query, EcnRow>({
  api: (q) => ecnApi.page(toParams(q) as EcnQuery),
  defaultQuery: () => ({ statuses: ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'IN_PROGRESS'], dates: sixMonths() }),
  refreshOnActivated: true
})
function toParams(q: Query) {
  const { statuses, dates, ...rest } = q
  return { ...rest, statuses: statuses?.length ? statuses.join(',') : undefined, dateFrom: dates?.[0], dateTo: dates?.[1] }
}

const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'title', label: '标题' },
  { prop: 'ecnType', label: '类型', type: 'dict', dictType: 'eng_ecn_type' },
  { prop: 'statuses', label: '状态', type: 'select', options: ECN_STATUS_OPTIONS, multiple: true },
  { prop: 'materialId', label: '涉及物料', type: 'slot' },
  { prop: 'dates', label: '单据日期', type: 'daterange' }
]
const columns: TableColumn<EcnRow>[] = [
  { prop: 'docNo', label: '单号', width: 150, type: 'link', onClick: (r) => router.push(`/engineering/ecn/${r.id}`) },
  { prop: 'title', label: '标题', minWidth: 200 },
  { prop: 'ecnType', label: '类型', width: 100, type: 'dict', dictType: 'eng_ecn_type' },
  { prop: 'reasonType', label: '原因', width: 100, type: 'dict', dictType: 'eng_ecn_reason' },
  { prop: 'urgency', label: '紧急', width: 70, slot: true },
  { prop: 'effectiveMode', label: '生效方式', width: 150, formatter: (r) => labelOf(MODE_OPTIONS, r.effectiveMode) },
  { prop: 'effectiveDate', label: '生效日期', width: 110, type: 'date' },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: ECN_STATUS },
  { prop: 'createdByName', label: '发起人', width: 90 },
  { prop: 'docDate', label: '单据日期', width: 110, type: 'date' }
]

async function remove(r: EcnRow) {
  await ecnApi.remove(r.id)
  ElMessage.success('删除成功')
  load()
}
async function exportList() {
  await download('/engineering/ecns/export', { ...toParams({ ...query }), pageNo: undefined, pageSize: undefined }, 'ECN.xlsx')
}
const asRow = (r: unknown) => r as EcnRow
</script>

<template>
  <ErpPage description="对已审核 BOM 的受控变更：分析影响、审批后生成新 BOM 版本、按生效方式切换默认版本，相关部门确认后关闭">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-materialId><MaterialSelect v-model="query.materialId" placeholder="父件或子件" class="w200" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="eng.ecn" :actions-width="120" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'eng:ecn:create'" type="primary" icon="Plus" @click="router.push('/engineering/ecn/new')">新建 ECN</el-button>
        </template>
        <template #toolbar-right>
          <ErpIconButton icon="Download" tooltip="导出" permission="eng:ecn:query" @click="exportList" />
        </template>
        <template #col-urgency="{ row }">
          <ErpBadge v-if="asRow(row).urgency === 'URGENT'" type="danger">紧急</ErpBadge>
          <span v-else class="text-muted">普通</span>
        </template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '编辑', permission: 'eng:ecn:update', visible: asRow(row).status === 'DRAFT', handler: () => router.push(`/engineering/ecn/${asRow(row).id}/edit`) },
            { label: '删除', permission: 'eng:ecn:delete', danger: true, visible: asRow(row).status === 'DRAFT', confirm: `确定删除 ${asRow(row).docNo} 吗？`, handler: () => remove(asRow(row)) }
          ]" />
        </template>
        <template #empty>
          <el-button v-perm="'eng:ecn:create'" icon="Plus" @click="router.push('/engineering/ecn/new')">新建 ECN</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
