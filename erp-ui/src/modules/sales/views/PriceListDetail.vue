<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction, TableColumn } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatDateTime } from '@/utils/format'
import { labelOf, PRICE_LIST_STATUS, priceListApi, SCOPE_OPTIONS, submitText, type PriceItem, type PriceListDetail } from '../api/sales'

defineOptions({ name: 'SalPriceListDetail' })

/** 销售价格表详情（需求 04-01 3.3，T5） */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<PriceListDetail>()
const activeTab = ref('items')
const importRef = ref<{ open: () => void }>()

async function load() {
  d.value = await priceListApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}
const s = computed(() => d.value?.status)

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'sales:price-list:delete', visible: () => s.value === 'DRAFT', confirm: `确定删除价格表「${d.value?.docNo}」吗？`,
    handler: async () => {
      await priceListApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/sales/price-list')
    } },
  { key: 'close', label: '关闭', permission: 'sales:price-list:update', visible: () => s.value === 'APPROVED', reasonRequired: true, reasonTitle: '关闭原因',
    confirm: '关闭后该价格表不再参与自动取价。', handler: async (reason) => {
      await priceListApi.close(id.value, reason!)
      ElMessage.success('已关闭')
      load()
    } },
  { key: 'copy', label: '复制', permission: 'sales:price-list:create', handler: async () => {
    const nid = await priceListApi.copy(id.value)
    ElMessage.success('已复制为新草稿')
    router.push(`/sales/price-list/${nid}/edit`)
  } },
  { key: 'import', label: '导入明细', permission: 'sales:price-list:import', visible: () => s.value === 'DRAFT', handler: () => importRef.value?.open() },
  { key: 'edit', label: '编辑', permission: 'sales:price-list:update', visible: () => s.value === 'DRAFT', handler: () => router.push(`/sales/price-list/${id.value}/edit`) },
  { key: 'submit', label: '提交', type: 'primary', permission: 'sales:price-list:submit', visible: () => s.value === 'DRAFT',
    handler: async () => {
      ElMessage.success(submitText((await priceListApi.submit(id.value)).status))
      load()
    } }
])

const steps = [
  { status: 'DRAFT', label: '草稿' },
  { status: 'PENDING_APPROVAL', label: '待审批' },
  { status: 'APPROVED', label: '已生效' }
]

const itemColumns = computed<TableColumn<PriceItem>[]>(() => [
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 150 },
  { prop: 'materialSpec', label: '规格', minWidth: 140 },
  { prop: 'uom', label: '单位', width: 60 },
  { prop: 'minQty', label: '起订量', width: 100, type: 'qty' },
  { prop: 'price', label: d.value?.taxIncluded ? '含税单价' : '不含税单价', width: 120, type: 'price' },
  ...(d.value?.costVisible ? [
    { prop: 'costPrice', label: '成本价', width: 110, type: 'price' } as TableColumn<PriceItem>,
    { prop: 'marginRate', label: '毛利率', width: 100, slot: true } as TableColumn<PriceItem>
  ] : []),
  { prop: 'remark', label: '备注', minWidth: 120 }
])
const asItem = (r: unknown) => r as PriceItem

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? '价格表'" :status="d?.status" :status-map="PRICE_LIST_STATUS" :actions="actions" @back="router.push('/sales/price-list')">
        <template #actions-prefix><ApprovalActions v-if="d" biz-type="SAL_PRICE_LIST" :biz-id="id" @changed="load" /></template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <DocSteps :steps="steps" :current="d.status" :terminal="{ CLOSED: `已关闭：${d.closeReason ?? ''}`, VOIDED: '已作废' }" />
        <el-descriptions :column="4" class="head">
          <el-descriptions-item label="名称">{{ d.name }}</el-descriptions-item>
          <el-descriptions-item label="适用范围">{{ labelOf(SCOPE_OPTIONS, d.scope) }}</el-descriptions-item>
          <el-descriptions-item label="客户 / 等级">
            <span v-if="d.scope === 'CUSTOMER'">{{ d.customerName }}</span>
            <DictTag v-else-if="d.scope === 'LEVEL'" type="crm_customer_level" :value="d.customerLevel" />
            <span v-else>全部客户</span>
          </el-descriptions-item>
          <el-descriptions-item label="币别">{{ d.currency }}（{{ d.taxIncluded ? '含税' : '不含税' }}）</el-descriptions-item>
          <el-descriptions-item label="有效期">{{ d.effectiveFrom }} ~ {{ d.effectiveTo ?? '长期' }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ d.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(d.createdAt, true) }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane :label="`价格明细(${d.items.length})`" name="items">
            <ErpTable :columns="itemColumns" :data="d.items" storage-key="sal.price-list-items">
              <template #col-marginRate="{ row }">
                <span :class="{ 'text-danger': asItem(row).belowFloor }">{{ asItem(row).marginRate != null ? `${(Number(asItem(row).marginRate) * 100).toFixed(2)}%` : '-' }}</span>
                <ErpBadge v-if="asItem(row).belowFloor" type="danger" :dot="false">低于底线</ErpBadge>
              </template>
            </ErpTable>
          </el-tab-pane>
          <el-tab-pane label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="SAL_PRICE_LIST" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="SAL_PRICE_LIST" :biz-id="id" :status-map="PRICE_LIST_STATUS" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <ImportDialog ref="importRef" title="导入价格明细" :base="`/sales/price-lists/${id}`" template-name="销售价格" @done="load" />
  </ErpPage>
</template>

<style scoped>
.head { margin-top: var(--erp-space-4); }
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
</style>
