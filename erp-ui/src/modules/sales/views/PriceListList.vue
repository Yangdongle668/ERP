<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { joinList, labelOf, optionsOf, PRICE_LIST_STATUS, priceListApi, SCOPE_OPTIONS, type PriceListRow } from '../api/sales'

defineOptions({ name: 'SalPriceListList' })

/** 销售价格表列表（需求 04-01 3.1，T1） */
const router = useRouter()
const tableRef = ref<{ getVisibleColumns: () => TableColumn[] }>()

type Query = { keyword?: string; scope?: string; customerId?: string; customerLevel?: string; currency?: string; statuses?: string[]; materialId?: string; effectiveOn?: string }
const toParams = (q: Query) => ({ ...q, statuses: joinList(q.statuses) })
const { query, list, total, loading, load, search, reset } = useListPage<Query, PriceListRow>({
  api: (q) => priceListApi.page(toParams(q) as never),
  defaultQuery: () => ({ statuses: ['DRAFT', 'PENDING_APPROVAL', 'APPROVED'] }),
  refreshOnActivated: true
})

const fields: SearchField[] = [
  { prop: 'keyword', label: '单号/名称' },
  { prop: 'scope', label: '适用范围', type: 'select', options: SCOPE_OPTIONS },
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'customerLevel', label: '客户等级', type: 'dict', dictType: 'crm_customer_level' },
  { prop: 'currency', label: '币别', type: 'currency' },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(PRICE_LIST_STATUS, ['IN_PROGRESS', 'COMPLETED']), multiple: true },
  { prop: 'materialId', label: '包含物料', type: 'slot' },
  { prop: 'effectiveOn', label: '有效日期', type: 'date' }
]

const columns: TableColumn<PriceListRow>[] = [
  { prop: 'docNo', label: '编号', width: 140, type: 'link', onClick: (r) => router.push(`/sales/price-list/${r.id}`) },
  { prop: 'name', label: '名称', minWidth: 180 },
  { prop: 'scope', label: '适用范围', width: 100, formatter: (r) => labelOf(SCOPE_OPTIONS, r.scope) },
  { prop: 'customerName', label: '客户 / 等级', width: 160, slot: true },
  { prop: 'currency', label: '币别', width: 70 },
  { prop: 'taxIncluded', label: '含税', width: 70, type: 'bool' },
  { prop: 'effectiveFrom', label: '生效日期', width: 110, type: 'date' },
  { prop: 'effectiveTo', label: '失效日期', width: 110, formatter: (r) => r.effectiveTo ?? '长期' },
  { prop: 'itemCount', label: '物料数', width: 80, align: 'right' },
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: PRICE_LIST_STATUS },
  { prop: 'ownerName', label: '创建人', width: 90 }
]
const asRow = (r: unknown) => r as PriceListRow
const exportColumns = () => (tableRef.value?.getVisibleColumns() ?? []).map((c) => String(c.prop))
</script>

<template>
  <ErpPage description="按客户、客户等级或全部客户维护销售价格；审核后生效，下单时按“客户专属 → 客户等级 → 通用”自动取价">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-customerId><CustomerSelect v-model="query.customerId" /></template>
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable ref="tableRef" :columns="columns" :data="list" :loading="loading" storage-key="sal.price-list" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'sales:price-list:create'" type="primary" icon="Plus" @click="router.push('/sales/price-list/new')">新建价格表</el-button>
        </template>
        <template #toolbar-right>
          <ExportButton url="/sales/price-lists/export" :params="() => toParams({ ...query })" :columns="exportColumns" filename="销售价格表" permission="sales:price-list:export" />
        </template>
        <template #col-customerName="{ row }">
          <span v-if="asRow(row).scope === 'CUSTOMER'">{{ asRow(row).customerName }}</span>
          <DictTag v-else-if="asRow(row).scope === 'LEVEL'" type="crm_customer_level" :value="asRow(row).customerLevel" />
          <span v-else class="text-muted">全部客户</span>
        </template>
        <template #empty>
          <el-button v-perm="'sales:price-list:create'" icon="Plus" @click="router.push('/sales/price-list/new')">新建价格表</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
