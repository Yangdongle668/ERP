<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { DOC_STATUS, HANDLING_OPTIONS, joinList, labelOf, optionsOf, PROGRESS_STATUS, returnApi, type ReturnRow } from '../api/sales'

defineOptions({ name: 'SalReturnList' })

/** 销售退货列表（需求 04-06 3.1，T1） */
const router = useRouter()

type Query = { docNo?: string; customerId?: string; rmaNo?: string; returnReason?: string; handling?: string; statuses?: string[]; materialId?: string; dates?: [string, string] }
const { query, list, total, loading, load, search, reset } = useListPage<Query, ReturnRow>({
  api: (q) => {
    const { statuses, dates, ...rest } = q
    return returnApi.page({ ...rest, statuses: joinList(statuses), dateFrom: dates?.[0], dateTo: dates?.[1] } as never)
  },
  refreshOnActivated: true
})

const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'rmaNo', label: 'RMA 号' },
  { prop: 'returnReason', label: '退货原因', type: 'dict', dictType: 'sal_return_reason' },
  { prop: 'handling', label: '处理方式', type: 'select', options: HANDLING_OPTIONS },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(DOC_STATUS), multiple: true },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'dates', label: '单据日期', type: 'daterange' }
]

const columns: TableColumn<ReturnRow>[] = [
  { prop: 'docNo', label: '单号', width: 160, type: 'link', onClick: (r) => router.push(`/sales/return/${r.id}`) },
  { prop: 'customerName', label: '客户', minWidth: 150 },
  { prop: 'rmaNo', label: 'RMA 号', width: 120 },
  { prop: 'returnReason', label: '退货原因', width: 110, type: 'dict', dictType: 'sal_return_reason' },
  { prop: 'handling', label: '处理方式', width: 100, formatter: (r) => labelOf(HANDLING_OPTIONS, r.handling) },
  { prop: 'materialSummary', label: '物料', minWidth: 180 },
  { prop: 'totalQty', label: '数量', width: 100, type: 'qty' },
  { prop: 'totalAmount', label: '金额', width: 120, type: 'amount', currencyProp: 'currency' },
  { prop: 'receiveStatus', label: '收货', width: 80, type: 'status', statusMap: PROGRESS_STATUS },
  { prop: 'judgeStatus', label: '判定', width: 80, type: 'status', statusMap: PROGRESS_STATUS },
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: DOC_STATUS },
  { prop: 'ownerName', label: '业务员', width: 90 },
  { prop: 'docDate', label: '单据日期', width: 110, type: 'date' }
]
</script>

<template>
  <ErpPage description="客户退货：审核后生成退货入库单，仓库收货、品质判定后回写订单并通知财务">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-customerId><CustomerSelect v-model="query.customerId" /></template>
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="sal.return" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'sales:return:create'" type="primary" icon="Plus" @click="router.push('/sales/return/new')">新建退货</el-button>
        </template>
        <template #empty>
          <el-button v-perm="'sales:return:create'" icon="Plus" @click="router.push('/sales/return/new')">新建退货</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
