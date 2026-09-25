<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { formatQty } from '@/utils/format'
import { joinList, labelOf } from '../api/common'
import {
  ADJUST_SOURCE_OPTIONS, ADJUST_STATUS, ADJUST_STATUS_OPTIONS, adjustApi, PRICE_STATUS, PRICE_STATUS_OPTIONS, priceApi,
  type AdjustQuery, type AdjustRow, type PriceQuery, type PriceRow
} from '../api/price'

defineOptions({ name: 'PurPriceList' })

/** 采购价格（需求 07-02 3.1，T1）：价格表 + 调价单两个页签 */
const router = useRouter()
const tab = ref<'price' | 'adjust'>('price')
const tableRef = ref<{ getVisibleColumns: () => TableColumn[] }>()
const importRef = ref<{ open: () => void }>()

// ---------- 价格表 ----------
type PQuery = Omit<PriceQuery, 'pageNo' | 'pageSize' | 'statuses'> & { statuses?: string[]; dates?: [string, string] }
const toPriceParams = (q: PQuery) => {
  const { statuses, dates, ...rest } = q
  return { ...rest, statuses: joinList(statuses), dateFrom: dates?.[0], dateTo: dates?.[1] }
}
const prices = useListPage<PQuery, PriceRow>({
  api: (q) => priceApi.page(toPriceParams(q) as PriceQuery),
  defaultQuery: () => ({ statuses: ['EFFECTIVE'] })
})
const priceQuery = prices.query

const priceFields: SearchField[] = [
  { prop: 'supplierId', label: '供应商', type: 'slot' },
  { prop: 'keyword', label: '物料', placeholder: '编码/名称' },
  { prop: 'statuses', label: '状态', type: 'select', options: PRICE_STATUS_OPTIONS, multiple: true },
  { prop: 'dates', label: '生效日期', type: 'daterange' }
]

const priceColumns = computed<TableColumn<PriceRow>[]>(() => [
  { prop: 'supplierName', label: '供应商', width: 120 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 160 },
  { prop: 'materialSpec', label: '规格', minWidth: 160 },
  { prop: 'baseUom', label: '单位', width: 60 },
  { prop: 'currency', label: '币别', width: 70 },
  { prop: 'minQty', label: '阶梯', width: 100, align: 'right', formatter: (r) => `≥${formatQty(r.minQty)}` },
  { prop: 'price', label: '不含税单价', width: 110, type: 'price' },
  { prop: 'taxRate', label: '税率', width: 80, type: 'percent' },
  { prop: 'priceInclTax', label: '含税单价', width: 110, type: 'price' },
  { prop: 'effectiveFrom', label: '生效日期', width: 110, type: 'date' },
  { prop: 'effectiveTo', label: '失效日期', width: 110, type: 'date' },
  { prop: 'priceStatus', label: '状态', width: 80, type: 'status', statusMap: PRICE_STATUS },
  { prop: 'adjustNo', label: '来源调价单', width: 160, type: 'link', onClick: (r) => r.adjustId && router.push(`/purchase/price/adjust/${r.adjustId}`) },
  { prop: 'updatedAt', label: '更新时间', width: 150, type: 'datetime', hidden: true }
])

// ---------- 历史价格 ----------
const historyVisible = ref(false)
const historyRows = ref<PriceRow[]>([])
const historyTitle = ref('')
async function openHistory(r: PriceRow) {
  historyTitle.value = `${r.supplierName} / ${r.materialCode} ${r.materialName}`
  historyRows.value = await priceApi.history(r.supplierId, r.materialId)
  historyVisible.value = true
}

// ---------- 调价单 ----------
type AQuery = Omit<AdjustQuery, 'pageNo' | 'pageSize' | 'statuses'> & { statuses?: string[]; dates?: [string, string] }
const adjusts = useListPage<AQuery, AdjustRow>({
  api: (q) => {
    const { statuses, dates, ...rest } = q
    return adjustApi.page({ ...rest, statuses: joinList(statuses), dateFrom: dates?.[0], dateTo: dates?.[1] } as AdjustQuery)
  },
  refreshOnActivated: true
})
const adjustQuery = adjusts.query
const adjustFields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'supplierId', label: '供应商', type: 'slot' },
  { prop: 'statuses', label: '状态', type: 'select', options: ADJUST_STATUS_OPTIONS, multiple: true },
  { prop: 'dates', label: '单据日期', type: 'daterange' }
]
const adjustColumns: TableColumn<AdjustRow>[] = [
  { prop: 'docNo', label: '单号', width: 160, type: 'link', onClick: (r) => router.push(`/purchase/price/adjust/${r.id}`) },
  { prop: 'docDate', label: '日期', width: 110, type: 'date' },
  { prop: 'supplierName', label: '供应商', width: 140 },
  { prop: 'currency', label: '币别', width: 70 },
  { prop: 'adjustReason', label: '调价原因', minWidth: 180 },
  { prop: 'source', label: '来源', width: 90, formatter: (r) => labelOf(ADJUST_SOURCE_OPTIONS, r.source) },
  { prop: 'lineCount', label: '行数', width: 70, align: 'right' },
  { prop: 'maxChangePct', label: '最大涨跌幅', width: 110, type: 'percent' },
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: ADJUST_STATUS },
  { prop: 'ownerName', label: '经办人', width: 90 }
]

const exportColumns = () => (tableRef.value?.getVisibleColumns() ?? []).map((c) => String(c.prop))
const asPrice = (r: unknown) => r as PriceRow
</script>

<template>
  <ErpPage description="供应商 + 物料 + 币别 + 起订量的有效价格；价格只能通过调价单审核生效，不能直接修改">
    <ErpPanel flush>
      <el-tabs v-model="tab" class="list-tabs">
        <el-tab-pane label="价格表" name="price">
          <ErpSearchForm v-model="priceQuery" :fields="priceFields" :loading="prices.loading.value" @search="prices.search" @reset="prices.reset">
            <template #field-supplierId><SupplierSelect v-model="priceQuery.supplierId" /></template>
          </ErpSearchForm>
          <ErpTable ref="tableRef" :columns="priceColumns" :data="prices.list.value" :loading="prices.loading.value" storage-key="pur.price" :actions-width="120"
                    @refresh="prices.load">
            <template #toolbar>
              <el-button v-perm="'pur:price:adjust'" type="primary" icon="Plus" @click="router.push('/purchase/price/adjust/new')">新建调价单</el-button>
            </template>
            <template #toolbar-right>
              <ErpIconButton icon="Upload" tooltip="导入价格（生成草稿调价单）" permission="pur:price:import" @click="importRef?.open()" />
              <ExportButton url="/purchase/prices/export" :params="() => toPriceParams({ ...priceQuery })" :columns="exportColumns" filename="采购价格"
                            permission="pur:price:export" />
            </template>
            <template #actions="{ row }">
              <RowActions :actions="[
                { label: '历史价格', handler: () => openHistory(asPrice(row)) },
                { label: '调价', permission: 'pur:price:adjust', handler: () => router.push({ path: '/purchase/price/adjust/new', query: { supplierId: asPrice(row).supplierId, materialId: asPrice(row).materialId, currency: asPrice(row).currency } }) }
              ]" />
            </template>
          </ErpTable>
          <ErpPagination v-model:page-no="priceQuery.pageNo" v-model:page-size="priceQuery.pageSize" :total="prices.total.value" @change="prices.load" />
        </el-tab-pane>

        <el-tab-pane label="调价单" name="adjust">
          <ErpSearchForm v-model="adjustQuery" :fields="adjustFields" :loading="adjusts.loading.value" @search="adjusts.search" @reset="adjusts.reset">
            <template #field-supplierId><SupplierSelect v-model="adjustQuery.supplierId" /></template>
          </ErpSearchForm>
          <ErpTable :columns="adjustColumns" :data="adjusts.list.value" :loading="adjusts.loading.value" storage-key="pur.price-adjust" @refresh="adjusts.load">
            <template #toolbar>
              <el-button v-perm="'pur:price:adjust'" type="primary" icon="Plus" @click="router.push('/purchase/price/adjust/new')">新建调价单</el-button>
            </template>
          </ErpTable>
          <ErpPagination v-model:page-no="adjustQuery.pageNo" v-model:page-size="adjustQuery.pageSize" :total="adjusts.total.value" @change="adjusts.load" />
        </el-tab-pane>
      </el-tabs>
    </ErpPanel>

    <el-drawer v-model="historyVisible" :title="`历史价格 - ${historyTitle}`" size="720px">
      <el-table :data="historyRows">
        <el-table-column label="生效日期" prop="effectiveFrom" width="110" />
        <el-table-column label="失效日期" prop="effectiveTo" width="110" />
        <el-table-column label="阶梯" width="100" align="right"><template #default="{ row }">≥{{ formatQty(row.minQty) }}</template></el-table-column>
        <el-table-column label="币别" prop="currency" width="70" />
        <el-table-column label="含税单价" prop="priceInclTax" width="110" align="right" />
        <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.priceStatus" :map="PRICE_STATUS" /></template></el-table-column>
        <el-table-column label="调价单" prop="adjustNo" min-width="150" />
        <template #empty><ErpEmpty compact /></template>
      </el-table>
    </el-drawer>

    <ImportDialog ref="importRef" title="导入价格" base="/purchase/price-adjusts" template-name="采购价格" @done="adjusts.load" />
  </ErpPage>
</template>

<style scoped>
.list-tabs { padding: 0 var(--erp-space-5) var(--erp-space-4); }
</style>
