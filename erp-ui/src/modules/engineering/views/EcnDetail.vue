<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatQty } from '@/utils/format'
import {
  ACTION_OPTIONS, DEPT_ROLE_OPTIONS, ecnApi, ECN_STATUS, HANDLING_OPTIONS, IMPACT_TYPE_OPTIONS, MODE_OPTIONS,
  type EcnDetail, type EcnTaskRow
} from '../api/ecn'
import { labelOf } from '../api/material'

defineOptions({ name: 'EngEcnDetail' })

/** ECN 详情（需求 05-05 3.3，T5）：按钮矩阵见需求；页签：变更明细、影响分析、执行确认、审批记录、操作日志、附件 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<EcnDetail>()
const activeTab = ref('lines')

async function load() {
  d.value = await ecnApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}
const s = computed(() => d.value?.status)

async function run(p: Promise<unknown>, msg: string) {
  await p
  ElMessage.success(msg)
  load()
}
const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'eng:ecn:delete', visible: () => s.value === 'DRAFT', confirm: `确定删除 ${d.value?.docNo} 吗？`,
    handler: async () => {
      await ecnApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/engineering/ecn')
    } },
  { key: 'void', label: '作废', permission: 'eng:ecn:void', visible: () => s.value === 'DRAFT', reasonRequired: true,
    handler: (reason) => run(ecnApi.void(id.value, reason), '已作废') },
  { key: 'edit', label: '编辑', permission: 'eng:ecn:update', visible: () => s.value === 'DRAFT', handler: () => router.push(`/engineering/ecn/${id.value}/edit`) },
  { key: 'submit', label: '提交', type: 'primary', permission: 'eng:ecn:submit', visible: () => s.value === 'DRAFT',
    handler: async () => {
      const r = await ecnApi.submit(id.value)
      if (r.keyPartWarning) ElMessage.warning(`${r.keyPartWarning}（已自动增加“认证评估”执行任务）`)
      ElMessage.success(r.status === 'PENDING_APPROVAL' ? '已提交审批' : '提交成功，已审核')
      load()
    } },
  { key: 'effect', label: '立即生效', type: 'primary', permission: 'eng:ecn:effect', visible: () => s.value === 'APPROVED',
    confirm: '生效后新 BOM 版本成为默认版本，MRP 和新建生产订单将使用新版本。确定生效吗？', handler: () => run(ecnApi.effect(id.value), '已生效') },
  { key: 'close', label: '关闭', type: 'primary', permission: 'eng:ecn:close', visible: () => s.value === 'IN_PROGRESS' && d.value?.pendingTasks === 0,
    confirm: '所有执行任务已完成，确定关闭 ECN 吗？', handler: () => run(ecnApi.close(id.value), '已关闭') }
])

async function done(t: EcnTaskRow) {
  const { value } = await ElMessageBox.prompt(t.content, '确认完成', { inputPlaceholder: '处理说明（可选）', inputValidator: (v) => !v || v.length <= 512 || '最多 512 个字' })
  await run(ecnApi.taskDone(id.value, t.id!, value || undefined), '已确认完成')
}

const steps = [
  { status: 'DRAFT', label: '草稿' },
  { status: 'PENDING_APPROVAL', label: '待审批' },
  { status: 'APPROVED', label: '已审核' },
  { status: 'IN_PROGRESS', label: '已生效' },
  { status: 'COMPLETED', label: '已关闭' }
]
const pct = (v?: string) => (v === undefined || v === null ? '' : `${Number((Number(v) * 100).toFixed(4))}%`)
const handling = (type: string, v?: string) => labelOf(HANDLING_OPTIONS[type] ?? [], v)
const myTasks = computed(() => d.value?.tasks.filter((t) => t.mine) ?? [])

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d ? `${d.docNo} ${d.title}` : 'ECN'" :status="d?.status" :status-map="ECN_STATUS" :actions="actions" @back="router.push('/engineering/ecn')">
        <template #extra>
          <ErpBadge v-if="d?.urgency === 'URGENT'" type="danger">紧急</ErpBadge>
          <ErpBadge v-if="d?.keyPart" type="warning">涉及认证关键件</ErpBadge>
        </template>
        <template #actions-prefix>
          <ApprovalActions v-if="d" biz-type="ENG_ECN" :biz-id="id" @changed="load" />
          <PrintButton v-if="d" biz-type="ENG_ECN" :ids="[id]" permission="eng:ecn:print" />
        </template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <el-alert v-if="d.status === 'VOIDED'" type="info" :closable="false" show-icon title="此 ECN 已作废" class="gap" />
        <DocSteps v-else :steps="steps" :current="d.status" />
        <el-alert v-if="myTasks.length && d.status === 'IN_PROGRESS'" type="warning" :closable="false" show-icon class="gap"
                  :title="`你有 ${myTasks.length} 项执行任务待确认，请在“执行确认”页签处理`" />
        <el-descriptions :column="3" class="head">
          <el-descriptions-item label="变更类型"><DictTag type="eng_ecn_type" :value="d.ecnType" /></el-descriptions-item>
          <el-descriptions-item label="变更原因"><DictTag type="eng_ecn_reason" :value="d.reasonType" /></el-descriptions-item>
          <el-descriptions-item label="生效方式">{{ labelOf(MODE_OPTIONS, d.effectiveMode) }}{{ d.effectiveDate ? `（${d.effectiveDate}）` : '' }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ d.customerName ?? d.customerId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="发起人">{{ d.createdByName ?? '-' }} {{ d.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="单据日期">{{ d.docDate }}</el-descriptions-item>
          <el-descriptions-item label="审核时间">{{ d.approvedAt ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="生效时间">{{ d.effectedAt ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="关闭时间">{{ d.closedAt ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="原因说明" :span="3"><span class="pre">{{ d.reason }}</span></el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane :label="`变更明细(${d.lines.length})`" name="lines">
            <el-table :data="d.lines">
              <el-table-column prop="lineNo" label="行" width="50" align="right" />
              <el-table-column label="BOM" width="160">
                <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/engineering/bom/${row.bomId}`)">{{ row.bomNo }}</el-link></template>
              </el-table-column>
              <el-table-column prop="parentName" label="父件" min-width="140" show-overflow-tooltip />
              <el-table-column label="动作" width="90"><template #default="{ row }">{{ labelOf(ACTION_OPTIONS, row.action) }}</template></el-table-column>
              <el-table-column label="原子件" min-width="170" show-overflow-tooltip><template #default="{ row }">{{ row.oldCode ? `${row.oldCode} ${row.oldName}` : '-' }}</template></el-table-column>
              <el-table-column label="新子件" min-width="170" show-overflow-tooltip><template #default="{ row }">{{ row.newCode ? `${row.newCode} ${row.newName}` : '-' }}</template></el-table-column>
              <el-table-column label="用量" width="130" align="right">
                <template #default="{ row }"><span class="num">{{ row.oldQtyPer ? formatQty(row.oldQtyPer) : '-' }} → {{ row.newQtyPer ? formatQty(row.newQtyPer) : '-' }}</span></template>
              </el-table-column>
              <el-table-column label="损耗" width="120" align="right">
                <template #default="{ row }"><span class="num">{{ pct(row.oldScrapRate) || '-' }} → {{ pct(row.newScrapRate) || '不变' }}</span></template>
              </el-table-column>
              <el-table-column prop="positionNo" label="新位号" min-width="110" show-overflow-tooltip />
              <el-table-column label="新 BOM 版本" width="140">
                <template #default="{ row }">
                  <el-link v-if="row.newBomId" type="primary" underline="never" @click="router.push(`/engineering/bom/${row.newBomId}`)">{{ row.newBomNo }}</el-link>
                  <span v-else class="text-muted">审批后生成</span>
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="110" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`影响分析(${d.impacts.length})`" name="impacts">
            <el-table :data="d.impacts">
              <el-table-column label="影响类型" width="130"><template #default="{ row }">{{ labelOf(IMPACT_TYPE_OPTIONS, row.impactType) }}</template></el-table-column>
              <el-table-column label="物料" min-width="180"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
              <el-table-column prop="docNo" label="相关单据" width="150" />
              <el-table-column label="数量" width="120" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.qty) }} {{ row.uom }}</span></template></el-table-column>
              <el-table-column label="处理方式" width="120"><template #default="{ row }">{{ handling(row.impactType, row.handling) || '-' }}</template></el-table-column>
              <el-table-column prop="handlingRemark" label="说明" min-width="160" show-overflow-tooltip />
              <template #empty><ErpEmpty compact :description="d.analyzed ? '没有影响' : '尚未分析影响'" /></template>
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`执行确认(${d.tasks.length - d.pendingTasks}/${d.tasks.length})`" name="tasks">
            <el-table :data="d.tasks">
              <el-table-column label="执行部门" width="100"><template #default="{ row }">{{ labelOf(DEPT_ROLE_OPTIONS, row.deptRole) }}</template></el-table-column>
              <el-table-column prop="assigneeName" label="负责人" width="100"><template #default="{ row }">{{ row.assigneeName ?? '发起人' }}</template></el-table-column>
              <el-table-column prop="content" label="执行内容" min-width="240" show-overflow-tooltip />
              <el-table-column label="状态" width="90">
                <template #default="{ row }"><ErpBadge :type="row.taskStatus === 'DONE' ? 'success' : 'warning'">{{ row.taskStatus === 'DONE' ? '已完成' : '待处理' }}</ErpBadge></template>
              </el-table-column>
              <el-table-column prop="doneRemark" label="处理说明" min-width="160" show-overflow-tooltip />
              <el-table-column label="完成" width="170"><template #default="{ row }">{{ row.doneByName ?? '' }} {{ row.doneAt?.slice(0, 16) ?? '' }}</template></el-table-column>
              <el-table-column label="" width="100" align="center">
                <template #default="{ row }">
                  <el-button v-if="row.mine && d.status === 'IN_PROGRESS'" link type="primary" @click="done(row as EcnTaskRow)">确认完成</el-button>
                </template>
              </el-table-column>
              <template #empty><ErpEmpty compact description="没有执行任务" /></template>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="ENG_ECN" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="ENG_ECN" :biz-id="id" :status-map="ECN_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="ENG_ECN" :biz-id="id" :editable="d.status === 'DRAFT'" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
.gap { margin-bottom: var(--erp-space-3); }
.head { margin-top: var(--erp-space-4); }
.pre { white-space: pre-wrap; }
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
</style>
