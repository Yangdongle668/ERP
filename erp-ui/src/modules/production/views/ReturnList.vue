<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { joinList, labelOf, MATERIAL_DOC_STATUS, optionsOf, RETURN_TYPE_OPTIONS, returnApi, type ReturnRow } from '../api/production'

defineOptions({ name: 'MfgReturnList' })

/** 退料单列表（需求 09-03 3.4，T1）：良品退回原仓，不良退到不良品仓并通知品质 */
const router = useRouter()

type Query = { docNo?: string; prodOrderNo?: string; returnType?: string; statuses?: string[]; warehouseId?: string; materialId?: string; dates?: [string, string] }
const { query, list, total, loading, selection, load, search, reset, onSelectionChange } = useListPage<Query, ReturnRow>({
  api: (q) => {
    const { statuses, dates, ...rest } = q
    return returnApi.page({ ...rest, statuses: joinList(statuses), dateFrom: dates?.[0], dateTo: dates?.[1] } as never)
  },
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'prodOrderNo', label: '生产订单', upper: true },
  { prop: 'returnType', label: '类型', type: 'select', options: RETURN_TYPE_OPTIONS },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(MATERIAL_DOC_STATUS), multiple: true },
  { prop: 'warehouseId', label: '仓库', type: 'slot' },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'dates', label: '单据日期', type: 'daterange' }
]
const columns: TableColumn<ReturnRow>[] = [
  { prop: 'docNo', label: '单号', width: 150, type: 'link', onClick: (r) => router.push(`/production/return/${r.id}`) },
  { prop: 'returnType', label: '类型', width: 90, formatter: (r) => labelOf(RETURN_TYPE_OPTIONS, r.returnType) },
  { prop: 'prodOrderNo', label: '生产订单', width: 150, type: 'link', onClick: (r) => router.push(`/production/prod-order/${r.prodOrderId}`) },
  { prop: 'productCode', label: '产品编码', width: 120 },
  { prop: 'productName', label: '产品名称', minWidth: 140 },
  { prop: 'warehouseName', label: '退入仓', width: 110 },
  { prop: 'lineCount', label: '行数', width: 60, align: 'right' },
  { prop: 'totalQty', label: '退料数量', width: 100, type: 'qty' },
  { prop: 'stockInNos', label: '入库单', width: 150 },
  { prop: 'status', label: '状态', width: 100, type: 'status', statusMap: MATERIAL_DOC_STATUS },
  { prop: 'ownerName', label: '退料人', width: 90 },
  { prop: 'docDate', label: '单据日期', width: 110, type: 'date' }
]
const printIds = () => selection.value.map((r) => r.id)
</script>

<template>
  <ErpPage description="生产退料：良品退回物料默认仓，不良品退到不良品仓；仓库确认入库后回写已退数量">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-warehouseId><WarehouseSelect v-model="query.warehouseId" :only-mine="false" /></template>
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" selection storage-key="mfg.return" @selection-change="onSelectionChange" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'mfg:return:create'" type="primary" icon="Plus" @click="router.push('/production/return/new')">新建退料单</el-button>
          <PrintButton biz-type="MFG_RETURN" :ids="printIds" permission="mfg:return:print" label="批量打印" />
        </template>
        <template #empty>
          <el-button v-perm="'mfg:return:create'" icon="Plus" @click="router.push('/production/return/new')">新建退料单</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
