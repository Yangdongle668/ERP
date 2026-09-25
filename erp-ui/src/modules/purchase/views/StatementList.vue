<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { useUserStore } from '@/stores/user'
import { formatDateTime, toDateString } from '@/utils/format'
import { joinList } from '../api/common'
import { STATEMENT_STATUS, STATEMENT_STATUS_OPTIONS, statementApi, type StatementQuery, type StatementRow } from '../api/statement'

defineOptions({ name: 'PurStatementList' })

/** 对账单列表（需求 07-09 4.1，T1） */
const router = useRouter()
const me = useUserStore()

type Query = Omit<StatementQuery, 'pageNo' | 'pageSize' | 'statuses'> & { statuses?: string[]; period?: [string, string] }
const { query, list, total, loading, load, search, reset } = useListPage<Query, StatementRow>({
  api: (q) => {
    const { statuses, period, ...rest } = q
    return statementApi.page({ ...rest, statuses: joinList(statuses), periodFrom: period?.[0], periodTo: period?.[1] } as StatementQuery)
  },
  refreshOnActivated: true
})

const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'supplierId', label: '供应商', type: 'slot' },
  { prop: 'period', label: '区间', type: 'daterange' },
  { prop: 'statuses', label: '状态', type: 'select', options: STATEMENT_STATUS_OPTIONS, multiple: true },
  { prop: 'ownerId', label: '采购员', type: 'user' }
]

const canPrice = computed(() => me.hasPermission('pur:price:view'))
const columns = computed<TableColumn<StatementRow>[]>(() => [
  { prop: 'docNo', label: '单号', width: 160, type: 'link', onClick: (r) => router.push(`/purchase/statement/${r.id}`) },
  { prop: 'supplierName', label: '供应商', width: 140 },
  { prop: 'period', label: '区间', width: 200, formatter: (r) => `${r.periodFrom} ~ ${r.periodTo}` },
  { prop: 'currency', label: '币别', width: 70 },
  ...(canPrice.value ? [
    { prop: 'goodsAmount', label: '货款', width: 120, type: 'amount', currencyProp: 'currency' } as TableColumn<StatementRow>,
    { prop: 'returnAmount', label: '退货', width: 110, type: 'amount', currencyProp: 'currency' } as TableColumn<StatementRow>,
    { prop: 'adjustAmount', label: '调整', width: 110, type: 'amount', currencyProp: 'currency' } as TableColumn<StatementRow>,
    { prop: 'totalAmount', label: '对账总额', width: 130, type: 'amount', currencyProp: 'currency' } as TableColumn<StatementRow>
  ] : []),
  { prop: 'status', label: '状态', width: 110, type: 'status', statusMap: STATEMENT_STATUS },
  { prop: 'supplierConfirmedAt', label: '供应商确认', width: 140, formatter: (r) => (r.supplierConfirmedAt ? formatDateTime(r.supplierConfirmedAt, true) : '-') },
  { prop: 'ownerName', label: '采购员', width: 90 }
])

// ---------- 批量生成 ----------
function lastMonth(): [string, string] {
  const now = new Date()
  const from = new Date(now.getFullYear(), now.getMonth() - 1, 1)
  const to = new Date(now.getFullYear(), now.getMonth(), 0)
  return [toDateString(from), toDateString(to)]
}
const batchVisible = ref(false)
const batchPeriod = ref<[string, string]>(lastMonth())
const generating = ref(false)
async function batchGenerate() {
  generating.value = true
  try {
    const ids = await statementApi.batchGenerate(batchPeriod.value[0], batchPeriod.value[1])
    batchVisible.value = false
    ElMessage.success(ids.length ? `已生成 ${ids.length} 张草稿对账单` : '区间内没有可对账的数据')
    load()
  } finally {
    generating.value = false
  }
}
</script>

<template>
  <ErpPage description="与供应商核对区间内的货款、退货与加扣款；供应商签字确认后推送财务生成应付">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-supplierId><SupplierSelect v-model="query.supplierId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="pur.statement" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'pur:statement:create'" type="primary" icon="Plus" @click="router.push('/purchase/statement/new')">新建对账</el-button>
          <el-button v-perm="'pur:statement:create'" @click="batchVisible = true">批量生成</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="batchVisible" title="批量生成对账单" width="480px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="区间"><el-date-picker v-model="batchPeriod" type="daterange" value-format="YYYY-MM-DD" /></el-form-item>
      </el-form>
      <p class="form-tip">为区间内有可对账数据的所有供应商各生成一张草稿对账单</p>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="batchGenerate">生成</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>
