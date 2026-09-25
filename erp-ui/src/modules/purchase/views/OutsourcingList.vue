<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { useUserStore } from '@/stores/user'
import { joinList, num } from '../api/common'
import { OS_STATUS, OS_STATUS_OPTIONS, outsourcingApi, type OsQuery, type OsRow } from '../api/outsourcing'

defineOptions({ name: 'PurOutsourcingList' })

/** 委外单列表（需求 07-07 3.1，T1） */
const router = useRouter()
const me = useUserStore()

type Query = Omit<OsQuery, 'pageNo' | 'pageSize' | 'statuses'> & { statuses?: string[]; required?: [string, string] }
const { query, list, total, loading, load, search, reset } = useListPage<Query, OsRow>({
  api: (q) => {
    const { statuses, required, ...rest } = q
    return outsourcingApi.page({ ...rest, statuses: joinList(statuses), requiredFrom: required?.[0], requiredTo: required?.[1] } as OsQuery)
  },
  refreshOnActivated: true
})

const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'supplierId', label: '加工商', type: 'slot' },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'statuses', label: '状态', type: 'select', options: OS_STATUS_OPTIONS, multiple: true },
  { prop: 'required', label: '要求日期', type: 'daterange' }
]

const columns = computed<TableColumn<OsRow>[]>(() => [
  { prop: 'docNo', label: '单号', width: 160, type: 'link', onClick: (r) => router.push(`/purchase/outsourcing/${r.id}`) },
  { prop: 'supplierName', label: '加工商', width: 130 },
  { prop: 'materialCode', label: '加工物料', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 150 },
  { prop: 'qty', label: '数量', width: 100, type: 'qty' },
  { prop: 'uom', label: '单位', width: 60 },
  ...(me.hasPermission('pur:price:view') ? [{ prop: 'processPrice', label: '加工费单价', width: 110, type: 'price' } as TableColumn<OsRow>] : []),
  { prop: 'requiredDate', label: '要求日期', width: 110, type: 'date' },
  { prop: 'issuePct', label: '发料进度', width: 130, slot: true },
  { prop: 'receivePct', label: '收货进度', width: 130, slot: true },
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: OS_STATUS },
  { prop: 'ownerName', label: '采购员', width: 90 }
])

const asRow = (r: unknown) => r as OsRow
const pct = (v: number) => Math.max(0, Math.min(100, Math.round(v)))
const receivePct = (r: OsRow) => (num(r.qty) > 0 ? pct((num(r.receivedQty) / num(r.qty)) * 100) : 0)
</script>

<template>
  <ErpPage description="委托加工商加工：发料 → 收货检验 → 余料退回 → 核销消耗与超耗">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-supplierId><SupplierSelect v-model="query.supplierId" /></template>
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="pur.outsourcing" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'pur:outsourcing:create'" type="primary" icon="Plus" @click="router.push('/purchase/outsourcing/new')">新建委外单</el-button>
        </template>
        <template #col-issuePct="{ row }"><el-progress :percentage="pct(num(asRow(row).issuePct))" :stroke-width="6" /></template>
        <template #col-receivePct="{ row }"><el-progress :percentage="receivePct(asRow(row))" :stroke-width="6" /></template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
