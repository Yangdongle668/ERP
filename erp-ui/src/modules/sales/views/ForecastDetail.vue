<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatDateTime, formatQty } from '@/utils/format'
import { FORECAST_STATUS, forecastApi, num, type ForecastCell, type ForecastDetail, type ForecastRowResp } from '../api/sales'

defineOptions({ name: 'SalForecastDetail' })

/** 销售预测详情（需求 04-05 3.3～3.5，T5）：每格显示预测数 / 已冲销数，点击查看冲销来源订单 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<ForecastDetail>()

async function load() {
  d.value = await forecastApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}
const s = computed(() => d.value?.status)

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'sales:forecast:delete', visible: () => s.value === 'DRAFT', confirm: `确定删除预测「${d.value?.docNo}」吗？`,
    handler: async () => {
      await forecastApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/sales/forecast')
    } },
  { key: 'close', label: '关闭', permission: 'sales:forecast:publish', visible: () => s.value === 'APPROVED', reasonRequired: true, reasonTitle: '关闭原因',
    confirm: '关闭后预测不再参与 PMC 需求计算和订单冲销。', handler: async (reason) => {
      await forecastApi.close(id.value, reason!)
      ElMessage.success('已关闭')
      load()
    } },
  { key: 'copy', label: '复制', permission: 'sales:forecast:create', handler: async () => {
    const nid = await forecastApi.copy(id.value)
    ElMessage.success('已复制为新草稿')
    router.push(`/sales/forecast/${nid}/edit`)
  } },
  { key: 'revise', label: '修订', permission: 'sales:forecast:create', visible: () => s.value === 'APPROVED',
    confirm: '修订会生成新草稿，新版本发布后本预测自动关闭，确定吗？', handler: async () => {
      const nid = await forecastApi.revise(id.value)
      ElMessage.success('已生成修订草稿')
      router.push(`/sales/forecast/${nid}/edit`)
    } },
  { key: 'edit', label: '编辑', permission: 'sales:forecast:update', visible: () => s.value === 'DRAFT', handler: () => router.push(`/sales/forecast/${id.value}/edit`) },
  { key: 'publish', label: '发布', type: 'primary', permission: 'sales:forecast:publish', visible: () => s.value === 'DRAFT',
    confirm: '发布后预测进入 PMC 需求计算，并开始被销售订单冲销，确定吗？', handler: async () => {
      await forecastApi.publish(id.value)
      ElMessage.success('已发布')
      load()
    } }
])

const fmtPeriod = (p: string) => `${p.slice(0, 4)}-${p.slice(4)}`
const cellOf = (r: unknown, p: string) => (r as ForecastRowResp).cells.find((c) => c.period === p)
const colTotal = (p: string) => (d.value?.rows ?? []).reduce((s, r) => s + num(cellOf(r, p)?.qty), 0)

// ---------- 冲销明细 ----------
const consVisible = ref(false)
const consTitle = ref('')
const cons = ref<Awaited<ReturnType<typeof forecastApi.consumptions>>>([])
async function showConsumptions(row: unknown, c?: ForecastCell) {
  const r = row as ForecastRowResp
  if (!c?.lineId || !(num(c.consumedQty) > 0)) return
  cons.value = await forecastApi.consumptions(id.value, c.lineId)
  consTitle.value = `冲销明细 - ${r.materialCode} ${fmtPeriod(c.period)}`
  consVisible.value = true
}

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? '销售预测'" :status="d?.status" :status-map="FORECAST_STATUS" :actions="actions" @back="router.push('/sales/forecast')" />
    </template>

    <template v-if="d">
      <ErpPanel>
        <el-descriptions :column="4">
          <el-descriptions-item label="标题">{{ d.title }}</el-descriptions-item>
          <el-descriptions-item label="期间">{{ fmtPeriod(d.startPeriod) }} ~ {{ fmtPeriod(d.endPeriod) }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{ d.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ d.publishedAt ? formatDateTime(d.publishedAt, true) : '未发布' }}</el-descriptions-item>
          <el-descriptions-item label="修订自">
            <el-link v-if="d.revisedFromId" type="primary" underline="never" @click="router.push(`/sales/forecast/${d.revisedFromId}`)">{{ d.revisedFromNo }}</el-link>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="d.closeReason" label="关闭原因">{{ d.closeReason }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel :title="`预测明细（${d.rows.length} 行）`" description="每格：预测数量 / 已冲销数量；点击已冲销数量查看来源订单">
        <el-table :data="d.rows" border max-height="600"
                  show-summary :summary-method="() => ['合计', '', ...d!.periods.map((p) => formatQty(colTotal(p))), formatQty(d!.rows.reduce((s, r) => s + num(r.totalQty), 0)), '']">
          <el-table-column label="客户" width="160" fixed="left"><template #default="{ row }">{{ row.customerName || '全部客户' }}</template></el-table-column>
          <el-table-column label="物料" width="220" fixed="left"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
          <el-table-column v-for="p in d.periods" :key="p" :label="fmtPeriod(p)" width="130" align="right">
            <template #default="{ row }">
              <template v-if="cellOf(row, p)">
                <span class="num">{{ formatQty(cellOf(row, p)?.qty) }}</span>
                <el-link v-if="num(cellOf(row, p)?.consumedQty) > 0" type="primary" underline="never" class="consumed" @click="showConsumptions(row, cellOf(row, p))">
                  / {{ formatQty(cellOf(row, p)?.consumedQty) }}
                </el-link>
              </template>
            </template>
          </el-table-column>
          <el-table-column label="合计" width="140" align="right">
            <template #default="{ row }">{{ formatQty(row.totalQty) }} / {{ formatQty(row.totalConsumed) }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="120" />
        </el-table>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs class="detail-tabs">
          <el-tab-pane label="操作日志"><OperationLogTable biz-type="SAL_FORECAST" :biz-id="id" :status-map="FORECAST_STATUS" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="consVisible" :title="consTitle" width="720px" append-to-body>
      <el-table :data="cons">
        <el-table-column label="订单" width="180">
          <template #default="{ row }">
            <el-link v-if="row.orderId" type="primary" underline="never" @click="router.push(`/sales/order/${row.orderId}`)">{{ row.orderNo }}</el-link>
            <span v-if="row.lineNo"> 行 {{ row.lineNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户" min-width="140" />
        <el-table-column label="冲销数量" width="120" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
        <el-table-column label="时间" width="160"><template #default="{ row }">{{ formatDateTime(row.createdAt, true) }}</template></el-table-column>
      </el-table>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
.consumed { margin-left: var(--erp-space-1); }
</style>
