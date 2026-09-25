<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { joinList } from '../api/common'
import { RFQ_STATUS, RFQ_STATUS_OPTIONS, rfqApi, type RfqQuery, type RfqRow } from '../api/rfq'

defineOptions({ name: 'PurRfqList' })

/** 询价单列表（需求 07-04 3.1，T1） */
const router = useRouter()

type Query = Omit<RfqQuery, 'pageNo' | 'pageSize' | 'statuses'> & { statuses?: string[]; deadline?: [string, string] }
const { query, list, total, loading, load, search, reset } = useListPage<Query, RfqRow>({
  api: (q) => {
    const { statuses, deadline, ...rest } = q
    return rfqApi.page({ ...rest, statuses: joinList(statuses), deadlineFrom: deadline?.[0], deadlineTo: deadline?.[1] } as RfqQuery)
  },
  refreshOnActivated: true
})

const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'title', label: '标题' },
  { prop: 'statuses', label: '状态', type: 'select', options: RFQ_STATUS_OPTIONS, multiple: true },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'supplierId', label: '供应商', type: 'slot' },
  { prop: 'deadline', label: '截止日期', type: 'daterange' }
]

const columns: TableColumn<RfqRow>[] = [
  { prop: 'docNo', label: '单号', width: 160, type: 'link', onClick: (r) => router.push(`/purchase/rfq/${r.id}`) },
  { prop: 'title', label: '标题', minWidth: 200 },
  { prop: 'currency', label: '币别', width: 70 },
  { prop: 'materialCount', label: '物料数', width: 80, align: 'right' },
  { prop: 'supplierCount', label: '供应商数', width: 90, align: 'right' },
  { prop: 'quotedCount', label: '已报价', width: 80, align: 'right' },
  { prop: 'quoteDeadline', label: '截止日期', width: 110, slot: true },
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: RFQ_STATUS },
  { prop: 'ownerName', label: '采购员', width: 90 },
  { prop: 'docDate', label: '单据日期', width: 110, type: 'date' }
]
const asRow = (r: unknown) => r as RfqRow
</script>

<template>
  <ErpPage description="向多家供应商询价、录入报价并比价；定标后按中标供应商生成草稿调价单">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
          <template #field-supplierId><SupplierSelect v-model="query.supplierId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="pur.rfq" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'pur:rfq:create'" type="primary" icon="Plus" @click="router.push('/purchase/rfq/new')">新建询价单</el-button>
        </template>
        <template #col-quoteDeadline="{ row }"><span :class="{ 'text-danger': asRow(row).overdue }">{{ asRow(row).quoteDeadline }}</span></template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
