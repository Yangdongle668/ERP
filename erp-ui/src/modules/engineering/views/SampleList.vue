<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import {
  FEEDBACK_STATUS, MAKE_METHOD_OPTIONS, sampleApi, SAMPLE_STATUS, SAMPLE_STATUS_OPTIONS, SAMPLE_TYPE_OPTIONS, type SampleQuery, type SampleRow
} from '../api/sample'
import { labelOf } from '../api/material'

defineOptions({ name: 'EngSampleList' })

/** 样品单列表（需求 05-07 3.1，T1）：默认未关闭、未作废；逾期未寄出标红 */
const router = useRouter()
type Query = Omit<SampleQuery, 'pageNo' | 'pageSize' | 'statuses'> & { statuses?: string[] }
const { query, list, total, loading, load, search, reset } = useListPage<Query, SampleRow>({
  api: (q) => sampleApi.page(toParams(q) as SampleQuery),
  defaultQuery: () => ({ statuses: ['DRAFT', 'PENDING', 'APPROVED', 'MAKING', 'READY', 'SHIPPED', 'FEEDBACK'] }),
  refreshOnActivated: true
})
function toParams(q: Query) {
  const { statuses, ...rest } = q
  return { ...rest, statuses: statuses?.length ? statuses.join(',') : undefined }
}
const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'sampleType', label: '样品类型', type: 'select', options: SAMPLE_TYPE_OPTIONS },
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'statuses', label: '状态', type: 'select', options: SAMPLE_STATUS_OPTIONS, multiple: true }
]
const columns: TableColumn<SampleRow>[] = [
  { prop: 'docNo', label: '单号', width: 150, type: 'link', onClick: (r) => router.push(`/engineering/sample/${r.id}`) },
  { prop: 'sampleType', label: '类型', width: 100, formatter: (r) => labelOf(SAMPLE_TYPE_OPTIONS, r.sampleType) },
  { prop: 'customerName', label: '客户', width: 130 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 160 },
  { prop: 'qty', label: '数量', width: 90, type: 'qty', uomProp: 'uom' },
  { prop: 'requiredDate', label: '要求日期', width: 110, slot: true },
  { prop: 'makeMethod', label: '制作方式', width: 100, formatter: (r) => labelOf(MAKE_METHOD_OPTIONS, r.makeMethod) },
  { prop: 'sampleStatus', label: '状态', width: 80, type: 'status', statusMap: SAMPLE_STATUS },
  { prop: 'feedbackResult', label: '反馈', width: 100, slot: true },
  { prop: 'createdByName', label: '申请人', width: 90 }
]
const asRow = (r: unknown) => r as SampleRow
</script>

<template>
  <ErpPage description="客户样、工程验证样、认证送样：申请 → 审批 → 制作或从库存领取 → 出库寄出 → 客户反馈">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-customerId><CustomerSelect v-model="query.customerId" placeholder="全部" class="w200" /></template>
          <template #field-materialId><MaterialSelect v-model="query.materialId" placeholder="全部" class="w200" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="eng.sample" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'eng:sample:create'" type="primary" icon="Plus" @click="router.push('/engineering/sample/new')">新建样品单</el-button>
        </template>
        <template #col-requiredDate="{ row }">
          <span :class="{ 'text-danger': asRow(row).overdue }">{{ asRow(row).requiredDate }}</span>
        </template>
        <template #col-feedbackResult="{ row }">
          <StatusTag v-if="asRow(row).feedbackResult" :value="asRow(row).feedbackResult" :map="FEEDBACK_STATUS" />
        </template>
        <template #empty>
          <el-button v-perm="'eng:sample:create'" icon="Plus" @click="router.push('/engineering/sample/new')">新建样品单</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
