<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { useUserStore } from '@/stores/user'
import { joinList, labelOf, optionsOf, REQUEST_STATUS, REQUEST_TYPES, requestApi, type RequestRow } from '../api/finance'

defineOptions({ name: 'FinRequestList' })

/** 付款申请列表（需求 12-05 3.1，T1）：快捷筛选待审批、待付款、本周计划付款 */
const router = useRouter()
const me = useUserStore()
type Query = { docNo?: string; supplierId?: string; requestType?: string; statuses?: string[]; dates?: [string, string]; thisWeek?: boolean }
const toParams = (q: Query) => {
  const { statuses, dates, ...rest } = q
  return { ...rest, statuses: joinList(statuses), planFrom: dates?.[0], planTo: dates?.[1] }
}
const { query, list, total, loading, load, search, reset } = useListPage<Query, RequestRow>({
  api: (q) => requestApi.page(toParams(q) as never),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'supplierId', label: '供应商', type: 'slot' },
  { prop: 'requestType', label: '类型', type: 'select', options: REQUEST_TYPES },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(REQUEST_STATUS), multiple: true },
  { prop: 'dates', label: '计划付款日', type: 'daterange' }
]
const columns: TableColumn<RequestRow>[] = [
  { prop: 'docNo', label: '单号', width: 150, type: 'link', onClick: (r) => router.push(`/finance/payment/request/${r.id}`) },
  { prop: 'requestType', label: '类型', width: 100, formatter: (r) => labelOf(REQUEST_TYPES, r.requestType) },
  { prop: 'supplierName', label: '供应商', minWidth: 140 },
  { prop: 'currency', label: '币别', width: 60 },
  { prop: 'amount', label: '申请金额', width: 120, type: 'amount', currencyProp: 'currency' },
  { prop: 'paidAmount', label: '已付', width: 120, type: 'amount', currencyProp: 'currency' },
  { prop: 'planPayDate', label: '计划付款日', width: 110, type: 'date' },
  { prop: 'orderNo', label: '采购订单（预付）', width: 140 },
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: REQUEST_STATUS },
  { prop: 'uninvoicedWarning', label: '未收发票', width: 80, type: 'bool' },
  { prop: 'ownerName', label: '申请人', width: 90 },
  { prop: 'reason', label: '原因', minWidth: 140, hidden: true }
]
function quick(k: 'PENDING' | 'TO_PAY' | 'WEEK') {
  reset()
  if (k === 'WEEK') query.thisWeek = true
  else query.statuses = [k === 'PENDING' ? 'PENDING' : 'TO_PAY']
  search()
}
</script>

<template>
  <ErpPage description="按到期应付或预付款（关联采购订单）申请付款；提交后占用应付可申请金额，审批通过后由出纳付款">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-supplierId><SupplierSelect v-model="query.supplierId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="fin.payment-request" @refresh="load">
        <template #toolbar>
          <el-button v-if="me.hasPermission('fin:payment-request:create')" type="primary" @click="router.push('/finance/payment/request/new')">新建付款申请</el-button>
          <el-button @click="quick('PENDING')">待审批</el-button>
          <el-button @click="quick('TO_PAY')">待付款</el-button>
          <el-button @click="quick('WEEK')">本周计划付款</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
