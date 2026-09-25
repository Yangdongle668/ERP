<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatQty, today } from '@/utils/format'
import { FEEDBACK_OPTIONS, FEEDBACK_STATUS, MAKE_METHOD_OPTIONS, sampleApi, SAMPLE_STATUS, SAMPLE_TYPE_OPTIONS, type SampleDetail } from '../api/sample'
import { labelOf } from '../api/material'

defineOptions({ name: 'EngSampleDetail' })

/** 样品单详情（需求 05-07 3.3，T5）：审批 → 生成生产订单 / 申请出库 → 登记寄出 → 登记反馈 → 关闭 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<SampleDetail>()
const activeTab = ref('log')

async function load() {
  d.value = await sampleApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}
const s = computed(() => d.value?.sampleStatus)
async function run(p: Promise<unknown>, msg: string) {
  await p
  ElMessage.success(msg)
  load()
}

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'eng:sample:delete', visible: () => s.value === 'DRAFT', confirm: `确定删除 ${d.value?.docNo} 吗？`,
    handler: async () => {
      await sampleApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/engineering/sample')
    } },
  { key: 'void', label: '作废', permission: 'eng:sample:void', visible: () => (s.value === 'DRAFT' || s.value === 'APPROVED') && !d.value?.prodOrderId && !d.value?.stockOutId,
    reasonRequired: true, handler: (reason) => run(sampleApi.void(id.value, reason!), '已作废') },
  { key: 'close', label: '关闭', permission: 'eng:sample:close', visible: () => !!s.value && !['DRAFT', 'CLOSED', 'VOIDED'].includes(s.value),
    reasonRequired: s.value !== 'FEEDBACK', confirm: s.value === 'FEEDBACK' ? '确定关闭样品单吗？' : undefined,
    handler: (reason) => run(sampleApi.close(id.value, reason), '已关闭') },
  { key: 'edit', label: '编辑', permission: 'eng:sample:update', visible: () => s.value === 'DRAFT', handler: () => router.push(`/engineering/sample/${id.value}/edit`) },
  { key: 'submit', label: '提交', type: 'primary', permission: 'eng:sample:submit', visible: () => s.value === 'DRAFT',
    handler: async () => {
      const st = await sampleApi.submit(id.value)
      ElMessage.success(st === 'APPROVED' ? '提交成功，已审批' : '已提交审批')
      load()
    } },
  { key: 'prod', label: '生成生产订单', type: 'primary', permission: 'eng:sample:update',
    visible: () => s.value === 'APPROVED' && d.value?.makeMethod === 'PRODUCE' && !!d.value?.productionAvailable,
    handler: () => run(sampleApi.createProdOrder(id.value), '已生成样品生产订单') },
  { key: 'stockOut', label: '申请出库', type: 'primary', permission: 'eng:sample:ship',
    visible: () => !d.value?.stockOutId && ((s.value === 'APPROVED' && d.value?.makeMethod === 'FROM_STOCK') || s.value === 'READY'),
    handler: () => run(sampleApi.requestStockOut(id.value), '已生成出库单，等待仓库确认出库') },
  { key: 'ship', label: '登记寄出', type: 'primary', permission: 'eng:sample:ship', visible: () => s.value === 'READY' && !!d.value?.stockOutDone,
    handler: () => openShip() },
  { key: 'feedback', label: '登记反馈', type: 'primary', permission: 'eng:sample:feedback', visible: () => s.value === 'SHIPPED', handler: () => openFeedback() }
])

// ---------- 寄出 ----------
const shipVisible = ref(false)
const shipRef = ref<FormInstance>()
const ship = ref({ shipDate: today(), courier: '', trackingNo: '', shipAddress: '' })
function openShip() {
  ship.value = { shipDate: today(), courier: '', trackingNo: '', shipAddress: d.value?.shipAddress ?? '' }
  shipVisible.value = true
}
async function doShip() {
  if (!ship.value.courier.trim() || !ship.value.trackingNo.trim()) return ElMessage.warning('请填写快递公司和快递单号')
  await sampleApi.ship(id.value, { ...ship.value, shipAddress: ship.value.shipAddress || undefined })
  shipVisible.value = false
  ElMessage.success('已登记寄出')
  load()
}

// ---------- 反馈 ----------
const fbVisible = ref(false)
const fb = ref<{ result?: string; feedbackDate: string; content?: string; fileIds: string[] }>({ feedbackDate: today(), fileIds: [] })
function openFeedback() {
  fb.value = { feedbackDate: today(), fileIds: [] }
  fbVisible.value = true
}
async function doFeedback() {
  if (!fb.value.result) return ElMessage.warning('请选择反馈结果')
  await sampleApi.feedback(id.value, { result: fb.value.result, feedbackDate: fb.value.feedbackDate, content: fb.value.content || undefined, fileIds: fb.value.fileIds })
  fbVisible.value = false
  ElMessage.success('已登记反馈')
  load()
}

const steps = [
  { status: 'DRAFT', label: '草稿' },
  { status: 'PENDING', label: '待审批' },
  { status: 'APPROVED', label: '已审批' },
  { status: 'MAKING', label: '制作中' },
  { status: 'READY', label: '待寄出' },
  { status: 'SHIPPED', label: '已寄出' },
  { status: 'FEEDBACK', label: '已反馈' },
  { status: 'CLOSED', label: '已关闭' }
]
onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d ? `${d.docNo} ${d.materialName}` : '样品单'" :status="d?.sampleStatus" :status-map="SAMPLE_STATUS" :actions="actions"
                     @back="router.push('/engineering/sample')">
        <template #actions-prefix>
          <ApprovalActions v-if="d" biz-type="ENG_SAMPLE" :biz-id="id" @changed="load" />
          <PrintButton v-if="d && d.sampleStatus !== 'VOIDED'" biz-type="ENG_SAMPLE" :ids="[id]" permission="eng:sample:print" />
        </template>
      </DocPageHeader>
    </template>
    <template v-if="d">
      <ErpPanel>
        <el-alert v-if="d.sampleStatus === 'VOIDED'" type="info" :closable="false" show-icon :title="`已作废：${d.closeReason ?? ''}`" />
        <DocSteps v-else :steps="steps" :current="d.sampleStatus" />
        <el-alert v-if="d.sampleStatus === 'APPROVED' && d.makeMethod === 'PRODUCE' && !d.productionAvailable" type="info" :closable="false" show-icon class="gap"
                  title="生产模块尚未启用，暂时不能生成样品生产订单；可改为“从库存领取”重新申请" />
        <el-alert v-if="d.stockOutId && !d.stockOutDone" type="warning" :closable="false" show-icon class="gap" title="已生成出库单，等待仓库确认出库后才能登记寄出" />
        <el-descriptions :column="3" class="head">
          <el-descriptions-item label="样品类型">{{ labelOf(SAMPLE_TYPE_OPTIONS, d.sampleType) }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ d.customerName ?? d.customerId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="研发项目">
            <el-link v-if="d.projectId" type="primary" underline="never" @click="router.push(`/engineering/project/${d.projectId}`)">{{ d.projectNo }}</el-link>
            <span v-else>-</span> {{ d.projectName ?? '' }}
          </el-descriptions-item>
          <el-descriptions-item label="物料">
            <el-link type="primary" underline="never" @click="router.push(`/engineering/material/${d.materialId}`)">{{ d.materialCode }}</el-link> {{ d.materialName }}
          </el-descriptions-item>
          <el-descriptions-item label="规格">{{ d.materialSpec || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户料号">{{ d.customerPartNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="数量">{{ formatQty(d.qty) }} {{ d.uom }}</el-descriptions-item>
          <el-descriptions-item label="要求日期">{{ d.requiredDate }}</el-descriptions-item>
          <el-descriptions-item label="制作方式">{{ labelOf(MAKE_METHOD_OPTIONS, d.makeMethod) }}</el-descriptions-item>
          <el-descriptions-item label="生产订单">{{ d.prodOrderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="出库单">{{ d.stockOutNo || (d.stockOutId ? '待确认' : '-') }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ d.createdByName ?? '-' }} {{ d.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="用途" :span="3">{{ d.purpose }}</el-descriptions-item>
          <el-descriptions-item label="特殊要求" :span="3">{{ d.requirements || '-' }}</el-descriptions-item>
          <el-descriptions-item label="寄送地址" :span="3">{{ d.shipAddress || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>
      <ErpPanel v-if="d.shipDate || d.feedbackResult" title="寄出与反馈">
        <el-descriptions :column="3">
          <el-descriptions-item label="寄出日期">{{ d.shipDate ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="快递">{{ d.courier ?? '' }} {{ d.trackingNo ?? '' }}</el-descriptions-item>
          <el-descriptions-item label="反馈结果"><StatusTag v-if="d.feedbackResult" :value="d.feedbackResult" :map="FEEDBACK_STATUS" /><span v-else>-</span></el-descriptions-item>
          <el-descriptions-item label="反馈日期">{{ d.feedbackDate ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="反馈内容" :span="2">{{ d.feedbackContent || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="d.closeReason" label="关闭原因" :span="3">{{ d.closeReason }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>
      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane label="操作日志" name="log"><OperationLogTable biz-type="ENG_SAMPLE" :biz-id="id" :status-map="SAMPLE_STATUS" /></el-tab-pane>
          <el-tab-pane label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="ENG_SAMPLE" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="ENG_SAMPLE" :biz-id="id" :editable="d.sampleStatus !== 'CLOSED' && d.sampleStatus !== 'VOIDED'" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="shipVisible" title="登记寄出" width="520px" append-to-body>
      <el-form ref="shipRef" :model="ship" label-width="90px">
        <el-form-item label="寄出日期" required><el-date-picker v-model="ship.shipDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="快递公司" required><el-input v-model="ship.courier" maxlength="32" /></el-form-item>
        <el-form-item label="快递单号" required><el-input v-model="ship.trackingNo" maxlength="64" /></el-form-item>
        <el-form-item label="寄送地址"><el-input v-model="ship.shipAddress" maxlength="512" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipVisible = false">取消</el-button>
        <el-button type="primary" @click="doShip">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="fbVisible" title="登记客户反馈" width="560px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="反馈结果" required>
          <el-radio-group v-model="fb.result"><el-radio v-for="o in FEEDBACK_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item label="反馈日期" required><el-date-picker v-model="fb.feedbackDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="反馈内容"><el-input v-model="fb.content" type="textarea" :rows="3" maxlength="1000" /></el-form-item>
        <el-form-item label="附件"><AttachmentUpload v-model="fb.fileIds" biz-type="ENG_SAMPLE" multiple /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fbVisible = false">取消</el-button>
        <el-button type="primary" @click="doFeedback">确定</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.gap { margin-top: var(--erp-space-3); }
.head { margin-top: var(--erp-space-4); }
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
</style>
