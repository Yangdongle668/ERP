<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction, TableColumn } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { labelOf, MATERIAL_DOC_STATUS, RETURN_TYPE_OPTIONS, returnApi, type ReturnDetail, type ReturnLine } from '../api/production'

defineOptions({ name: 'MfgReturnDetail' })

/** 退料单详情（需求 09-03 3.6，T5）：提交后生成入库单，仓库确认后回写订单已退数量 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<ReturnDetail>()

async function load() {
  d.value = await returnApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}
const s = computed(() => d.value?.status)

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'mfg:return:delete', visible: () => s.value === 'DRAFT', confirm: `确定删除退料单「${d.value?.docNo}」吗？`,
    handler: async () => {
      await returnApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/production/return')
    } },
  { key: 'withdraw', label: '撤回', permission: 'mfg:return:submit', visible: () => s.value === 'APPROVED',
    confirm: '撤回后作废已生成的入库单（仓库未确认时），退料单回到草稿，确定吗？', handler: async () => {
      await returnApi.withdraw(id.value)
      ElMessage.success('已撤回')
      load()
    } },
  { key: 'edit', label: '编辑', permission: 'mfg:return:update', visible: () => s.value === 'DRAFT', handler: () => router.push(`/production/return/${id.value}/edit`) },
  { key: 'submit', label: '提交', type: 'primary', permission: 'mfg:return:submit', visible: () => s.value === 'DRAFT', handler: async () => {
    await returnApi.submit(id.value)
    ElMessage.success('已提交，等待仓库确认入库')
    load()
  } }
])

const columns = computed<TableColumn<ReturnLine>[]>(() => [
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'code', label: '物料编码', width: 130 },
  { prop: 'name', label: '名称', minWidth: 150 },
  { prop: 'spec', label: '规格', width: 130 },
  { prop: 'qty', label: '退料数量', width: 100, type: 'qty', uomProp: 'uom' },
  { prop: 'receivedQty', label: '实收数量', width: 100, type: 'qty', uomProp: 'uom' },
  { prop: 'uom', label: '单位', width: 60 },
  { prop: 'batchNo', label: '批次', width: 130 },
  ...(d.value?.returnType === 'DEFECT' ? [{ prop: 'defectDesc', label: '不良描述', minWidth: 180 }] : [])
])

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? '退料单'" :status="d?.status" :status-map="MATERIAL_DOC_STATUS" :actions="actions" @back="router.push('/production/return')">
        <template #extra><ErpBadge v-if="d" :type="d.returnType === 'DEFECT' ? 'danger' : 'success'" :dot="false">{{ labelOf(RETURN_TYPE_OPTIONS, d.returnType) }}</ErpBadge></template>
        <template #actions-prefix>
          <PrintButton v-if="d && s !== 'VOIDED'" biz-type="MFG_RETURN" :ids="[id]" permission="mfg:return:print" />
        </template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <el-descriptions :column="4">
          <el-descriptions-item label="生产订单">
            <el-link type="primary" underline="never" @click="router.push(`/production/prod-order/${d.prodOrderId}`)">{{ d.prodOrderNo }}</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="产品">{{ d.productCode }} {{ d.productName }}</el-descriptions-item>
          <el-descriptions-item label="退入仓库">{{ d.warehouseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="单据日期">{{ d.docDate }}</el-descriptions-item>
          <el-descriptions-item label="入库单">{{ d.stockInNos || '-' }}</el-descriptions-item>
          <el-descriptions-item label="退料人">{{ d.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>
      <ErpPanel flush>
        <el-tabs class="detail-tabs">
          <el-tab-pane :label="`明细(${d.lines.length})`"><ErpTable :columns="columns" :data="d.lines" storage-key="mfg.return-lines" /></el-tab-pane>
          <el-tab-pane label="操作日志" lazy><OperationLogTable biz-type="MFG_RETURN" :biz-id="id" :status-map="MATERIAL_DOC_STATUS" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
</style>
