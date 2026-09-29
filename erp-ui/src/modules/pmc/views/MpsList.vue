<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'
import type { RowAction, SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { addWeeks, isoWeek, joinList, mpsApi, optionsOf, PLAN_STATUS, type MpsRow } from '../api/pmc'

defineOptions({ name: 'PmcMpsList' })

/** MPS 列表（需求 06-02，T1）：每个周期只能有一张已发布的 MPS；发布后 MRP 可按参数取 MPS 计划数量 */
const router = useRouter()
type Query = { docNo?: string; statuses?: string[]; week?: string }
const { query, list, total, loading, load, search, reset } = useListPage<Query, MpsRow>({
  api: (q) => mpsApi.page({ ...q, statuses: joinList(q.statuses) } as never),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(PLAN_STATUS), multiple: true },
  { prop: 'week', label: '包含周', placeholder: '如 2026-W40' }
]
const columns: TableColumn<MpsRow>[] = [
  { prop: 'docNo', label: '单号', width: 150, type: 'link', onClick: (r) => router.push(`/pmc/mps/${r.id}`) },
  { prop: 'title', label: '标题', minWidth: 180 },
  { prop: 'startWeek', label: '开始周', width: 100 },
  { prop: 'endWeek', label: '结束周', width: 100 },
  { prop: 'materialCount', label: '物料数', width: 80, align: 'right' },
  { prop: 'mpsStatus', label: '状态', width: 90, type: 'status', statusMap: PLAN_STATUS },
  { prop: 'publishedAt', label: '发布时间', width: 150, type: 'datetime' },
  { prop: 'ownerName', label: '计划员', width: 90 },
  { prop: 'createdAt', label: '创建时间', width: 150, type: 'datetime' }
]
const rowActions = (r: MpsRow): RowAction[] => [
  { label: '复制', permission: 'pmc:mps:create', handler: async () => {
    const id = await mpsApi.copy(r.id)
    ElMessage.success('已复制为新草稿')
    router.push(`/pmc/mps/${id}`)
  } },
  { label: '删除', permission: 'pmc:mps:update', danger: true, visible: r.mpsStatus === 'DRAFT', confirm: `确定删除「${r.docNo}」吗？`, handler: async () => {
    await mpsApi.remove(r.id)
    ElMessage.success('删除成功')
    load()
  } }
]
const asRow = (r: unknown) => r as MpsRow

// ---------- 新建 ----------
const dlg = ref(false)
const formRef = ref<FormInstance>()
const next = isoWeek(new Date(Date.now() + 7 * 86400000))
const form = ref({ title: '', startWeek: next, endWeek: addWeeks(next, 7) })
const saving = ref(false)
async function create() {
  if (!form.value.title.trim()) return ElMessage.warning('请填写标题')
  saving.value = true
  try {
    const id = await mpsApi.create(form.value)
    dlg.value = false
    router.push(`/pmc/mps/${id}`)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ErpPage description="主生产计划：按周编制成品 / 关键半成品的计划生产数量，平滑生产节奏">
    <ErpPanel>
      <template #filter><ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset" /></template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="pmc.mps" :actions-width="110" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'pmc:mps:create'" type="primary" icon="Plus" @click="dlg = true">新建 MPS</el-button>
        </template>
        <template #actions="{ row }"><RowActions :actions="rowActions(asRow(row))" /></template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="dlg" title="新建 MPS" width="480px" append-to-body>
      <el-form ref="formRef" :model="form" label-width="80px">
        <el-form-item label="标题" required><el-input v-model="form.title" maxlength="128" /></el-form-item>
        <el-form-item label="开始周" required><el-input v-model="form.startWeek" placeholder="2026-W40" /></el-form-item>
        <el-form-item label="结束周" required><el-input v-model="form.endWeek" placeholder="最多 26 周" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">创建并编制</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>
