<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { FORECAST_STATUS, forecastApi, joinList, optionsOf, type ForecastRow } from '../api/sales'

defineOptions({ name: 'SalForecastList' })

/** 销售预测列表（需求 04-05 3.1，T1） */
const router = useRouter()

type Query = { keyword?: string; statuses?: string[]; period?: string; ownerId?: string; materialId?: string }
const { query, list, total, loading, load, search, reset } = useListPage<Query, ForecastRow>({
  api: (q) => forecastApi.page({ ...q, statuses: joinList(q.statuses), period: q.period?.replace('-', '') } as never),
  refreshOnActivated: true
})

const fields: SearchField[] = [
  { prop: 'keyword', label: '单号/标题' },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(FORECAST_STATUS), multiple: true },
  { prop: 'period', label: '包含月份', type: 'slot' },
  { prop: 'ownerId', label: '负责人', type: 'user' },
  { prop: 'materialId', label: '物料', type: 'slot' }
]
const fmtPeriod = (p: string) => (p?.length === 6 ? `${p.slice(0, 4)}-${p.slice(4)}` : p)

const columns: TableColumn<ForecastRow>[] = [
  { prop: 'docNo', label: '单号', width: 150, type: 'link', onClick: (r) => router.push(`/sales/forecast/${r.id}`) },
  { prop: 'title', label: '标题', minWidth: 200 },
  { prop: 'startPeriod', label: '期间', width: 160, formatter: (r) => `${fmtPeriod(r.startPeriod)} ~ ${fmtPeriod(r.endPeriod)}` },
  { prop: 'lineCount', label: '物料数', width: 80, align: 'right' },
  { prop: 'totalQty', label: '预测数量', width: 120, type: 'qty' },
  { prop: 'consumedQty', label: '已冲销', width: 120, type: 'qty' },
  { prop: 'consumeRate', label: '冲销率', width: 90, type: 'percent' },
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: FORECAST_STATUS },
  { prop: 'ownerName', label: '负责人', width: 90 },
  { prop: 'publishedAt', label: '发布时间', width: 150, type: 'datetime' }
]
</script>

<template>
  <ErpPage description="按月录入客户/物料预测；发布后供 PMC 计算需求，销售订单审核时按冲销窗口自动冲减">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-period><el-date-picker v-model="query.period" type="month" value-format="YYYY-MM" class="w-full" /></template>
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="sal.forecast" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'sales:forecast:create'" type="primary" icon="Plus" @click="router.push('/sales/forecast/new')">新建预测</el-button>
        </template>
        <template #empty>
          <el-button v-perm="'sales:forecast:create'" icon="Plus" @click="router.push('/sales/forecast/new')">新建预测</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
