<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatAmount } from '@/utils/format'
import {
  labelOf, num, round2, settingApi, VOUCHER_BIZ_TYPES, VOUCHER_STATUS, voucherApi, type AccountOption, type VoucherDetail, type VoucherLine
} from '../api/finance'

defineOptions({ name: 'FinVoucherEdit' })

/**
 * 凭证编辑 / 查看（需求 12-06 4.2，T4 经典凭证样式）：科目只能选末级启用科目，有辅助核算时选择客户 / 供应商 / 部门；
 * 借贷不平衡时合计标红（可保存草稿，不能审核）。草稿可编辑、删除；审核人不能是制单人；已审核可过账、反审核；已过账可反过账。
 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const accounts = ref<AccountOption[]>([])
const detail = ref<VoucherDetail>()
const saving = ref(false)
type Row = VoucherLine & { key: number }
let seq = 0
const blank = (summary?: string): Row => ({ key: ++seq, summary, accountCode: '' })
const form = ref<{ voucherDate: string; attachmentCount: number; remark?: string; lines: Row[] }>({
  voucherDate: new Date().toISOString().slice(0, 10), attachmentCount: 0, lines: [blank(), blank()]
})
const guard = useLeaveGuard(() => form.value)
const status = computed(() => detail.value?.header.status ?? 'DRAFT')
const editable = computed(() => status.value === 'DRAFT' && me.hasPermission(id.value ? 'fin:voucher:update' : 'fin:voucher:create'))
const totalDebit = computed(() => round2(form.value.lines.reduce((s, l) => s + num(l.debit), 0)))
const totalCredit = computed(() => round2(form.value.lines.reduce((s, l) => s + num(l.credit), 0)))
const balanced = computed(() => totalDebit.value === totalCredit.value && totalDebit.value > 0)
const accountOf = (code?: string) => accounts.value.find((a) => a.code === code)
const hasAux = (l: Row, aux: string) => accountOf(l.accountCode)?.auxTypes.includes(aux) ?? false
const voucherWord = computed(() => detail.value ? detail.value.header.voucherNo : '（保存后编号）')

async function load() {
  if (!id.value) return
  const d = await voucherApi.get(id.value)
  detail.value = d
  form.value = { voucherDate: d.header.voucherDate, attachmentCount: d.header.attachmentCount, remark: d.remark,
    lines: d.lines.map((l) => ({ ...l, key: ++seq, debit: num(l.debit) ? l.debit : undefined, credit: num(l.credit) ? l.credit : undefined })) }
  tabs.setTitle(tabKeyOf(route), `凭证 ${d.header.voucherNo}`)
  guard.markClean()
}
onMounted(async () => {
  accounts.value = await settingApi.accountOptions()
  await load()
  guard.markClean()
})

function addLine(i?: number) {
  const prev = form.value.lines[i ?? form.value.lines.length - 1]
  form.value.lines.splice((i ?? form.value.lines.length - 1) + 1, 0, blank(prev?.summary))
}
function removeLine(i: number) {
  if (form.value.lines.length > 2) form.value.lines.splice(i, 1)
}
function onDebit(l: Row) { if (num(l.debit)) l.credit = undefined }
function onCredit(l: Row) { if (num(l.credit)) l.debit = undefined }
/** 最后一行按差额补平 */
function balanceLast(l: Row) {
  const diff = round2(totalDebit.value - totalCredit.value + num(l.credit) - num(l.debit))
  if (diff > 0) { l.credit = String(diff); l.debit = undefined } else if (diff < 0) { l.debit = String(-diff); l.credit = undefined }
}

async function save() {
  const lines = form.value.lines.filter((l) => l.accountCode || num(l.debit) || num(l.credit))
  if (lines.length < 2) return ElMessage.warning('凭证至少需要两行分录')
  const bad = lines.findIndex((l) => !l.accountCode || (num(l.debit) === 0) === (num(l.credit) === 0))
  if (bad >= 0) return ElMessage.warning(`第 ${bad + 1} 行请选择科目，并填写借方或贷方金额`)
  saving.value = true
  try {
    const body = { voucherDate: form.value.voucherDate, attachmentCount: form.value.attachmentCount, remark: form.value.remark,
      lines: lines.map(({ key: _k, ...l }) => l) }
    if (id.value) {
      await voucherApi.update(id.value, body)
      ElMessage.success(balanced.value ? '已保存' : '已保存（借贷不平衡，不能审核）')
      await load()
    } else {
      const nid = await voucherApi.create(body)
      guard.markClean()
      ElMessage.success('已保存')
      tabs.remove([tabKeyOf(route)])
      router.push(`/finance/voucher/${nid}`)
    }
  } finally {
    saving.value = false
  }
}
async function act(action: 'audit' | 'unaudit' | 'post' | 'unpost', label: string) {
  await voucherApi.action(id.value!, action)
  ElMessage.success(`已${label}`)
  load()
}
async function remove() {
  await ElMessageBox.confirm('确定删除该草稿凭证吗？来源单据可重新生成凭证。', '删除凭证', { type: 'warning' })
  await voucherApi.remove(id.value!)
  guard.markClean()
  ElMessage.success('已删除')
  tabs.remove([tabKeyOf(route)])
  router.push('/finance/voucher')
}
async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push('/finance/voucher')
}
</script>

<template>
  <ErpPage :title="id ? `凭证 ${detail?.header.voucherNo ?? ''}` : '新建凭证'" back sticky :on-back="back">
    <template #actions>
      <StatusTag v-if="detail" :value="status" :map="VOUCHER_STATUS" />
      <el-button v-if="editable && id" type="danger" plain @click="remove">删除</el-button>
      <el-button v-if="status === 'AUDITED' && me.hasPermission('fin:voucher:audit')" @click="act('unaudit', '反审核')">反审核</el-button>
      <el-button v-if="status === 'POSTED' && me.hasPermission('fin:voucher:unpost')" @click="act('unpost', '反过账')">反过账</el-button>
      <el-button v-if="editable" :loading="saving" @click="save">保存</el-button>
      <el-button v-if="id && status === 'DRAFT' && me.hasPermission('fin:voucher:audit')" type="primary" :disabled="!balanced" @click="act('audit', '审核')">审核</el-button>
      <el-button v-if="status === 'AUDITED' && me.hasPermission('fin:voucher:post')" type="primary" @click="act('post', '过账')">过账</el-button>
    </template>
    <ErpPanel>
      <div class="head">
        <span class="word">{{ voucherWord }}</span>
        <span class="label">日期</span>
        <el-date-picker v-model="form.voucherDate" value-format="YYYY-MM-DD" class="date" :disabled="!editable" :clearable="false" />
        <span class="label">附件</span>
        <el-input-number v-model="form.attachmentCount" :min="0" :max="999" controls-position="right" class="att" :disabled="!editable" />
        <span class="label">张</span>
        <span class="grow" />
        <span v-if="detail" class="meta">
          {{ detail.header.source === 'AUTO' ? labelOf(VOUCHER_BIZ_TYPES, detail.header.bizType) || '自动' : '手工' }}
          · 制单 {{ detail.header.creatorName ?? '-' }} · 审核 {{ detail.header.auditorName ?? '-' }} · 过账 {{ detail.header.posterName ?? '-' }}
        </span>
      </div>
    </ErpPanel>
    <ErpPanel flush>
      <el-table :data="form.lines" row-key="key" class="voucher" show-summary :summary-method="() => ['', '合计', formatAmount(totalDebit), formatAmount(totalCredit), '']">
        <el-table-column type="index" label="#" width="44" />
        <el-table-column label="摘要" min-width="200">
          <template #default="{ row }">
            <el-input v-if="editable" v-model="row.summary" maxlength="200" />
            <span v-else>{{ row.summary }}</span>
          </template>
        </el-table-column>
        <el-table-column label="科目" min-width="320">
          <template #default="{ row }">
            <template v-if="editable">
              <el-select v-model="row.accountCode" filterable class="w-full" placeholder="编码 / 名称">
                <el-option v-for="a in accounts" :key="a.code" :value="a.code" :label="`${a.code} ${a.fullName}`" />
              </el-select>
              <div v-if="hasAux(row as Row, 'CUSTOMER') || hasAux(row as Row, 'SUPPLIER') || hasAux(row as Row, 'DEPT')" class="aux">
                <CustomerSelect v-if="hasAux(row as Row, 'CUSTOMER')" v-model="row.auxCustomerId" placeholder="客户" />
                <SupplierSelect v-if="hasAux(row as Row, 'SUPPLIER')" v-model="row.auxSupplierId" placeholder="供应商" />
                <OrgTreeSelect v-if="hasAux(row as Row, 'DEPT')" v-model="row.auxDeptId" placeholder="部门" />
              </div>
            </template>
            <template v-else>
              {{ row.accountCode }} {{ row.accountName ?? accountOf(row.accountCode)?.fullName }}
              <span v-if="row.auxCustomerName || row.auxSupplierName || row.auxDeptName" class="sub">
                / {{ [row.auxCustomerName, row.auxSupplierName, row.auxDeptName, row.auxMaterialName].filter(Boolean).join(' / ') }}
              </span>
              <div v-if="row.currency && num(row.fcAmount)" class="sub">{{ row.currency }} {{ formatAmount(row.fcAmount) }} × {{ row.exchangeRate }}</div>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="借方金额" width="160" align="right">
          <template #default="{ row }">
            <AmountInput v-if="editable" v-model="row.debit" @change="onDebit(row as Row)" />
            <span v-else>{{ num(row.debit) ? formatAmount(row.debit) : '' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="贷方金额" width="160" align="right">
          <template #default="{ row }">
            <AmountInput v-if="editable" v-model="row.credit" @change="onCredit(row as Row)" />
            <span v-else>{{ num(row.credit) ? formatAmount(row.credit) : '' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="editable" width="110">
          <template #default="{ row, $index }">
            <ErpIconButton icon="Plus" tooltip="插入行" @click="addLine($index)" />
            <ErpIconButton icon="Check" tooltip="按差额补平" @click="balanceLast(row as Row)" />
            <ErpIconButton icon="Delete" tooltip="删除行" @click="removeLine($index)" />
          </template>
        </el-table-column>
      </el-table>
      <div class="foot">
        <span v-if="!balanced" class="red">借贷不平衡，差额 {{ formatAmount(Math.abs(round2(totalDebit - totalCredit))) }}</span>
        <span v-else class="ok">借贷平衡</span>
      </div>
    </ErpPanel>
    <ErpPanel title="备注">
      <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" :disabled="!editable" />
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.head { display: flex; align-items: center; gap: var(--erp-space-2); flex-wrap: wrap; }
.word { font-size: var(--erp-font-size-section-title); font-weight: var(--erp-font-weight-medium); margin-right: var(--erp-space-4); }
.label { color: var(--erp-color-text-secondary); }
.date { width: 150px; }
.att { width: 100px; }
.grow { flex: 1; }
.meta { color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-caption); }
.aux { display: flex; gap: var(--erp-space-1); margin-top: var(--erp-space-1); }
.sub { color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-caption); }
.foot { padding: var(--erp-space-2) var(--erp-space-3); text-align: right; }
.red { color: var(--erp-color-error); }
.ok { color: var(--erp-color-success); }
</style>
