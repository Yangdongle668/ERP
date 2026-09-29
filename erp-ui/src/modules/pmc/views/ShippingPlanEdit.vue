<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatQty } from '@/utils/format'
import { num, PLAN_STATUS, SHIP_LINE_STATUS, shippingPlanApi, type ShipPlanDetail, type ShipPlanLine } from '../api/pmc'

defineOptions({ name: 'PmcShippingPlanEdit' })

/** 出货计划编辑（需求 06-08 3.2，T4）：生成计划、调整计划数量 / 日期 / 运输方式；发布后仍可修改未通知的行 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<ShipPlanDetail>()
const lines = ref<ShipPlanLine[]>([])
const saving = ref(false)
const dirty = ref(false)

async function load() {
  d.value = await shippingPlanApi.get(id.value)
  lines.value = d.value.lines.map((l) => ({ ...l }))
  dirty.value = false
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}
onMounted(load)
const editable = computed(() => d.value && d.value.planStatus !== 'CLOSED')
const draft = computed(() => d.value?.planStatus === 'DRAFT')

async function save() {
  if (!d.value) return
  for (const l of lines.value) {
    if (num(l.planQty) > num(l.openQty)) return ElMessage.warning(`${l.orderNo} 行 ${l.orderLineNo}：计划数量不能超过未出货数量 ${formatQty(l.openQty)}`)
  }
  saving.value = true
  try {
    await shippingPlanApi.update(id.value, {
      planWeek: d.value.planWeek, remark: d.value.remark, version: d.value.version,
      lines: lines.value.map((l) => ({ id: l.id, orderLineId: l.orderLineId, planQty: l.planQty ?? '0', planShipDate: l.planShipDate,
        transportMode: l.transportMode, remark: l.remark?.trim() || undefined }))
    })
    ElMessage.success('保存成功')
    load()
  } finally {
    saving.value = false
  }
}
async function generate() {
  if (dirty.value) await save()
  d.value = await shippingPlanApi.generate(id.value)
  lines.value = d.value.lines.map((l) => ({ ...l }))
  ElMessage.success('已生成')
}
function removeLine(i: number) {
  lines.value.splice(i, 1)
  dirty.value = true
}

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'pmc:shipping-plan:update', visible: () => draft.value, confirm: '确定删除该出货计划吗？', handler: async () => {
    await shippingPlanApi.remove(id.value)
    tabs.remove([tabKeyOf(route)])
    router.push('/pmc/shipping-plan')
  } },
  { key: 'close', label: '关闭', permission: 'pmc:shipping-plan:publish', visible: () => d.value?.planStatus === 'PUBLISHED', confirm: '确定关闭吗？',
    handler: async () => {
      await shippingPlanApi.close(id.value)
      load()
    } },
  { key: 'generate', label: '生成计划', permission: 'pmc:shipping-plan:update', visible: () => draft.value, handler: generate },
  { key: 'save', label: '保存', permission: 'pmc:shipping-plan:update', visible: () => !!editable.value, handler: save },
  { key: 'publish', label: '发布', type: 'primary', permission: 'pmc:shipping-plan:publish', visible: () => draft.value,
    confirm: '发布后出货模块可据此生成出货通知，确定吗？', handler: async () => {
      if (dirty.value) await save()
      await shippingPlanApi.publish(id.value)
      ElMessage.success('已发布')
      load()
    } }
])
const rowClass = (p: { row: ShipPlanLine }) => (num(p.row.planQty) < num(p.row.openQty) ? 'short' : '')
const totalPlan = computed(() => lines.value.reduce((s, l) => s + num(l.planQty), 0))
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d ? `${d.docNo}（${d.planWeek}）` : '出货计划'" :status="d?.planStatus" :status-map="PLAN_STATUS" :actions="actions"
                     @back="router.push('/pmc/shipping-plan')" />
    </template>
    <ErpPanel v-if="d" :title="`明细（${lines.length} 行，计划 ${formatQty(totalPlan)}）`" description="可用不足的行橙色；已生成出货通知的行不能删除">
      <el-table :data="lines" max-height="640" :row-class-name="rowClass">
        <el-table-column label="订单" width="170">
          <template #default="{ row }">
            <el-link type="primary" underline="never" @click="router.push(`/sales/order/${row.orderId}`)">{{ row.orderNo }}</el-link> 行 {{ row.orderLineNo }}
          </template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户" width="120" show-overflow-tooltip />
        <el-table-column label="物料" min-width="180"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
        <el-table-column prop="dueDate" label="承诺交期" width="105" />
        <el-table-column label="未出货" width="90" align="right"><template #default="{ row }">{{ formatQty(row.openQty) }}</template></el-table-column>
        <el-table-column label="可用" width="90" align="right"><template #default="{ row }">{{ formatQty(row.availableQty) }}</template></el-table-column>
        <el-table-column label="计划数量" width="130">
          <template #default="{ row }">
            <QtyInput v-if="editable" v-model="row.planQty" :uom="row.baseUom" @change="dirty = true" />
            <span v-else>{{ formatQty(row.planQty) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="计划日期" width="150">
          <template #default="{ row }">
            <el-date-picker v-if="editable" v-model="row.planShipDate" value-format="YYYY-MM-DD" :clearable="false" class="w-full" @change="dirty = true" />
            <span v-else>{{ row.planShipDate }}</span>
          </template>
        </el-table-column>
        <el-table-column label="运输方式" width="120">
          <template #default="{ row }">
            <DictSelect v-if="editable" v-model="row.transportMode" type="pmc_transport_mode" @change="dirty = true" />
            <DictTag v-else type="pmc_transport_mode" :value="row.transportMode" />
          </template>
        </el-table-column>
        <el-table-column label="已通知" width="90" align="right"><template #default="{ row }">{{ formatQty(row.noticedQty) }}</template></el-table-column>
        <el-table-column label="状态" width="80"><template #default="{ row }"><StatusTag :value="row.lineStatus" :map="SHIP_LINE_STATUS" /></template></el-table-column>
        <el-table-column label="备注" min-width="120">
          <template #default="{ row }"><el-input v-if="editable" v-model="row.remark" maxlength="256" @change="dirty = true" /><span v-else>{{ row.remark }}</span></template>
        </el-table-column>
        <el-table-column v-if="editable" label="" width="60">
          <template #default="{ row, $index }"><el-button v-if="!num(row.noticedQty)" link type="danger" @click="removeLine($index)">删除</el-button></template>
        </el-table-column>
        <template #empty><ErpEmpty compact description="点击“生成计划”带出本周到期的订单行" /></template>
      </el-table>
    </ErpPanel>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
:deep(.short) { background: var(--erp-color-warning-bg); }
</style>
