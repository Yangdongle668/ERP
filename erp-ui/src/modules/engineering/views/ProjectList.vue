<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { projectApi, PROJECT_STATUS, PROJECT_STATUS_OPTIONS, PROJECT_TYPE_OPTIONS, PRIORITY_OPTIONS, STAGE_OPTIONS, type ProjectQuery, type ProjectRow } from '../api/project'
import { labelOf } from '../api/material'

defineOptions({ name: 'EngProjectList' })

/** 研发项目列表（需求 05-06 3.1，T1）：默认未完成、未取消 */
const router = useRouter()
type Query = Omit<ProjectQuery, 'pageNo' | 'pageSize' | 'statuses'> & { statuses?: string[] }
const { query, list, total, loading, load, search, reset } = useListPage<Query, ProjectRow>({
  api: (q) => projectApi.page(toParams(q) as ProjectQuery),
  defaultQuery: () => ({ statuses: ['PLANNING', 'IN_PROGRESS', 'ON_HOLD'] }),
  refreshOnActivated: true
})
function toParams(q: Query) {
  const { statuses, ...rest } = q
  return { ...rest, statuses: statuses?.length ? statuses.join(',') : undefined }
}
const fields: SearchField[] = [
  { prop: 'docNo', label: '项目编号', upper: true },
  { prop: 'name', label: '项目名称' },
  { prop: 'pmUserId', label: '项目经理', type: 'user' },
  { prop: 'stage', label: '阶段', type: 'select', options: STAGE_OPTIONS },
  { prop: 'statuses', label: '状态', type: 'select', options: PROJECT_STATUS_OPTIONS, multiple: true }
]
const columns: TableColumn<ProjectRow>[] = [
  { prop: 'docNo', label: '项目编号', width: 140, type: 'link', onClick: (r) => router.push(`/engineering/project/${r.id}`) },
  { prop: 'name', label: '项目名称', minWidth: 180 },
  { prop: 'projectType', label: '类型', width: 100, formatter: (r) => labelOf(PROJECT_TYPE_OPTIONS, r.projectType) },
  { prop: 'customerName', label: '客户', width: 130 },
  { prop: 'productCode', label: '产品', width: 130, formatter: (r) => (r.productCode ? `${r.productCode} ${r.productName ?? ''}` : '-') },
  { prop: 'pmName', label: '项目经理', width: 90 },
  { prop: 'stage', label: '阶段', width: 90, formatter: (r) => labelOf(STAGE_OPTIONS, r.stage) },
  { prop: 'progressPct', label: '进度', width: 140, slot: true },
  { prop: 'planEnd', label: '计划结束', width: 110, slot: true },
  { prop: 'priority', label: '优先级', width: 70, formatter: (r) => labelOf(PRIORITY_OPTIONS, r.priority) },
  { prop: 'projectStatus', label: '状态', width: 80, type: 'status', statusMap: PROJECT_STATUS }
]
const asRow = (r: unknown) => r as ProjectRow
</script>

<template>
  <ErpPage description="新产品从概念到量产的阶段、任务、成员与进度；关联样品、BOM、ECN 和认证">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset" />
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="eng.project" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'eng:project:create'" type="primary" icon="Plus" @click="router.push('/engineering/project/new')">新建项目</el-button>
        </template>
        <template #col-progressPct="{ row }">
          <el-progress :percentage="Math.round(Number(asRow(row).progressPct) * 100)" :stroke-width="8" />
        </template>
        <template #col-planEnd="{ row }">
          <span :class="{ 'text-danger': asRow(row).overdue }">{{ asRow(row).planEnd }}</span>
        </template>
        <template #empty>
          <el-button v-perm="'eng:project:create'" icon="Plus" @click="router.push('/engineering/project/new')">新建项目</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
