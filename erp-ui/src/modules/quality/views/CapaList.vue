<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { CAPA_SOURCE, CAPA_STATUS, capaApi, joinList, optionsOf, recordOptions, type CapaRow } from '../api/quality'

defineOptions({ name: 'QcCapaList' })

/** CAPA 列表（需求 10-04 3.1，T1）：当前步骤进度点、到期日超期标红 */
const router = useRouter()
type Query = { docNo?: string; source?: string; leaderId?: string; statuses?: string[]; due?: string[]; overdue?: boolean }
const { query, list, total, loading, load, search, reset } = useListPage<Query, CapaRow>({
  api: (q) => capaApi.page({ ...q, statuses: joinList(q.statuses), dueFrom: q.due?.[0], dueTo: q.due?.[1], due: undefined } as never),
  defaultQuery: () => ({ statuses: ['OPEN', 'VERIFYING'] }),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'source', label: '来源', type: 'select', options: recordOptions(CAPA_SOURCE) },
  { prop: 'leaderId', label: '负责人', type: 'user' },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(CAPA_STATUS), multiple: true },
  { prop: 'due', label: '到期日', type: 'daterange' }
]
const columns: TableColumn<CapaRow>[] = [
  { prop: 'docNo', label: '单号', width: 150, type: 'link', onClick: (r) => router.push(`/quality/capa/${r.id}`) },
  { prop: 'title', label: '标题', minWidth: 220 },
  { prop: 'source', label: '来源', width: 170, slot: true },
  { prop: 'leaderName', label: '负责人', width: 80 },
  { prop: 'currentStep', label: '当前步骤', width: 190, slot: true },
  { prop: 'dueDate', label: '到期日', width: 100, slot: true },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: CAPA_STATUS },
  { prop: 'invalidCount', label: '验证无效', width: 80, align: 'right', hidden: true }
]
const asRow = (r: unknown) => r as CapaRow
function openSource(r: CapaRow) {
  if (r.source === 'NCR' && r.sourceId) router.push(`/quality/ncr/${r.sourceId}`)
  else if (r.source === 'COMPLAINT' && r.sourceId) router.push(`/quality/complaint/${r.sourceId}`)
}
const visible = ref(false)
const form = ref<{ title?: string; source: string; sourceNo?: string; leaderId?: string; teamMembers: string[]; dueDate?: string; materialId?: string }>({ source: 'AUDIT', teamMembers: [] })
function openCreate() {
  const due = new Date(Date.now() + 30 * 86400000)
  form.value = { source: 'AUDIT', teamMembers: [], dueDate: `${due.getFullYear()}-${String(due.getMonth() + 1).padStart(2, '0')}-${String(due.getDate()).padStart(2, '0')}` }
  visible.value = true
}
async function create() {
  if (!form.value.title || !form.value.leaderId || !form.value.dueDate) return ElMessage.warning('请填写标题、负责人和期限')
  const id = await capaApi.save(undefined, form.value)
  visible.value = false
  router.push(`/quality/capa/${id}`)
}
</script>

<template>
  <ErpPage>
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset" />
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="qc.capa" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'qc:capa:create'" type="primary" @click="openCreate">新建 CAPA</el-button>
          <el-checkbox v-model="query.overdue" class="gap-l" @change="search">只看超期</el-checkbox>
        </template>
        <template #col-source="{ row }">
          {{ CAPA_SOURCE[asRow(row).source] }}
          <el-link v-if="asRow(row).sourceNo" type="primary" underline="never" @click="openSource(asRow(row))">{{ asRow(row).sourceNo }}</el-link>
        </template>
        <template #col-currentStep="{ row }">
          <span class="dots">
            <i v-for="n in 8" :key="n" :class="{ done: n < asRow(row).currentStep, cur: n === asRow(row).currentStep }" />
          </span>
          <span class="step">{{ asRow(row).currentStep > 8 ? '完成' : `D${asRow(row).currentStep}` }}</span>
        </template>
        <template #col-dueDate="{ row }"><span :class="{ danger: asRow(row).overdue }">{{ asRow(row).dueDate }}</span></template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="visible" title="新建 CAPA" width="520px" append-to-body :close-on-click-modal="false">
      <el-form label-width="96px">
        <el-form-item label="标题" required><el-input v-model="form.title" maxlength="128" /></el-form-item>
        <el-form-item label="来源">
          <el-radio-group v-model="form.source"><el-radio value="AUDIT">审核</el-radio><el-radio value="OTHER">其他</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item label="来源说明"><el-input v-model="form.sourceNo" maxlength="64" placeholder="如：2026 年内审不符合项 3" /></el-form-item>
        <el-form-item label="物料"><MaterialSelect v-model="form.materialId" /></el-form-item>
        <el-form-item label="负责人" required><UserSelect v-model="form.leaderId" /></el-form-item>
        <el-form-item label="小组成员"><UserSelect v-model="form.teamMembers" multiple /></el-form-item>
        <el-form-item label="完成期限" required><el-date-picker v-model="form.dueDate" value-format="YYYY-MM-DD" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" @click="create">确定</el-button></template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.gap-l { margin-left: var(--erp-space-3); }
.dots { display: inline-flex; gap: var(--erp-space-1); vertical-align: middle; }
.dots i { width: 10px; height: 10px; border-radius: 50%; background: var(--erp-color-border); }
.dots i.done { background: var(--erp-color-success); }
.dots i.cur { background: var(--erp-color-primary); }
.step { margin-left: var(--erp-space-2); }
.danger { color: var(--erp-color-error); }
</style>
