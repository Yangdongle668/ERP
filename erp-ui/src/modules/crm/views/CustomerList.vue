<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { formatAmount } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import PaymentTermSelect from '../components/PaymentTermSelect.vue'
import { CUSTOMER_STATUS, CUSTOMER_STATUS_OPTIONS, customerApi, type CustomerQuery, type CustomerRow } from '../api/crm'

defineOptions({ name: 'CrmCustomerList' })

/** 客户列表（需求 03-01 3.1，T1）：默认潜在、审批中、正式；按负责人/负责部门数据权限 */
const router = useRouter()
const me = useUserStore()
const tableRef = ref<{ getVisibleColumns: () => TableColumn[] }>()
const importRef = ref<{ open: () => void }>()
const reasonRef = ref<{ open: (o: { title?: string; tip?: string; options?: string[] }) => Promise<string | undefined> }>()

type Query = Omit<CustomerQuery, 'pageNo' | 'pageSize' | 'statuses' | 'levels' | 'countries' | 'lastOrderFrom' | 'lastOrderTo'> & {
  statuses?: string[]; levels?: string[]; countries?: string; lastOrder?: string[]
}
function toParams(q: Query) {
  const { statuses, levels, lastOrder, ...rest } = q
  return {
    ...rest, statuses: statuses?.length ? statuses.join(',') : undefined, levels: levels?.length ? levels.join(',') : undefined,
    lastOrderFrom: lastOrder?.[0], lastOrderTo: lastOrder?.[1], noOrderDays: rest.noOrderDays || undefined
  }
}
const { query, list, total, loading, selection, load, search, reset, onSelectionChange } = useListPage<Query, CustomerRow>({
  api: (q) => customerApi.page(toParams(q) as CustomerQuery),
  defaultQuery: () => ({ statuses: ['PROSPECT', 'PENDING', 'ACTIVE'] }),
  refreshOnActivated: true
})

const fields: SearchField[] = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称/英文名/简称' },
  { prop: 'statuses', label: '状态', type: 'select', options: CUSTOMER_STATUS_OPTIONS, multiple: true },
  { prop: 'levels', label: '等级', type: 'dict', dictType: 'crm_customer_level', multiple: true },
  { prop: 'countries', label: '国家', type: 'slot' },
  { prop: 'ownerId', label: '负责人', type: 'user' },
  { prop: 'customerType', label: '客户类型', type: 'dict', dictType: 'crm_customer_type' },
  { prop: 'source', label: '来源', type: 'dict', dictType: 'crm_source' },
  { prop: 'lastOrder', label: '最近下单', type: 'daterange' },
  { prop: 'noOrderDays', label: '超过N天未下单', type: 'number' }
]

const columns = computed<TableColumn<CustomerRow>[]>(() => [
  { prop: 'code', label: '编码', width: 100, type: 'link', onClick: (r) => router.push(`/crm/customer/${r.id}`) },
  { prop: 'shortName', label: '简称', width: 120 },
  { prop: 'name', label: '名称', minWidth: 200 },
  { prop: 'country', label: '国家', width: 80 },
  { prop: 'customerType', label: '类型', width: 90, type: 'dict', dictType: 'crm_customer_type' },
  { prop: 'level', label: '等级', width: 60, type: 'dict', dictType: 'crm_customer_level' },
  { prop: 'ownerName', label: '负责人', width: 90 },
  { prop: 'primaryContact', label: '主联系人', width: 140, slot: true },
  { prop: 'currency', label: '币别', width: 60 },
  { prop: 'creditLimit', label: '信用额度', width: 110, align: 'right', formatter: (r) => (r.creditVisible ? (r.creditLimit ? formatAmount(r.creditLimit) : '-') : '***') },
  { prop: 'lastOrderDate', label: '最近下单', width: 100, type: 'date', sortable: true },
  { prop: 'customerStatus', label: '状态', width: 80, type: 'status', statusMap: CUSTOMER_STATUS },
  { prop: 'createdAt', label: '创建时间', width: 150, type: 'datetime', hidden: true }
])

async function run(p: Promise<unknown>, msg: string) {
  await p
  ElMessage.success(msg)
  load()
}
async function activate(r: CustomerRow) {
  const res = await customerApi.activate(r.id)
  ElMessage.success(res.customerStatus === 'ACTIVE' ? '已转为正式客户' : '已提交转正式审批')
  load()
}
async function withReason(title: string, tip: string, fn: (reason: string) => Promise<unknown>, msg: string) {
  const reason = await reasonRef.value?.open({ title, tip })
  if (!reason) return
  await run(fn(reason), msg)
}

// ---------- 转移 ----------
const transferVisible = ref(false)
const transferRef = ref<FormInstance>()
const transfer = ref<{ newOwnerId?: string; transferDocs: boolean; reason: string }>({ transferDocs: true, reason: '' })
function openTransfer() {
  if (!selection.value.length) return ElMessage.warning('请先勾选客户')
  transfer.value = { transferDocs: true, reason: '' }
  transferVisible.value = true
}
async function doTransfer() {
  if (!transfer.value.newOwnerId) return ElMessage.warning('请选择新负责人')
  if (!transfer.value.reason.trim()) return ElMessage.warning('请填写转移原因')
  const n = await customerApi.transfer({ customerIds: selection.value.map((r) => r.id), newOwnerId: transfer.value.newOwnerId,
    transferDocs: transfer.value.transferDocs, reason: transfer.value.reason.trim() })
  ElMessage.success(`已转移 ${n} 个客户`)
  transferVisible.value = false
  load()
}

// ---------- 导入 ----------
const importTerm = ref<string>()
const importParams = computed(() => {
  const p: Record<string, string> = {}
  if (importTerm.value) p.paymentTermId = importTerm.value
  return p
})

const exportColumns = () => (tableRef.value?.getVisibleColumns() ?? []).map((c) => String(c.prop))
const asRow = (r: unknown) => r as CustomerRow
</script>

<template>
  <ErpPage description="客户主数据：潜在客户可报价、送样，转为正式客户后才能下单；业务员只能看到自己负责的客户">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-countries><el-input v-model="query.countries" placeholder="如 US,DE" clearable class="w200" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable ref="tableRef" :columns="columns" :data="list" :loading="loading" selection storage-key="crm.customer" :actions-width="200"
                @selection-change="onSelectionChange" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'crm:customer:create'" type="primary" icon="Plus" @click="router.push('/crm/customer/new')">新建客户</el-button>
          <el-button v-perm="'crm:customer:transfer'" icon="Users" @click="openTransfer">转移{{ selection.length ? `（${selection.length}）` : '' }}</el-button>
        </template>
        <template #toolbar-right>
          <ErpIconButton icon="Upload" tooltip="导入" permission="crm:customer:import" @click="importRef?.open()" />
          <ExportButton url="/crm/customers/export" :params="() => toParams({ ...query })" :columns="exportColumns" filename="客户" permission="crm:customer:export" />
        </template>
        <template #col-primaryContact="{ row }">
          <el-tooltip v-if="asRow(row).primaryContact" :content="asRow(row).primaryContactEmail || '无邮箱'" placement="top">
            <span>{{ asRow(row).primaryContact }}</span>
          </el-tooltip>
          <span v-else class="text-muted">-</span>
        </template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '编辑', permission: 'crm:customer:update', visible: asRow(row).customerStatus !== 'BLACKLIST', handler: () => router.push(`/crm/customer/${asRow(row).id}/edit`) },
            { label: '转正式', permission: 'crm:customer:activate', visible: asRow(row).customerStatus === 'PROSPECT', handler: () => activate(asRow(row)) },
            { label: '停用', permission: 'crm:customer:disable', visible: asRow(row).customerStatus === 'ACTIVE', confirm: `停用后不能对「${asRow(row).shortName}」报价、下单，在途订单可以出货。确定停用吗？`, handler: () => run(customerApi.disable(asRow(row).id), '已停用') },
            { label: '启用', permission: 'crm:customer:disable', visible: asRow(row).customerStatus === 'DISABLED', handler: () => run(customerApi.enable(asRow(row).id), '已启用') },
            { label: '加入黑名单', permission: 'crm:customer:blacklist', danger: true, visible: ['PROSPECT', 'ACTIVE', 'DISABLED'].includes(asRow(row).customerStatus), handler: () => withReason('加入黑名单', '黑名单客户不能报价、下单和出货（在途订单出货也会被阻止）。', (r) => customerApi.blacklist(asRow(row).id, r), '已加入黑名单') },
            { label: '移出黑名单', permission: 'crm:customer:blacklist', visible: asRow(row).customerStatus === 'BLACKLIST', handler: () => run(customerApi.unblacklist(asRow(row).id), '已移出黑名单') },
            { label: '删除', permission: 'crm:customer:delete', danger: true, visible: asRow(row).customerStatus === 'PROSPECT', confirm: `确定删除客户「${asRow(row).code} ${asRow(row).shortName}」吗？`, handler: () => run(customerApi.remove(asRow(row).id), '删除成功') }
          ]" />
        </template>
        <template #empty>
          <el-button v-perm="'crm:customer:create'" icon="Plus" @click="router.push('/crm/customer/new')">新建客户</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="transferVisible" title="客户转移" width="520px" append-to-body>
      <el-form ref="transferRef" label-width="120px">
        <el-form-item label="客户">{{ selection.map((r) => r.shortName).join('、') }}</el-form-item>
        <el-form-item label="新负责人" required><UserSelect v-model="transfer.newOwnerId" /></el-form-item>
        <el-form-item label="同时转移单据">
          <el-switch v-model="transfer.transferDocs" />
          <div class="form-tip">未完成的报价、订单的业务员同时改为新负责人</div>
        </el-form-item>
        <el-form-item label="原因" required><el-input v-model="transfer.reason" type="textarea" :rows="2" maxlength="512" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" @click="doTransfer">确定</el-button>
      </template>
    </el-dialog>

    <ReasonDialog ref="reasonRef" />
    <ImportDialog ref="importRef" title="导入客户" base="/crm/customers" template-name="客户" :params="importParams" allow-partial @done="load">
      <template #options>
        <div class="import-tip">导入为潜在客户；可同时导入主联系人和默认收货地址。</div>
        <el-form-item label="默认付款条件" v-if="me.hasPermission('crm:customer:import')"><PaymentTermSelect v-model="importTerm" /></el-form-item>
      </template>
    </ImportDialog>
  </ErpPage>
</template>

<style scoped>
.import-tip { margin-bottom: var(--erp-space-2); color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-secondary); }
</style>
