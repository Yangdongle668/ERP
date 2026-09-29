<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { SCAR_STATUS, joinList, optionsOf, scarApi, type ScarRow } from '../api/quality'

defineOptions({ name: 'QcScarList' })

/** SCAR 列表（需求 10-06 第 3 节，T1）：回复期限逾期标红 */
const router = useRouter()
type Query = { docNo?: string; supplierId?: string; materialId?: string; statuses?: string[]; overdue?: boolean }
const { query, list, total, loading, load, search, reset } = useListPage<Query, ScarRow>({
  api: (q) => scarApi.page({ ...q, statuses: joinList(q.statuses) } as never),
  defaultQuery: () => ({ statuses: ['OPEN'] }),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'supplierId', label: '供应商', type: 'slot' },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'statuses', label: '状态', type: 'select', options: [{ value: 'OPEN', label: '未结案' }, ...optionsOf(SCAR_STATUS)], multiple: true }
]
const columns: TableColumn<ScarRow>[] = [
  { prop: 'docNo', label: '单号', width: 150, type: 'link', onClick: (r) => router.push(`/quality/scar/${r.id}`) },
  { prop: 'supplierName', label: '供应商', width: 150 },
  { prop: 'material', label: '物料', minWidth: 160, formatter: (r) => `${r.materialCode ?? ''} ${r.materialName ?? ''}` },
  { prop: 'problemSummary', label: '问题摘要', minWidth: 220 },
  { prop: 'sentAt', label: '发出时间', width: 140, type: 'datetime' },
  { prop: 'replyDueDate', label: '回复期限', width: 100, slot: true },
  { prop: 'repliedAt', label: '回复时间', width: 140, type: 'datetime' },
  { prop: 'invalidCount', label: '无效次数', width: 80, align: 'right', hidden: true },
  { prop: 'ncrNo', label: 'NCR', width: 150 },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: SCAR_STATUS },
  { prop: 'ownerName', label: 'SQE', width: 80 }
]
const asRow = (r: unknown) => r as ScarRow
const visible = ref(false)
const form = ref<{ supplierId?: string; materialId?: string; batchNo?: string; problemDescription?: string; requirement?: string; replyDueDate?: string; fileIds: string[] }>({ fileIds: [] })
function openCreate() {
  form.value = { fileIds: [], requirement: '请于 7 天内提交 8D 改善报告：临时围堵措施、根本原因分析、永久纠正措施及实施计划。' }
  visible.value = true
}
async function create() {
  if (!form.value.supplierId || !form.value.materialId || !form.value.problemDescription) return ElMessage.warning('请填写供应商、物料和问题描述')
  const id = await scarApi.save(undefined, form.value)
  visible.value = false
  router.push(`/quality/scar/${id}`)
}
</script>

<template>
  <ErpPage>
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-supplierId><SupplierSelect v-model="query.supplierId" /></template>
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="qc.scar" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'qc:scar:create'" type="primary" @click="openCreate">新建 SCAR</el-button>
          <el-checkbox v-model="query.overdue" class="gap-l" @change="search">只看逾期未回复</el-checkbox>
        </template>
        <template #col-replyDueDate="{ row }"><span :class="{ danger: asRow(row).replyOverdue }">{{ asRow(row).replyDueDate ?? '-' }}</span></template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
    <el-dialog v-model="visible" title="新建 SCAR" width="620px" append-to-body :close-on-click-modal="false">
      <el-form label-width="96px">
        <el-form-item label="供应商" required><SupplierSelect v-model="form.supplierId" /></el-form-item>
        <el-form-item label="物料" required><MaterialSelect v-model="form.materialId" /></el-form-item>
        <el-form-item label="批次"><el-input v-model="form.batchNo" maxlength="64" /></el-form-item>
        <el-form-item label="问题描述" required><el-input v-model="form.problemDescription" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="要求" required><el-input v-model="form.requirement" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="回复期限"><el-date-picker v-model="form.replyDueDate" value-format="YYYY-MM-DD" placeholder="默认发出日 + 参数天数" /></el-form-item>
        <el-form-item label="附件"><AttachmentUpload v-model="form.fileIds" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" @click="create">确定</el-button></template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.gap-l { margin-left: var(--erp-space-3); }
.danger { color: var(--erp-color-error); }
</style>
