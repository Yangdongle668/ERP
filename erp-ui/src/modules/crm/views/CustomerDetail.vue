<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction } from '@/components'
import { http, type PageResult } from '@/api/http'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatAmount, formatQty } from '@/utils/format'
import FollowupTimeline from '../components/FollowupTimeline.vue'
import OpportunityDialog from '../components/OpportunityDialog.vue'
import {
  ADDRESS_TYPE_OPTIONS, CREDIT_CONTROL_OPTIONS, CUSTOMER_STATUS, GENDER_OPTIONS, OPP_STATUS, STAGE_LABELS, customerApi, labelOf, oppApi, partApi,
  type CustomerDetail, type OppRow, type PartRow, type TransferLogRow
} from '../api/crm'

defineOptions({ name: 'CrmCustomerDetail' })

/** 客户详情（需求 03-01 3.3，T5 客户 360 视图）：其他模块的页签按用户权限显示 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => String(route.params.id))
const d = ref<CustomerDetail>()
const activeTab = ref('contacts')
const s = computed(() => d.value?.customerStatus)

async function load() {
  d.value = await customerApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.shortName)
}
async function run(p: Promise<unknown>, msg: string) {
  await p
  ElMessage.success(msg)
  load()
}

// ---------- 转移 ----------
const transferVisible = ref(false)
const transfer = ref<{ newOwnerId?: string; transferDocs: boolean; reason: string }>({ transferDocs: true, reason: '' })
async function doTransfer() {
  if (!transfer.value.newOwnerId) return ElMessage.warning('请选择新负责人')
  if (!transfer.value.reason.trim()) return ElMessage.warning('请填写转移原因')
  await customerApi.transfer({ customerIds: [id.value], newOwnerId: transfer.value.newOwnerId, transferDocs: transfer.value.transferDocs, reason: transfer.value.reason.trim() })
  transferVisible.value = false
  ElMessage.success('已转移')
  load()
  transferLogs.value = []
}

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'crm:customer:delete', visible: () => s.value === 'PROSPECT', confirm: `确定删除客户「${d.value?.shortName}」吗？`,
    handler: async () => {
      await customerApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/crm/customer')
    } },
  { key: 'blacklist', label: '加入黑名单', permission: 'crm:customer:blacklist', visible: () => ['PROSPECT', 'ACTIVE', 'DISABLED'].includes(s.value ?? ''),
    reasonRequired: true, reasonTitle: '加入黑名单', handler: (reason) => run(customerApi.blacklist(id.value, reason!), '已加入黑名单') },
  { key: 'unblacklist', label: '移出黑名单', permission: 'crm:customer:blacklist', visible: () => s.value === 'BLACKLIST',
    handler: () => run(customerApi.unblacklist(id.value), '已移出黑名单') },
  { key: 'disable', label: '停用', permission: 'crm:customer:disable', visible: () => s.value === 'ACTIVE',
    confirm: '停用后不能报价、下单，在途订单可以出货。确定停用吗？', handler: () => run(customerApi.disable(id.value), '已停用') },
  { key: 'enable', label: '启用', permission: 'crm:customer:disable', visible: () => s.value === 'DISABLED', handler: () => run(customerApi.enable(id.value), '已启用') },
  { key: 'transfer', label: '转移', permission: 'crm:customer:transfer', visible: () => s.value !== 'BLACKLIST',
    handler: () => {
      transfer.value = { transferDocs: true, reason: '' }
      transferVisible.value = true
    } },
  { key: 'edit', label: '编辑', permission: 'crm:customer:update', visible: () => s.value !== 'BLACKLIST', handler: () => router.push(`/crm/customer/${id.value}/edit`) },
  { key: 'activate', label: '转正式', type: 'primary', permission: 'crm:customer:activate', visible: () => s.value === 'PROSPECT',
    handler: async () => {
      const r = await customerApi.activate(id.value)
      ElMessage.success(r.customerStatus === 'ACTIVE' ? '已转为正式客户' : '已提交转正式审批')
      load()
    } }
])

// ---------- 信用 ----------
const creditClass = computed(() => {
  const p = Number(d.value?.credit?.usagePct ?? 0)
  return p > 1 ? 'text-danger' : p > 0.8 ? 'text-warning' : ''
})

// ---------- 懒加载页签 ----------
const opps = ref<OppRow[]>([])
const parts = ref<PartRow[]>([])
const transferLogs = ref<TransferLogRow[]>([])
interface SampleBrief { id: string; docNo: string; sampleType: string; materialCode?: string; materialName?: string; qty: string; uom?: string; requiredDate: string; sampleStatus: string; feedbackResult?: string }
const samples = ref<SampleBrief[]>([])
const oppDialog = ref<InstanceType<typeof OpportunityDialog>>()
async function loadOpps() {
  opps.value = (await oppApi.page({ customerId: id.value, statuses: 'OPEN,WON,LOST,SHELVED', pageNo: 1, pageSize: 100 })).list
}
async function onTab(name: string | number) {
  if (name === 'opps') loadOpps()
  if (name === 'parts' && !parts.value.length) parts.value = (await partApi.page({ customerId: id.value, pageNo: 1, pageSize: 200 })).list
  if (name === 'transfers' && !transferLogs.value.length) transferLogs.value = await customerApi.transferLogs(id.value)
  if (name === 'samples' && !samples.value.length) {
    samples.value = (await http.get<PageResult<SampleBrief>>('/engineering/samples', { customerId: id.value, statuses: 'DRAFT,PENDING,APPROVED,MAKING,READY,SHIPPED,FEEDBACK,CLOSED', pageNo: 1, pageSize: 100 })).list
  }
}

const primary = computed(() => d.value?.contacts.find((c) => c.isPrimary))
onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d ? `${d.code} ${d.shortName}` : '客户'" :status="d?.customerStatus" :status-map="CUSTOMER_STATUS" :actions="actions"
                     @back="router.push('/crm/customer')">
        <template #extra><DictTag v-if="d" type="crm_customer_level" :value="d.level" /></template>
        <template #actions-prefix><ApprovalActions v-if="d" biz-type="CRM_CUSTOMER_ACTIVATE" :biz-id="id" @changed="load" /></template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <el-alert v-if="d.customerStatus === 'BLACKLIST'" type="error" :closable="false" show-icon :title="`黑名单：${d.blacklistReason ?? ''}`" class="gap" />
        <el-descriptions :column="3">
          <el-descriptions-item label="名称">{{ d.name }}</el-descriptions-item>
          <el-descriptions-item label="英文名">{{ d.nameEn || '-' }}</el-descriptions-item>
          <el-descriptions-item label="国家">{{ d.country }}{{ d.isForeign ? '（外销）' : '' }}</el-descriptions-item>
          <el-descriptions-item label="类型"><DictTag type="crm_customer_type" :value="d.customerType" /></el-descriptions-item>
          <el-descriptions-item label="负责人">{{ d.ownerName ?? '-' }}{{ d.deptName ? `（${d.deptName}）` : '' }}</el-descriptions-item>
          <el-descriptions-item label="主联系人">{{ primary ? `${primary.name} ${primary.email ?? primary.mobile ?? primary.phone ?? ''}` : '-' }}</el-descriptions-item>
          <el-descriptions-item label="币别 / 税率">{{ d.currency }} / {{ Number((Number(d.salesTaxRate) * 100).toFixed(2)) }}%</el-descriptions-item>
          <el-descriptions-item label="付款条件">{{ d.paymentTermName ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="贸易条款">{{ d.tradeTerm ? '' : '-' }}<DictTag v-if="d.tradeTerm" type="sys_trade_term" :value="d.tradeTerm" /></el-descriptions-item>
          <el-descriptions-item label="税号">{{ d.taxNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="电话 / 邮箱">{{ d.phone || '-' }} / {{ d.email || '-' }}</el-descriptions-item>
          <el-descriptions-item label="网址">{{ d.website || '-' }}</el-descriptions-item>
          <el-descriptions-item label="下单">首次 {{ d.firstOrderDate ?? '-' }}，最近 {{ d.lastOrderDate ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="地址" :span="2">{{ [d.province, d.city, d.address].filter(Boolean).join(' ') || '-' }}</el-descriptions-item>
          <template v-if="d.credit">
            <el-descriptions-item label="信用额度">
              <span class="num">{{ d.credit.creditLimit ? formatAmount(d.credit.creditLimit) : '未设置' }}</span>
              <span class="text-muted">（{{ labelOf(CREDIT_CONTROL_OPTIONS, d.credit.creditControl) }}{{ d.credit.creditDays ? `，信用期 ${d.credit.creditDays} 天` : '' }}）</span>
            </el-descriptions-item>
            <el-descriptions-item label="已用 / 可用">
              <span :class="['num', creditClass]">{{ formatAmount(d.credit.used) }}</span> /
              <span :class="['num', { 'text-danger': Number(d.credit.available) < 0 }]">{{ d.credit.available ? formatAmount(d.credit.available) : '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="逾期应收"><span :class="['num', { 'text-danger': Number(d.credit.overdueAmount) > 0 }]">{{ formatAmount(d.credit.overdueAmount) }}</span></el-descriptions-item>
          </template>
          <el-descriptions-item label="备注" :span="3">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs" @tab-change="onTab">
          <el-tab-pane :label="`联系人(${d.contacts.length})`" name="contacts">
            <el-table :data="d.contacts">
              <el-table-column label="姓名" width="120"><template #default="{ row }">{{ row.name }} <ErpBadge v-if="row.isPrimary" type="primary">主</ErpBadge></template></el-table-column>
              <el-table-column label="性别" width="60"><template #default="{ row }">{{ labelOf(GENDER_OPTIONS, row.gender) }}</template></el-table-column>
              <el-table-column prop="title" label="职位" width="120" />
              <el-table-column label="角色" width="90"><template #default="{ row }"><DictTag type="crm_contact_role" :value="row.role" /></template></el-table-column>
              <el-table-column prop="email" label="邮箱" min-width="180" />
              <el-table-column prop="phone" label="电话" width="130" />
              <el-table-column prop="mobile" label="手机" width="130" />
              <el-table-column prop="im" label="即时通讯" width="130" />
              <el-table-column label="状态" width="80"><template #default="{ row }">{{ row.status === 'LEFT' ? '已离职' : '在职' }}</template></el-table-column>
              <template #empty><ErpEmpty compact description="没有联系人" /></template>
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`地址(${d.addresses.length})`" name="addresses">
            <el-table :data="d.addresses">
              <el-table-column label="类型" width="100"><template #default="{ row }">{{ labelOf(ADDRESS_TYPE_OPTIONS, row.addressType) }} <ErpBadge v-if="row.isDefault" type="success">默认</ErpBadge></template></el-table-column>
              <el-table-column prop="companyName" label="抬头" min-width="180" />
              <el-table-column label="联系人" width="160"><template #default="{ row }">{{ row.contactName ?? '' }} {{ row.phone ?? '' }}</template></el-table-column>
              <el-table-column label="地址" min-width="260"><template #default="{ row }">{{ [row.country, row.province, row.city, row.addressLine].filter(Boolean).join(' ') }}{{ row.zip ? `（${row.zip}）` : '' }}</template></el-table-column>
              <template #empty><ErpEmpty compact description="没有地址" /></template>
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`银行(${d.banks.length})`" name="banks">
            <el-table :data="d.banks">
              <el-table-column prop="bankName" label="开户行" min-width="180" />
              <el-table-column prop="accountName" label="户名" min-width="160" />
              <el-table-column prop="accountNo" label="账号" min-width="180" />
              <el-table-column prop="swift" label="SWIFT" width="120" />
              <el-table-column prop="currency" label="币别" width="70" />
              <template #empty><ErpEmpty compact description="没有银行信息" /></template>
            </el-table>
          </el-tab-pane>
          <el-tab-pane v-if="me.hasPermission('crm:followup:query')" label="跟进记录" name="followups" lazy><FollowupTimeline :customer-id="id" /></el-tab-pane>
          <el-tab-pane v-if="me.hasPermission('crm:opportunity:query')" label="商机" name="opps" lazy>
            <div class="bar"><el-button v-perm="'crm:opportunity:create'" type="primary" icon="Plus" @click="oppDialog?.open(undefined, { customerId: id, currency: d.currency })">新建商机</el-button></div>
            <el-table :data="opps">
              <el-table-column prop="code" label="编码" width="140" />
              <el-table-column label="名称" min-width="200"><template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/crm/opportunity?open=${row.id}`)">{{ row.name }}</el-link></template></el-table-column>
              <el-table-column label="阶段" width="100"><template #default="{ row }">{{ STAGE_LABELS[row.stage] }}</template></el-table-column>
              <el-table-column label="金额" width="150" align="right"><template #default="{ row }"><span class="num">{{ row.currency }} {{ formatAmount(row.amount) }}</span></template></el-table-column>
              <el-table-column label="赢率" width="70" align="right"><template #default="{ row }">{{ Math.round(Number(row.winRate) * 100) }}%</template></el-table-column>
              <el-table-column prop="expectedDate" label="预计成交" width="110" />
              <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" :map="OPP_STATUS" /></template></el-table-column>
              <template #empty><ErpEmpty compact description="没有商机" /></template>
            </el-table>
          </el-tab-pane>
          <el-tab-pane v-if="me.hasPermission('crm:customer-part:query')" label="客户料号" name="parts" lazy>
            <el-table :data="parts">
              <el-table-column prop="customerPartNo" label="客户料号" width="150" />
              <el-table-column prop="customerPartName" label="客户品名" min-width="160" />
              <el-table-column prop="customerRevision" label="版本" width="70" />
              <el-table-column label="本厂物料" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
              <el-table-column label="状态" width="80"><template #default="{ row }">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</template></el-table-column>
              <template #empty><ErpEmpty compact description="没有客户料号对照" /></template>
            </el-table>
          </el-tab-pane>
          <el-tab-pane v-if="me.hasPermission('eng:sample:query')" label="样品" name="samples" lazy>
            <el-table :data="samples">
              <el-table-column label="单号" width="160"><template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/engineering/sample/${row.id}`)">{{ row.docNo }}</el-link></template></el-table-column>
              <el-table-column label="物料" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
              <el-table-column label="数量" width="100" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.qty) }} {{ row.uom ?? '' }}</span></template></el-table-column>
              <el-table-column prop="requiredDate" label="要求日期" width="110" />
              <el-table-column prop="sampleStatus" label="状态" width="100" />
              <template #empty><ErpEmpty compact description="没有样品单" /></template>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="转移记录" name="transfers" lazy>
            <el-table :data="transferLogs">
              <el-table-column label="时间" width="160"><template #default="{ row }">{{ row.createdAt?.slice(0, 16) }}</template></el-table-column>
              <el-table-column label="原负责人 → 新负责人" width="220"><template #default="{ row }">{{ row.fromOwnerName ?? '-' }} → {{ row.toOwnerName }}</template></el-table-column>
              <el-table-column label="转移单据" width="90"><template #default="{ row }">{{ row.transferDocs ? '是' : '否' }}</template></el-table-column>
              <el-table-column prop="reason" label="原因" min-width="200" />
              <el-table-column prop="operatorName" label="操作人" width="100" />
              <template #empty><ErpEmpty compact description="没有转移记录" /></template>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="CRM_CUSTOMER_ACTIVATE" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="CRM_CUSTOMER" :biz-id="id" :status-map="CUSTOMER_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="CRM_CUSTOMER" :biz-id="id" :editable="d.customerStatus !== 'BLACKLIST'" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="transferVisible" title="客户转移" width="520px" append-to-body>
      <el-form label-width="120px">
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
    <OpportunityDialog ref="oppDialog" @saved="loadOpps" />
  </ErpPage>
</template>

<style scoped>
.gap { margin-bottom: var(--erp-space-3); }
.bar { margin-bottom: var(--erp-space-3); }
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
</style>
