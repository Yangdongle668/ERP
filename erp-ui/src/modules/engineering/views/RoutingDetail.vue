<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatQty } from '@/utils/format'
import { BOM_STATUS } from '../api/bom'
import { routingApi, WC_TYPE_OPTIONS, type RoutingDetail } from '../api/routing'
import { labelOf } from '../api/material'

defineOptions({ name: 'EngRoutingDetail' })

/** 工艺路线详情（需求 05-04 3.4，T5）：工序、按工作中心汇总、操作日志 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<RoutingDetail>()
const activeTab = ref('steps')

async function load() {
  d.value = await routingApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}
const s = computed(() => d.value?.status)
const isDefault = computed(() => !!d.value?.isDefault)

async function run(p: Promise<unknown>, msg: string) {
  await p
  ElMessage.success(msg)
  load()
}
const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'eng:routing:delete', visible: () => s.value === 'DRAFT', confirm: `确定删除 ${d.value?.docNo} 吗？`,
    handler: async () => {
      await routingApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/engineering/routing')
    } },
  { key: 'disable', label: '停用', permission: 'eng:routing:approve', visible: () => s.value === 'APPROVED' && !isDefault.value,
    confirm: '停用后不能恢复。确定停用吗？', handler: () => run(routingApi.disable(id.value), '已停用') },
  { key: 'unapprove', label: '反审核', permission: 'eng:routing:approve', visible: () => s.value === 'APPROVED' && !isDefault.value, reasonRequired: true,
    handler: (reason) => run(routingApi.unapprove(id.value, reason!), '已反审核') },
  { key: 'newVersion', label: '新建版本', permission: 'eng:routing:create', visible: () => s.value === 'APPROVED' || s.value === 'CLOSED',
    handler: async () => router.push(`/engineering/routing/${await routingApi.newVersion(id.value)}/edit`) },
  { key: 'setDefault', label: '设为默认', permission: 'eng:routing:set-default', visible: () => s.value === 'APPROVED' && !isDefault.value,
    handler: () => run(routingApi.setDefault(id.value), '已设为默认版本') },
  { key: 'edit', label: '编辑', permission: 'eng:routing:update', visible: () => s.value === 'DRAFT', handler: () => router.push(`/engineering/routing/${id.value}/edit`) },
  { key: 'approve', label: '审核', type: 'primary', permission: 'eng:routing:approve', visible: () => s.value === 'DRAFT', handler: () => run(routingApi.approve(id.value), '已审核') }
])

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d ? `${d.materialCode} ${d.materialName} V${d.version}` : '工艺路线'" :status="d?.status" :status-map="BOM_STATUS" :actions="actions"
                     @back="router.push('/engineering/routing')">
        <template #extra><ErpBadge v-if="isDefault" type="success">默认</ErpBadge></template>
      </DocPageHeader>
    </template>
    <template v-if="d">
      <ErpPanel>
        <el-descriptions :column="3">
          <el-descriptions-item label="物料">
            <el-link type="primary" underline="never" @click="router.push(`/engineering/material/${d.materialId}`)">{{ d.materialCode }}</el-link> {{ d.materialName }}
          </el-descriptions-item>
          <el-descriptions-item label="规格">{{ d.materialSpec || '-' }}</el-descriptions-item>
          <el-descriptions-item label="单号">{{ d.docNo }}</el-descriptions-item>
          <el-descriptions-item label="合计准备时间">{{ formatQty(d.totalSetupMinutes, 2) }} 分</el-descriptions-item>
          <el-descriptions-item label="合计标准工时">{{ formatQty(d.totalRunSeconds, 2) }} 秒/件</el-descriptions-item>
          <el-descriptions-item label="复制自">
            <el-link v-if="d.copiedFromId" type="primary" underline="never" @click="router.push(`/engineering/routing/${d.copiedFromId}`)">{{ d.copiedFromNo }}</el-link>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="版本说明" :span="3">{{ d.description || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建">{{ d.createdByName ?? '-' }} {{ d.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="更新">{{ d.updatedByName ?? '-' }} {{ d.updatedAt }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>
      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane :label="`工序(${d.steps.length})`" name="steps">
            <el-table :data="d.steps">
              <el-table-column prop="seq" label="工序号" width="80" align="right" />
              <el-table-column label="工序" width="120"><template #default="{ row }"><DictTag type="eng_operation" :value="row.operation" /></template></el-table-column>
              <el-table-column label="工作中心" min-width="180"><template #default="{ row }">{{ row.workCenterCode }} {{ row.workCenterName }}</template></el-table-column>
              <el-table-column label="类型" width="80"><template #default="{ row }">{{ labelOf(WC_TYPE_OPTIONS, row.wcType) }}</template></el-table-column>
              <el-table-column label="准备时间(分)" width="110" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.setupMinutes, 2) }}</span></template></el-table-column>
              <el-table-column label="标准工时(秒)" width="110" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.runSeconds, 2) }}</span></template></el-table-column>
              <el-table-column label="报工点" width="70" align="center"><template #default="{ row }">{{ row.isReportPoint ? '是' : '' }}</template></el-table-column>
              <el-table-column label="检验点" width="70" align="center"><template #default="{ row }">{{ row.isInspectionPoint ? '是' : '' }}</template></el-table-column>
              <el-table-column label="委外" width="60" align="center"><template #default="{ row }">{{ row.isOutsourced ? '是' : '' }}</template></el-table-column>
              <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="按工作中心汇总" name="wc">
            <el-table :data="d.byWorkCenter">
              <el-table-column prop="workCenterName" label="工作中心" min-width="180" />
              <el-table-column label="准备时间(分)" width="130" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.setupMinutes, 2) }}</span></template></el-table-column>
              <el-table-column label="标准工时(秒/件)" width="140" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.runSeconds, 2) }}</span></template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="ENG_ROUTING" :biz-id="id" :status-map="BOM_STATUS" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
</style>
