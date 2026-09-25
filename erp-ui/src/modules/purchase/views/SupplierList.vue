<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { joinList } from '../api/common'
import { CERT_EXPIRY_OPTIONS, SUPPLIER_STATUS, SUPPLIER_STATUS_OPTIONS, supplierApi, type SupplierQuery, type SupplierRow } from '../api/supplier'

defineOptions({ name: 'PurSupplierList' })

/** 供应商列表（需求 07-01 3.1，T1） */
const router = useRouter()
const tableRef = ref<{ getVisibleColumns: () => TableColumn[] }>()
const importRef = ref<{ open: () => void }>()

type Query = Omit<SupplierQuery, 'pageNo' | 'pageSize' | 'statuses'> & { statuses?: string[] }
const toParams = (q: Query) => ({ ...q, statuses: joinList(q.statuses) })

const { query, list, total, loading, load, search, reset } = useListPage<Query, SupplierRow>({
  api: (q) => supplierApi.page(toParams(q) as SupplierQuery),
  defaultQuery: () => ({ statuses: ['POTENTIAL', 'PENDING', 'QUALIFIED', 'SUSPENDED'] }),
  refreshOnActivated: true
})

const fields: SearchField[] = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称/简称' },
  { prop: 'statuses', label: '状态', type: 'select', options: SUPPLIER_STATUS_OPTIONS, multiple: true },
  { prop: 'level', label: '等级', type: 'dict', dictType: 'pur_supplier_level' },
  { prop: 'supplierType', label: '类型', type: 'dict', dictType: 'pur_supplier_type' },
  { prop: 'buyerId', label: '采购员', type: 'user' },
  { prop: 'certExpiry', label: '资质到期', type: 'select', options: CERT_EXPIRY_OPTIONS },
  { prop: 'materialId', label: '可供物料', type: 'slot' }
]

const columns = computed<TableColumn<SupplierRow>[]>(() => [
  { prop: 'code', label: '编码', width: 110, type: 'link', onClick: (r) => router.push(`/purchase/supplier/${r.id}`) },
  { prop: 'shortName', label: '简称', width: 120 },
  { prop: 'name', label: '名称', minWidth: 200 },
  { prop: 'supplierType', label: '类型', width: 100, type: 'dict', dictType: 'pur_supplier_type' },
  { prop: 'level', label: '等级', width: 70, type: 'dict', dictType: 'pur_supplier_level' },
  { prop: 'country', label: '国家', width: 70 },
  { prop: 'buyerName', label: '采购员', width: 90 },
  { prop: 'currency', label: '币别', width: 70 },
  { prop: 'paymentTermName', label: '付款条件', width: 120 },
  { prop: 'primaryContact', label: '主联系人', width: 100 },
  { prop: 'primaryPhone', label: '电话', width: 120, hidden: true },
  { prop: 'cert', label: '资质', width: 100, slot: true },
  { prop: 'status', label: '状态', width: 100, type: 'status', statusMap: SUPPLIER_STATUS },
  { prop: 'updatedAt', label: '更新时间', width: 150, type: 'datetime', hidden: true }
])

async function qualify(r: SupplierRow) {
  const st = await supplierApi.qualify(r.id)
  ElMessage.success(st === 'QUALIFIED' ? '准入成功' : '已提交准入审批')
  load()
}

const reasonRef = ref<{ open: (o: { title?: string; tip?: string; options?: string[] }) => Promise<string | undefined> }>()
async function withReason(title: string, tip: string, fn: (reason: string) => Promise<unknown>) {
  const reason = await reasonRef.value?.open({ title, tip })
  if (!reason) return
  await fn(reason)
  ElMessage.success('操作成功')
  load()
}

async function resume(r: SupplierRow) {
  await supplierApi.resume(r.id)
  ElMessage.success('已恢复')
  load()
}

async function remove(r: SupplierRow) {
  await supplierApi.remove(r.id)
  ElMessage.success('删除成功')
  load()
}

const exportColumns = () => (tableRef.value?.getVisibleColumns() ?? []).map((c) => String(c.prop))
const asRow = (r: unknown) => r as SupplierRow
</script>

<template>
  <ErpPage description="供应商主数据：基本信息、交易条件、联系人、银行、资质与可供物料；准入后才能下单">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable ref="tableRef" :columns="columns" :data="list" :loading="loading" storage-key="pur.supplier" :actions-width="200" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'pur:supplier:create'" type="primary" icon="Plus" @click="router.push('/purchase/supplier/new')">新建供应商</el-button>
        </template>
        <template #toolbar-right>
          <ErpIconButton icon="Upload" tooltip="导入" permission="pur:supplier:import" @click="importRef?.open()" />
          <ExportButton url="/purchase/suppliers/export" :params="() => toParams({ ...query })" :columns="exportColumns" filename="供应商" permission="pur:supplier:export" />
        </template>
        <template #col-cert="{ row }">
          <ErpBadge v-if="asRow(row).certExpired" type="danger">资质过期</ErpBadge>
          <ErpBadge v-else-if="asRow(row).certExpiring" type="warning">即将到期</ErpBadge>
          <span v-else class="text-muted">正常</span>
        </template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '编辑', permission: 'pur:supplier:update', visible: asRow(row).status !== 'PENDING' && asRow(row).status !== 'ELIMINATED', handler: () => router.push(`/purchase/supplier/${asRow(row).id}/edit`) },
            { label: '提交准入', permission: 'pur:supplier:qualify', visible: asRow(row).status === 'POTENTIAL', handler: () => qualify(asRow(row)) },
            { label: '暂停', permission: 'pur:supplier:suspend', visible: asRow(row).status === 'QUALIFIED', handler: () => withReason('暂停供应商', '暂停后不能对该供应商下新订单，已有订单可以继续执行。', (r) => supplierApi.suspend(asRow(row).id, r)) },
            { label: '恢复', permission: 'pur:supplier:suspend', visible: asRow(row).status === 'SUSPENDED', handler: () => resume(asRow(row)) },
            { label: '淘汰', permission: 'pur:supplier:eliminate', danger: true, visible: asRow(row).status === 'QUALIFIED' || asRow(row).status === 'SUSPENDED', handler: () => withReason('淘汰供应商', '淘汰后不能恢复，也不能再下单和收货。', (r) => supplierApi.eliminate(asRow(row).id, r)) },
            { label: '删除', permission: 'pur:supplier:delete', danger: true, visible: asRow(row).status === 'POTENTIAL', confirm: `确定删除供应商「${asRow(row).code} ${asRow(row).shortName}」吗？删除后不可恢复。`, handler: () => remove(asRow(row)) }
          ]" />
        </template>
        <template #empty>
          <el-button v-perm="'pur:supplier:create'" icon="Plus" @click="router.push('/purchase/supplier/new')">新建供应商</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
    <ReasonDialog ref="reasonRef" />
    <ImportDialog ref="importRef" title="导入供应商" base="/purchase/suppliers" template-name="供应商" @done="load" />
  </ErpPage>
</template>
