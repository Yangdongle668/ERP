<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { formatAmount, today } from '@/utils/format'
import { CHANGE_STATUS, CREDIT_CONTROL_OPTIONS, creditApi, customerApi, labelOf, type ChangeRow, type CreditRow } from '../api/crm'

defineOptions({ name: 'CrmCreditList' })

/** 客户信用（需求 03-03，T1 + 调整额度弹窗 + 调整记录抽屉） */
const route = useRoute()
const router = useRouter()
type Query = { customerId?: string; ownerId?: string; usageAtLeast?: number; overdueOnly?: boolean }
const { query, list, total, loading, load, search, reset } = useListPage<Query, CreditRow>({ api: (q) => creditApi.page(q), refreshOnActivated: true })

const fields: SearchField[] = [
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'ownerId', label: '负责人', type: 'user' },
  { prop: 'usageAtLeast', label: '使用率', type: 'select', options: [{ value: 80, label: '≥80%' }, { value: 100, label: '≥100%' }] },
  { prop: 'overdueOnly', label: '有逾期', type: 'slot' }
]
const money = (v?: string) => (v === undefined || v === null ? '-' : formatAmount(v))
const columns: TableColumn<CreditRow>[] = [
  { prop: 'customerCode', label: '客户编码', width: 100, type: 'link', onClick: (r) => router.push(`/crm/customer/${r.customerId}`) },
  { prop: 'customerShortName', label: '简称', width: 120 },
  { prop: 'ownerName', label: '负责人', width: 90 },
  { prop: 'effectiveControl', label: '控制方式', width: 90, formatter: (r) => labelOf(CREDIT_CONTROL_OPTIONS, r.effectiveControl) + (r.control === 'DEFAULT' ? '*' : '') },
  { prop: 'creditLimit', label: '信用额度', width: 120, align: 'right', formatter: (r) => money(r.creditLimit) },
  { prop: 'receivableBalance', label: '应收余额', width: 120, align: 'right', formatter: (r) => money(r.receivableBalance) },
  { prop: 'overdueAmount', label: '逾期应收', width: 110, align: 'right', slot: true },
  { prop: 'openOrderAmount', label: '未出货订单', width: 120, align: 'right', formatter: (r) => money(r.openOrderAmount) },
  { prop: 'used', label: '已用额度', width: 120, align: 'right', formatter: (r) => money(r.used) },
  { prop: 'available', label: '可用额度', width: 120, align: 'right', slot: true },
  { prop: 'usagePct', label: '使用率', width: 140, slot: true },
  { prop: 'creditDays', label: '信用期(天)', width: 90, align: 'right' },
  { prop: 'refreshedAt', label: '更新时间', width: 150, type: 'datetime', hidden: true }
]

// ---------- 调整额度 ----------
const visible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const target = ref<{ customerId: string; name: string; limit?: string; days?: number; control?: string }>()
const form = ref<{ newLimit?: string; newDays?: number; newControl?: string; temporary: boolean; expireDate?: string; reason: string }>({ temporary: false, reason: '' })
const rules: FormRules = {
  newLimit: [{ required: true, message: '请填写新额度', trigger: 'blur' }],
  reason: [{ required: true, message: '请填写调整原因', trigger: 'blur' }]
}
function openAdjust(t: { customerId: string; name: string; limit?: string; days?: number; control?: string }) {
  target.value = t
  form.value = { newLimit: t.limit, newDays: t.days, newControl: t.control ?? 'DEFAULT', temporary: false, reason: '' }
  visible.value = true
}
async function openAdjustFor(customerId: string) {
  const d = await customerApi.get(customerId)
  openAdjust({ customerId, name: `${d.code} ${d.shortName}`, limit: d.credit?.creditLimit, days: d.credit?.creditDays, control: d.credit?.creditControl })
}
async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  const f = form.value
  if (f.temporary && (!f.expireDate || f.expireDate <= today())) return ElMessage.warning('临时额度到期日必须晚于今天')
  saving.value = true
  try {
    const r = await creditApi.create({ customerId: target.value!.customerId, newLimit: f.newLimit!, newDays: f.newDays, newControl: f.newControl,
      expireDate: f.temporary ? f.expireDate : undefined, reason: f.reason.trim() })
    ElMessage.success(r.status === 'APPROVED' ? '额度已生效' : '已提交审批')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------- 调整记录 ----------
const drawer = ref(false)
const drawerTitle = ref('')
const changes = ref<ChangeRow[]>([])
async function openChanges(r: CreditRow) {
  drawerTitle.value = `调整记录 - ${r.customerCode} ${r.customerShortName}`
  changes.value = await creditApi.changes(r.customerId)
  drawer.value = true
}

onMounted(() => {
  if (typeof route.query.customerId === 'string') openAdjustFor(route.query.customerId)
})
const asRow = (r: unknown) => r as CreditRow
</script>

<template>
  <ErpPage description="已用额度 = 应收余额 + 未出货订单；销售订单审核、出货单提交时按控制方式检查（* 表示取系统参数）">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-customerId><CustomerSelect v-model="query.customerId" placeholder="全部" class="w200" /></template>
          <template #field-overdueOnly><el-switch v-model="query.overdueOnly" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="crm.credit" :actions-width="160" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'crm:credit:update'" type="primary" icon="Plus" @click="query.customerId ? openAdjustFor(query.customerId) : ElMessage.warning('请先在查询条件中选择客户')">
            为客户设置额度
          </el-button>
        </template>
        <template #col-overdueAmount="{ row }">
          <span :class="['num', { 'text-danger': Number(asRow(row).overdueAmount) > 0 }]">{{ money(asRow(row).overdueAmount) }}</span>
        </template>
        <template #col-available="{ row }">
          <span :class="['num', { 'text-danger': Number(asRow(row).available) < 0 }]">{{ money(asRow(row).available) }}</span>
        </template>
        <template #col-usagePct="{ row }">
          <el-progress v-if="asRow(row).usagePct" :percentage="Math.min(100, Math.round(Number(asRow(row).usagePct) * 100))" :stroke-width="8"
                       :status="Number(asRow(row).usagePct) >= 1 ? 'exception' : Number(asRow(row).usagePct) >= 0.8 ? 'warning' : undefined" />
          <span v-else class="text-muted">未设置额度</span>
        </template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '调整额度', permission: 'crm:credit:update', visible: !asRow(row).pendingChangeId, handler: () => openAdjust({ customerId: asRow(row).customerId, name: `${asRow(row).customerCode} ${asRow(row).customerShortName}`, limit: asRow(row).creditLimit, days: asRow(row).creditDays, control: asRow(row).control }) },
            { label: '调整记录', handler: () => openChanges(asRow(row)) }
          ]" />
          <ErpBadge v-if="asRow(row).pendingChangeId" type="warning">审批中</ErpBadge>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="visible" title="调整信用额度" width="560px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="客户">{{ target?.name }}</el-form-item>
        <el-form-item label="当前额度">{{ target?.limit ? formatAmount(target.limit) : '未设置' }}</el-form-item>
        <el-form-item label="新额度" prop="newLimit"><AmountInput v-model="form.newLimit" /></el-form-item>
        <el-form-item label="信用期(天)"><el-input-number v-model="form.newDays" :min="0" :max="365" :precision="0" controls-position="right" /></el-form-item>
        <el-form-item label="控制方式">
          <el-select v-model="form.newControl" class="w-full"><el-option v-for="o in CREDIT_CONTROL_OPTIONS" :key="o.value" :value="o.value" :label="o.label" /></el-select>
        </el-form-item>
        <el-form-item label="临时额度">
          <el-switch v-model="form.temporary" />
          <el-date-picker v-if="form.temporary" v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" placeholder="到期日" class="expire" />
          <div class="form-tip">到期后次日自动恢复为当前额度</div>
        </el-form-item>
        <el-form-item label="原因" prop="reason"><el-input v-model="form.reason" type="textarea" :rows="2" maxlength="512" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">提交</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="drawer" :title="drawerTitle" size="720px">
      <el-table :data="changes">
        <el-table-column prop="docNo" label="单号" width="190" />
        <el-table-column label="额度" min-width="200">
          <template #default="{ row }">{{ row.oldLimit ? formatAmount(row.oldLimit) : '未设置' }} → {{ formatAmount(row.newLimit) }}
            <span v-if="row.expireDate" class="text-muted">（临时至 {{ row.expireDate }}{{ row.restored ? '，已恢复' : '' }}）</span></template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" :map="CHANGE_STATUS" /></template></el-table-column>
        <el-table-column label="申请" width="150"><template #default="{ row }">{{ row.createdByName ?? '' }} {{ row.docDate }}</template></el-table-column>
        <template #empty><ErpEmpty compact description="没有调整记录" /></template>
      </el-table>
    </el-drawer>
  </ErpPage>
</template>

<style scoped>
.expire { margin-left: var(--erp-space-3); width: 160px; }
</style>
