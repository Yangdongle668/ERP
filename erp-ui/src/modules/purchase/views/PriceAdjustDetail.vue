<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction, TableColumn } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { labelOf, submitText } from '../api/common'
import { ADJUST_SOURCE_OPTIONS, ADJUST_STATUS, adjustApi, type AdjustDetail, type AdjustLine } from '../api/price'

defineOptions({ name: 'PurPriceAdjustDetail' })

/** 调价单详情（需求 07-02 3.3，T5） */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<AdjustDetail>()
const activeTab = ref('lines')

async function load() {
  d.value = await adjustApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}

const s = computed(() => d.value?.status)
const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'pur:price:adjust', visible: () => s.value === 'DRAFT', confirm: `确定删除调价单「${d.value?.docNo}」吗？删除后不可恢复。`,
    handler: async () => {
      await adjustApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/purchase/price')
    } },
  { key: 'void', label: '作废', permission: 'pur:price:adjust', visible: () => s.value === 'DRAFT', reasonRequired: true, reasonTitle: '作废原因',
    confirm: '作废后单据不可恢复，也不再参与任何统计。', handler: (reason) => run(adjustApi.void(id.value, reason!), '已作废') },
  { key: 'edit', label: '编辑', permission: 'pur:price:adjust', visible: () => s.value === 'DRAFT', handler: () => router.push(`/purchase/price/adjust/${id.value}/edit`) },
  { key: 'submit', label: '提交', type: 'primary', permission: 'pur:price:submit', visible: () => s.value === 'DRAFT',
    handler: async () => {
      const r = await adjustApi.submit(id.value)
      ElMessage.success(r.status === 'APPROVED' ? '提交成功，价格已生效' : submitText(r.status))
      load()
    } }
])

async function run(p: Promise<unknown>, msg: string) {
  await p
  ElMessage.success(msg)
  load()
}

const steps = [
  { status: 'DRAFT', label: '草稿' },
  { status: 'PENDING_APPROVAL', label: '待审批' },
  { status: 'APPROVED', label: '已生效' }
]

const columns: TableColumn<AdjustLine>[] = [
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 160 },
  { prop: 'materialSpec', label: '规格', minWidth: 140 },
  { prop: 'baseUom', label: '单位', width: 60 },
  { prop: 'minQty', label: '阶梯', width: 100, type: 'qty' },
  { prop: 'oldPrice', label: '原价', width: 100, type: 'price' },
  { prop: 'newPrice', label: '新价', width: 100, type: 'price' },
  { prop: 'taxRate', label: '税率', width: 80, type: 'percent' },
  { prop: 'changePct', label: '涨跌幅', width: 100, slot: true },
  { prop: 'effectiveFrom', label: '生效日期', width: 110, type: 'date' },
  { prop: 'effectiveTo', label: '失效日期', width: 110, type: 'date' },
  { prop: 'remark', label: '备注', minWidth: 120 }
]
const asLine = (r: unknown) => r as AdjustLine
const pct = (v?: string) => (v === undefined || v === null ? '-' : `${Number(v) > 0 ? '+' : ''}${(Number(v) * 100).toFixed(2)}%`)

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? '调价单'" :status="d?.status" :status-map="ADJUST_STATUS" :actions="actions" @back="router.push('/purchase/price')">
        <template #actions-prefix><ApprovalActions v-if="d" biz-type="PUR_PRICE_ADJUST" :biz-id="id" @changed="load" /></template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <DocSteps :steps="steps" :current="d.status" :terminal="{ VOIDED: '已作废' }" />
        <el-descriptions :column="3" class="head">
          <el-descriptions-item label="供应商">
            <el-link type="primary" underline="never" @click="router.push(`/purchase/supplier/${d.supplierId}`)">{{ d.supplierName }}</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="币别">{{ d.currency }}</el-descriptions-item>
          <el-descriptions-item label="来源">
            {{ labelOf(ADJUST_SOURCE_OPTIONS, d.source) }}
            <el-link v-if="d.rfqId" type="primary" underline="never" @click="router.push(`/purchase/rfq/${d.rfqId}`)">{{ d.rfqNo }}</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="调价原因">{{ d.adjustReason }}</el-descriptions-item>
          <el-descriptions-item label="单据日期">{{ d.docDate }}</el-descriptions-item>
          <el-descriptions-item label="经办人">{{ d.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane :label="`明细(${d.lines.length})`" name="lines">
            <ErpTable :columns="columns" :data="d.lines" no-toolbar>
              <template #col-changePct="{ row }">
                <span :class="['num', Number(asLine(row).changePct) > 0 ? 'text-danger' : Number(asLine(row).changePct) < 0 ? 'text-success' : '']">{{ pct(asLine(row).changePct) }}</span>
                <ErpBadge v-if="asLine(row).overThreshold" type="warning" :dot="false">超阈值</ErpBadge>
              </template>
            </ErpTable>
          </el-tab-pane>
          <el-tab-pane label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="PUR_PRICE_ADJUST" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="PUR_PRICE_ADJUST" :biz-id="id" :status-map="ADJUST_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="PUR_PRICE_ADJUST" :biz-id="id" :editable="d.status === 'DRAFT'" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
.head { margin-top: var(--erp-space-4); }
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
</style>
