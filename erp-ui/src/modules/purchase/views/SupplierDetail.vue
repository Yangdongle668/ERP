<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction, TableColumn } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatPercent } from '@/utils/format'
import { INSPECT_STATUS, labelOf } from '../api/common'
import { ORDER_STATUS, orderApi, type OrderRow } from '../api/order'
import { priceApi, type PriceRow } from '../api/price'
import { SCORE_STATUS, scoreApi, type ScoreRow } from '../api/score'
import {
  CERT_STATUS, INVOICE_TYPE_OPTIONS, SUPPLIER_STATUS, SUPPLY_STATUS, supplierApi,
  type QualitySummary, type SupplierDetail
} from '../api/supplier'

defineOptions({ name: 'PurSupplierDetail' })

/** 供应商详情（需求 07-01 3.3，T5） */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => String(route.params.id))
const d = ref<SupplierDetail>()
const activeTab = ref('base')

async function load() {
  d.value = await supplierApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), `${d.value.code} ${d.value.shortName}`)
}

const s = computed(() => d.value?.status)
const actions = computed<DocAction[]>(() => [
  { key: 'eliminate', label: '淘汰', permission: 'pur:supplier:eliminate', visible: () => s.value === 'QUALIFIED' || s.value === 'SUSPENDED',
    reasonRequired: true, reasonTitle: '淘汰原因', confirm: '淘汰后不能恢复，也不能再下单和收货。',
    handler: (reason) => run(supplierApi.eliminate(id.value, reason!), '已淘汰') },
  { key: 'suspend', label: '暂停', permission: 'pur:supplier:suspend', visible: () => s.value === 'QUALIFIED', reasonRequired: true, reasonTitle: '暂停原因',
    confirm: '暂停后不能对该供应商下新订单，已有订单可以继续执行。', handler: (reason) => run(supplierApi.suspend(id.value, reason!), '已暂停') },
  { key: 'resume', label: '恢复', permission: 'pur:supplier:suspend', visible: () => s.value === 'SUSPENDED', handler: () => run(supplierApi.resume(id.value), '已恢复') },
  { key: 'edit', label: '编辑', permission: 'pur:supplier:update', visible: () => s.value !== 'PENDING' && s.value !== 'ELIMINATED',
    handler: () => router.push(`/purchase/supplier/${id.value}/edit`) },
  { key: 'qualify', label: '提交准入', type: 'primary', permission: 'pur:supplier:qualify', visible: () => s.value === 'POTENTIAL',
    handler: async () => {
      const st = await supplierApi.qualify(id.value)
      ElMessage.success(st === 'QUALIFIED' ? '准入成功' : '已提交准入审批')
      load()
    } }
])

async function run(p: Promise<unknown>, msg: string) {
  await p
  ElMessage.success(msg)
  load()
}

// ---------- 懒加载页签 ----------
const canPrice = computed(() => me.hasPermission('pur:price:view') && me.hasPermission('pur:price:query'))
const prices = ref<PriceRow[]>()
const orders = ref<OrderRow[]>()
const quality = ref<QualitySummary>()
const scores = ref<ScoreRow[]>()

watch(activeTab, async (t) => {
  if (t === 'price' && !prices.value) prices.value = (await priceApi.page({ supplierId: id.value, pageNo: 1, pageSize: 200 })).list
  if (t === 'order' && !orders.value) orders.value = (await orderApi.page({ supplierId: id.value, pageNo: 1, pageSize: 50 })).list
  if (t === 'quality' && !quality.value) quality.value = await supplierApi.quality(id.value)
  if (t === 'score' && !scores.value) scores.value = (await scoreApi.page({ supplierId: id.value, pageNo: 1, pageSize: 24 })).list
})

const priceColumns: TableColumn<PriceRow>[] = [
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 160 },
  { prop: 'minQty', label: '起订量', width: 100, type: 'qty' },
  { prop: 'currency', label: '币别', width: 70 },
  { prop: 'price', label: '不含税价', width: 110, type: 'price' },
  { prop: 'priceInclTax', label: '含税价', width: 110, type: 'price' },
  { prop: 'taxRate', label: '税率', width: 80, type: 'percent' },
  { prop: 'effectiveFrom', label: '生效日期', width: 110, type: 'date' },
  { prop: 'effectiveTo', label: '失效日期', width: 110, type: 'date' }
]
const orderColumns: TableColumn<OrderRow>[] = [
  { prop: 'docNo', label: '订单号', width: 160, type: 'link', onClick: (r) => router.push(`/purchase/order/${r.id}`) },
  { prop: 'docDate', label: '日期', width: 110, type: 'date' },
  { prop: 'currency', label: '币别', width: 70 },
  { prop: 'totalAmount', label: '金额', width: 130, type: 'amount' },
  { prop: 'orderedQty', label: '订购数量', width: 110, type: 'qty' },
  { prop: 'receivedQty', label: '已到货', width: 110, type: 'qty' },
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: ORDER_STATUS }
]
const scoreColumns: TableColumn<ScoreRow>[] = [
  { prop: 'period', label: '评估期', width: 100 },
  { prop: 'qualityScore', label: '质量', width: 90, type: 'qty' },
  { prop: 'deliveryScore', label: '交期', width: 90, type: 'qty' },
  { prop: 'priceScore', label: '价格', width: 90, type: 'qty' },
  { prop: 'serviceScore', label: '服务', width: 90, type: 'qty' },
  { prop: 'totalScore', label: '总分', width: 90, type: 'qty' },
  { prop: 'grade', label: '等级', width: 70 },
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: SCORE_STATUS }
]

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d ? `${d.code} ${d.shortName}` : '供应商'" :status="d?.status" :status-map="SUPPLIER_STATUS" :actions="actions"
                     @back="router.push('/purchase/supplier')">
        <template #actions-prefix><ApprovalActions v-if="d" biz-type="PUR_SUPPLIER_QUALIFY" :biz-id="id" @changed="load" /></template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <el-alert v-if="d.status === 'SUSPENDED' || d.status === 'ELIMINATED'" :type="d.status === 'SUSPENDED' ? 'warning' : 'error'" :closable="false" show-icon
                  :title="`${SUPPLIER_STATUS[d.status].label}：${d.suspendReason || '-'}`" class="alert" />
        <el-descriptions :column="3">
          <el-descriptions-item label="名称">{{ d.name }}</el-descriptions-item>
          <el-descriptions-item label="英文名">{{ d.nameEn || '-' }}</el-descriptions-item>
          <el-descriptions-item label="类型 / 等级"><DictTag type="pur_supplier_type" :value="d.supplierType" /> / {{ d.level || '-' }}</el-descriptions-item>
          <el-descriptions-item label="采购员">{{ d.buyerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="币别 / 付款条件">{{ d.currency }} / {{ d.paymentTermName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="未完成订单">{{ d.openOrderCount }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane label="基本信息" name="base">
            <el-descriptions :column="3" border>
              <el-descriptions-item label="编码">{{ d.code }}</el-descriptions-item>
              <el-descriptions-item label="简称">{{ d.shortName }}</el-descriptions-item>
              <el-descriptions-item label="国家">{{ d.country || '-' }}</el-descriptions-item>
              <el-descriptions-item label="省 / 市">{{ d.province || '-' }} / {{ d.city || '-' }}</el-descriptions-item>
              <el-descriptions-item label="地址" :span="2">{{ d.address || '-' }}</el-descriptions-item>
              <el-descriptions-item label="税号">{{ d.taxNo || '-' }}</el-descriptions-item>
              <el-descriptions-item label="电话">{{ d.phone || '-' }}</el-descriptions-item>
              <el-descriptions-item label="邮箱">{{ d.email || '-' }}</el-descriptions-item>
              <el-descriptions-item label="网址">{{ d.website || '-' }}</el-descriptions-item>
              <el-descriptions-item label="所属部门">{{ d.deptName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="贸易条款"><DictTag v-if="d.tradeTerm" type="sys_trade_term" :value="d.tradeTerm" /><span v-else>-</span></el-descriptions-item>
              <el-descriptions-item label="税率">{{ formatPercent(d.purchaseTaxRate) }}</el-descriptions-item>
              <el-descriptions-item label="发票类型">{{ labelOf(INVOICE_TYPE_OPTIONS, d.invoiceType) }}</el-descriptions-item>
              <el-descriptions-item label="默认交货期">{{ d.leadTimeDays ?? '-' }} 天</el-descriptions-item>
              <el-descriptions-item label="准入日期">{{ d.qualifiedAt || '-' }}</el-descriptions-item>
              <el-descriptions-item label="创建">{{ d.createdByName ?? '-' }} {{ d.createdAt }}</el-descriptions-item>
              <el-descriptions-item label="备注" :span="2">{{ d.remark || '-' }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <el-tab-pane label="联系人与银行" name="contact">
            <div class="group-title">联系人</div>
            <el-table :data="d.contacts">
              <el-table-column prop="name" label="姓名" width="120" />
              <el-table-column prop="title" label="职务" width="120" />
              <el-table-column prop="role" label="角色" width="100" />
              <el-table-column prop="phone" label="电话" width="140" />
              <el-table-column prop="mobile" label="手机" width="140" />
              <el-table-column prop="email" label="邮箱" min-width="180" />
              <el-table-column label="主联系人" width="90"><template #default="{ row }">{{ row.isPrimary ? '是' : '' }}</template></el-table-column>
              <template #empty><ErpEmpty compact /></template>
            </el-table>
            <div class="group-title">银行账户</div>
            <el-table :data="d.banks">
              <el-table-column prop="bankName" label="开户行" min-width="180" />
              <el-table-column prop="accountName" label="户名" min-width="160" />
              <el-table-column prop="accountNo" label="账号" min-width="180" />
              <el-table-column prop="swift" label="SWIFT" width="120" />
              <el-table-column prop="currency" label="币别" width="80" />
              <el-table-column label="默认" width="70"><template #default="{ row }">{{ row.isDefault ? '是' : '' }}</template></el-table-column>
              <template #empty><ErpEmpty compact /></template>
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`资质(${d.certs.length})`" name="cert">
            <el-table :data="d.certs">
              <el-table-column label="类型" width="160"><template #default="{ row }"><DictTag type="pur_cert_type" :value="row.certType" /></template></el-table-column>
              <el-table-column prop="certNo" label="证书号" width="160" />
              <el-table-column prop="issueDate" label="发证日期" width="120" />
              <el-table-column prop="expireDate" label="到期日期" width="120" />
              <el-table-column label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.certStatus" :map="CERT_STATUS" /></template></el-table-column>
              <el-table-column prop="fileName" label="文件" min-width="180" />
              <el-table-column prop="remark" label="备注" min-width="140" />
              <template #empty><ErpEmpty compact /></template>
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`可供物料(${d.materials.length})`" name="material">
            <el-table :data="d.materials">
              <el-table-column label="物料编码" width="140">
                <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/engineering/material/${row.materialId}`)">{{ row.materialCode }}</el-link></template>
              </el-table-column>
              <el-table-column prop="materialName" label="名称" min-width="160" />
              <el-table-column prop="materialSpec" label="规格" min-width="160" />
              <el-table-column prop="supplierPartNo" label="供应商料号" width="140" />
              <el-table-column label="供货状态" width="90"><template #default="{ row }"><StatusTag :value="row.supplyStatus" :map="SUPPLY_STATUS" /></template></el-table-column>
              <el-table-column label="默认" width="60"><template #default="{ row }">{{ row.isDefault ? '是' : '' }}</template></el-table-column>
              <el-table-column prop="leadTimeDays" label="交期(天)" width="90" align="right" />
              <el-table-column prop="moq" label="MOQ" width="90" align="right" />
              <el-table-column prop="mpq" label="MPQ" width="90" align="right" />
              <template #empty><ErpEmpty compact /></template>
            </el-table>
          </el-tab-pane>

          <el-tab-pane v-if="canPrice" label="价格" name="price">
            <ErpTable :columns="priceColumns" :data="prices ?? []" :loading="!prices" no-toolbar />
          </el-tab-pane>
          <el-tab-pane label="采购订单" name="order">
            <ErpTable :columns="orderColumns" :data="orders ?? []" :loading="!orders" no-toolbar />
          </el-tab-pane>
          <el-tab-pane label="到货与质量" name="quality">
            <template v-if="quality">
              <p class="tab-tip">最近 12 个月检验批次 {{ quality.lotCount }}，合格 {{ quality.passCount }}，合格率 {{ quality.passRate ? `${quality.passRate}%` : '-' }}</p>
              <el-table :data="quality.lots">
                <el-table-column prop="receiptNo" label="到货单" width="160">
                  <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/purchase/receipt/${row.receiptId}`)">{{ row.receiptNo }}</el-link></template>
                </el-table-column>
                <el-table-column prop="arrivalDate" label="到货日期" width="110" />
                <el-table-column prop="materialCode" label="物料" width="130" />
                <el-table-column prop="materialName" label="名称" min-width="160" />
                <el-table-column prop="qty" label="数量" width="100" align="right" />
                <el-table-column label="检验结果" width="100"><template #default="{ row }"><StatusTag :value="row.inspectStatus" :map="INSPECT_STATUS" /></template></el-table-column>
                <el-table-column prop="qualifiedQty" label="合格" width="90" align="right" />
                <el-table-column prop="rejectedQty" label="不合格" width="90" align="right" />
                <el-table-column prop="inspectionNo" label="检验单" width="150" />
                <template #empty><ErpEmpty compact /></template>
              </el-table>
            </template>
            <el-skeleton v-else :rows="4" animated />
          </el-tab-pane>
          <el-tab-pane label="评估记录" name="score">
            <ErpTable :columns="scoreColumns" :data="scores ?? []" :loading="!scores" no-toolbar />
          </el-tab-pane>
          <el-tab-pane label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="PUR_SUPPLIER_QUALIFY" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="PUR_SUPPLIER" :biz-id="id" :status-map="SUPPLIER_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="PUR_SUPPLIER" :biz-id="id" :editable="d.status !== 'ELIMINATED'" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
.alert { margin-bottom: var(--erp-space-4); }
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-4); }
.tab-tip { margin: 0 0 var(--erp-space-3); color: var(--erp-color-text-secondary); }
.group-title { margin: var(--erp-space-3) 0; }
</style>
