<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { useUserStore } from '@/stores/user'
import { joinList, optionsOf, QUOTE_STATUS, quotationApi, type QuotationRow } from '../api/sales'

defineOptions({ name: 'SalQuotationList' })

/** 报价单列表（需求 04-02 4.1，T1） */
const router = useRouter()
const me = useUserStore()

type Query = { docNo?: string; customerId?: string; ownerId?: string; statuses?: string[]; materialId?: string; valid?: [string, string]; dates?: [string, string] }
const { query, list, total, loading, load, search, reset } = useListPage<Query, QuotationRow>({
  api: (q) => {
    const { statuses, valid, dates, ...rest } = q
    return quotationApi.page({ ...rest, statuses: joinList(statuses), validFrom: valid?.[0], validTo: valid?.[1], dateFrom: dates?.[0], dateTo: dates?.[1] } as never)
  },
  refreshOnActivated: true
})

const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(QUOTE_STATUS), multiple: true },
  { prop: 'ownerId', label: '业务员', type: 'user' },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'valid', label: '有效期至', type: 'daterange' },
  { prop: 'dates', label: '报价日期', type: 'daterange' }
]

const columns: TableColumn<QuotationRow>[] = [
  { prop: 'docNo', label: '单号', width: 170, type: 'link', onClick: (r) => router.push(`/sales/quotation/${r.id}`) },
  { prop: 'revision', label: '版本', width: 70, formatter: (r) => (r.revision > 0 ? `R${r.revision}` : '') },
  { prop: 'customerName', label: '客户', minWidth: 160 },
  { prop: 'currency', label: '币别', width: 70 },
  { prop: 'totalAmount', label: '报价金额', width: 130, type: 'amount', currencyProp: 'currency' },
  ...(me.hasPermission('sales:quotation:cost') ? [{ prop: 'minMarginRate', label: '最低毛利率', width: 110, slot: true } as TableColumn<QuotationRow>] : []),
  { prop: 'validUntil', label: '有效期至', width: 110, slot: true },
  { prop: 'quoteStatus', label: '状态', width: 90, type: 'status', statusMap: QUOTE_STATUS },
  { prop: 'ownerName', label: '业务员', width: 90 },
  { prop: 'docDate', label: '报价日期', width: 110, type: 'date' }
]
const asRow = (r: unknown) => r as QuotationRow
</script>

<template>
  <ErpPage description="向客户报价：审核后发送，客户接受后转销售订单；可修订出新版本，过期自动失效">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-customerId><CustomerSelect v-model="query.customerId" /></template>
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="sal.quotation" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'sales:quotation:create'" type="primary" icon="Plus" @click="router.push('/sales/quotation/new')">新建报价单</el-button>
        </template>
        <template #col-minMarginRate="{ row }">
          <span v-if="asRow(row).minMarginRate != null" :class="{ 'text-danger': asRow(row).lowMargin }">{{ (Number(asRow(row).minMarginRate) * 100).toFixed(2) }}%</span>
          <span v-else class="text-muted">-</span>
        </template>
        <template #col-validUntil="{ row }"><span :class="{ 'text-muted': asRow(row).expired }">{{ asRow(row).validUntil }}</span></template>
        <template #empty>
          <el-button v-perm="'sales:quotation:create'" icon="Plus" @click="router.push('/sales/quotation/new')">新建报价单</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
