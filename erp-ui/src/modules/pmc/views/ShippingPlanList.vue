<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { RowAction, SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { addWeeks, isoWeek, joinList, optionsOf, PLAN_STATUS, shippingPlanApi, type ShipPlanRow } from '../api/pmc'

defineOptions({ name: 'PmcShippingPlanList' })

/** 出货计划（需求 06-08 3.1，T1）：按周编制，发布后出货模块据此生成出货通知 */
const router = useRouter()
type Query = { docNo?: string; week?: string; statuses?: string[] }
const { query, list, total, loading, load, search, reset } = useListPage<Query, ShipPlanRow>({
  api: (q) => shippingPlanApi.page({ ...q, statuses: joinList(q.statuses) } as never),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'week', label: '周', placeholder: '如 2026-W40' },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(PLAN_STATUS), multiple: true }
]
const columns: TableColumn<ShipPlanRow>[] = [
  { prop: 'docNo', label: '单号', width: 150, type: 'link', onClick: (r) => router.push(`/pmc/shipping-plan/${r.id}`) },
  { prop: 'planWeek', label: '计划周', width: 100 },
  { prop: 'lineCount', label: '行数', width: 70, align: 'right' },
  { prop: 'planQty', label: '计划数量', width: 110, type: 'qty' },
  { prop: 'noticedLineCount', label: '已通知行', width: 90, align: 'right' },
  { prop: 'planStatus', label: '状态', width: 90, type: 'status', statusMap: PLAN_STATUS },
  { prop: 'publishedAt', label: '发布时间', width: 150, type: 'datetime' },
  { prop: 'ownerName', label: '计划员', width: 90 },
  { prop: 'createdAt', label: '创建时间', width: 150, type: 'datetime' }
]
const rowActions = (r: ShipPlanRow): RowAction[] => [
  { label: '编辑', permission: 'pmc:shipping-plan:update', visible: r.planStatus !== 'CLOSED', handler: () => router.push(`/pmc/shipping-plan/${r.id}`) },
  { label: '发布', permission: 'pmc:shipping-plan:publish', visible: r.planStatus === 'DRAFT', confirm: '发布后出货模块可据此生成出货通知，确定吗？', handler: async () => {
    await shippingPlanApi.publish(r.id)
    ElMessage.success('已发布')
    load()
  } },
  { label: '关闭', permission: 'pmc:shipping-plan:publish', visible: r.planStatus === 'PUBLISHED', confirm: '确定关闭该出货计划吗？', handler: async () => {
    await shippingPlanApi.close(r.id)
    ElMessage.success('已关闭')
    load()
  } }
]
const asRow = (r: unknown) => r as ShipPlanRow

const dlg = ref(false)
const cur = isoWeek(new Date())
const week = ref(cur)
const weekOptions = [0, 1, 2, 3].map((n) => addWeeks(cur, n))
const creating = ref(false)
async function create() {
  creating.value = true
  try {
    const id = await shippingPlanApi.create({ planWeek: week.value, lines: [] })
    await shippingPlanApi.generate(id)
    dlg.value = false
    router.push(`/pmc/shipping-plan/${id}`)
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <ErpPage description="按周确定要出哪些订单、多少数量：计划数量 = min(未出货, 成品可用按交期先后分配)">
    <ErpPanel>
      <template #filter><ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset" /></template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="pmc.shipping-plan" :actions-width="150" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'pmc:shipping-plan:create'" type="primary" icon="Plus" @click="dlg = true">新建出货计划</el-button>
        </template>
        <template #actions="{ row }"><RowActions :actions="rowActions(asRow(row))" /></template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="dlg" title="新建出货计划" width="420px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="计划周">
          <el-select v-model="week" filterable allow-create class="w-full"><el-option v-for="w in weekOptions" :key="w" :value="w" :label="w" /></el-select>
        </el-form-item>
      </el-form>
      <p class="hint">创建后自动生成：承诺交期（无则要求交期）在该周及之前、未出货的订单行。</p>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="create">创建并生成</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.hint { color: var(--erp-color-text-secondary); }
</style>
