<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { formatAmount } from '@/utils/format'
import FollowupTimeline from '../components/FollowupTimeline.vue'
import OpportunityDialog from '../components/OpportunityDialog.vue'
import { OPP_STATUS, OPP_STATUS_OPTIONS, STAGE_LABELS, STAGE_OPTIONS, oppApi, type Funnel, type OppQuery, type OppRow } from '../api/crm'

defineOptions({ name: 'CrmOpportunityList' })

/** 商机（需求 03-05）：漏斗统计卡片、列表（T1）、表单（T2）、详情抽屉 */
const route = useRoute()
const router = useRouter()
const dialogRef = ref<InstanceType<typeof OpportunityDialog>>()
type Query = Omit<OppQuery, 'pageNo' | 'pageSize' | 'statuses' | 'expectedFrom' | 'expectedTo'> & { statuses?: string[]; expected?: string[] }
function toParams(q: Query) {
  const { statuses, expected, ...rest } = q
  return { ...rest, statuses: statuses?.length ? statuses.join(',') : undefined, expectedFrom: expected?.[0], expectedTo: expected?.[1] }
}
const funnel = ref<Funnel>()
const { query, list, total, loading, load: loadList, search, reset } = useListPage<Query, OppRow>({
  api: (q) => oppApi.page(toParams(q) as OppQuery),
  defaultQuery: () => ({ statuses: ['OPEN'] }),
  refreshOnActivated: true
})
async function load() {
  await loadList()
  funnel.value = await oppApi.funnel(toParams({ ...query })).catch(() => undefined)
}

const fields: SearchField[] = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' },
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'ownerId', label: '负责人', type: 'user' },
  { prop: 'stage', label: '阶段', type: 'select', options: STAGE_OPTIONS },
  { prop: 'statuses', label: '状态', type: 'select', options: OPP_STATUS_OPTIONS, multiple: true },
  { prop: 'expected', label: '预计成交', type: 'daterange' }
]
const columns: TableColumn<OppRow>[] = [
  { prop: 'code', label: '编码', width: 140 },
  { prop: 'name', label: '名称', minWidth: 200, type: 'link', onClick: (r) => openDetail(r) },
  { prop: 'customerShortName', label: '客户', width: 130 },
  { prop: 'stage', label: '阶段', width: 100, slot: true },
  { prop: 'amount', label: '金额', width: 150, align: 'right', formatter: (r) => `${r.currency} ${formatAmount(r.amount, 2)}` },
  { prop: 'winRate', label: '赢率', width: 70, align: 'right', formatter: (r) => `${Math.round(Number(r.winRate) * 100)}%` },
  { prop: 'expectedDate', label: '预计成交', width: 110, slot: true },
  { prop: 'ownerName', label: '负责人', width: 90 },
  { prop: 'lastFollowupAt', label: '最近跟进', width: 150, type: 'datetime' },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: OPP_STATUS }
]

async function run(p: Promise<unknown>, msg: string) {
  await p
  ElMessage.success(msg)
  load()
  if (detail.value) detail.value = await oppApi.get(detail.value.id)
}
async function nextStage(r: OppRow) {
  const i = STAGE_OPTIONS.findIndex((s) => s.value === r.stage)
  const next = STAGE_OPTIONS[i + 1]
  if (next) await run(oppApi.stage(r.id, next.value), `已推进到${next.label}`)
}
async function win(r: OppRow) {
  const { value } = await ElMessageBox.prompt('关联的销售订单号（可选）', `赢单 - ${r.name}`, { inputPlaceholder: '如 SO-202609-0001' })
  await run(oppApi.win(r.id, value || undefined), '已赢单')
}

// ---------- 输单 ----------
const loseVisible = ref(false)
const loseOf = ref<OppRow>()
const lose = ref<{ reason?: string; remark?: string }>({})
function openLose(r: OppRow) {
  loseOf.value = r
  lose.value = {}
  loseVisible.value = true
}
async function doLose() {
  if (!lose.value.reason) return ElMessage.warning('请选择输单原因')
  await run(oppApi.lose(loseOf.value!.id, lose.value.reason, lose.value.remark), '已记录输单')
  loseVisible.value = false
}
async function shelve(r: OppRow) {
  const { value } = await ElMessageBox.prompt('搁置说明（可选）', `搁置 - ${r.name}`)
  await run(oppApi.shelve(r.id, value || undefined), '已搁置')
}

// ---------- 详情抽屉 ----------
const detail = ref<OppRow>()
const drawer = ref(false)
function openDetail(r: OppRow) {
  detail.value = r
  drawer.value = true
}
const stageSteps = STAGE_OPTIONS.map((s) => ({ status: s.value, label: s.label }))
const detailSteps = computed(() => (detail.value?.status === 'WON' ? [...stageSteps, { status: 'WON', label: '赢单' }]
  : detail.value?.status === 'LOST' ? [...stageSteps, { status: 'LOST', label: '输单' }] : stageSteps))

onMounted(async () => {
  if (typeof route.query.open === 'string') openDetail(await oppApi.get(route.query.open))
  funnel.value = await oppApi.funnel(toParams({ ...query })).catch(() => undefined)
})
const asRow = (r: unknown) => r as OppRow
const isOpen = (r: OppRow) => r.status === 'OPEN'
</script>

<template>
  <ErpPage description="跟踪潜在订单从初步接触到赢单/输单；漏斗按进行中商机统计，金额折算本位币，加权金额 = 金额 × 赢率">
    <div v-if="funnel" class="funnel">
      <ErpPanel v-for="s in funnel.stages" :key="s.stage" class="card">
        <div class="card-title">{{ STAGE_LABELS[s.stage] }}</div>
        <div class="card-value num">{{ s.count }} <span class="text-muted">个</span></div>
        <div class="card-sub">金额 <span class="num">{{ formatAmount(s.amountBase) }}</span></div>
        <div class="card-sub">加权 <span class="num">{{ formatAmount(s.weightedBase) }}</span></div>
      </ErpPanel>
      <ErpPanel class="card">
        <div class="card-title">合计（{{ funnel.baseCurrency }}）</div>
        <div class="card-value num">{{ funnel.totalCount }} <span class="text-muted">个</span></div>
        <div class="card-sub">金额 <span class="num">{{ formatAmount(funnel.totalAmountBase) }}</span></div>
        <div class="card-sub">加权 <span class="num">{{ formatAmount(funnel.totalWeightedBase) }}</span></div>
      </ErpPanel>
    </div>
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="() => { search(); load() }" @reset="() => { reset(); load() }">
          <template #field-customerId><CustomerSelect v-model="query.customerId" placeholder="全部" class="w200" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="crm.opportunity" :actions-width="220" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'crm:opportunity:create'" type="primary" icon="Plus" @click="dialogRef?.open()">新建商机</el-button>
        </template>
        <template #col-stage="{ row }"><ErpBadge :type="asRow(row).status === 'OPEN' ? 'primary' : 'info'" plain>{{ STAGE_LABELS[asRow(row).stage] }}</ErpBadge></template>
        <template #col-expectedDate="{ row }"><span :class="{ 'text-danger': asRow(row).overdue }">{{ asRow(row).expectedDate }}</span></template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '编辑', permission: 'crm:opportunity:update', visible: isOpen(asRow(row)) || asRow(row).status === 'SHELVED', handler: () => dialogRef?.open(asRow(row)) },
            { label: '推进阶段', permission: 'crm:opportunity:update', visible: isOpen(asRow(row)) && asRow(row).stage !== 'NEGOTIATION', handler: () => nextStage(asRow(row)) },
            { label: '赢单', permission: 'crm:opportunity:close', visible: isOpen(asRow(row)), handler: () => win(asRow(row)) },
            { label: '输单', permission: 'crm:opportunity:close', visible: isOpen(asRow(row)), handler: () => openLose(asRow(row)) },
            { label: '搁置', permission: 'crm:opportunity:close', visible: isOpen(asRow(row)), handler: () => shelve(asRow(row)) },
            { label: '恢复', permission: 'crm:opportunity:close', visible: asRow(row).status === 'SHELVED', handler: () => run(oppApi.resume(asRow(row).id), '已恢复') },
            { label: '删除', permission: 'crm:opportunity:delete', danger: true, visible: isOpen(asRow(row)) || asRow(row).status === 'SHELVED', confirm: `确定删除商机「${asRow(row).name}」吗？`, handler: () => run(oppApi.remove(asRow(row).id), '删除成功') }
          ]" />
        </template>
        <template #empty>
          <el-button v-perm="'crm:opportunity:create'" icon="Plus" @click="dialogRef?.open()">新建商机</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <OpportunityDialog ref="dialogRef" @saved="load" />

    <el-dialog v-model="loseVisible" :title="`输单 - ${loseOf?.name ?? ''}`" width="480px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="输单原因" required><DictSelect v-model="lose.reason" type="crm_lost_reason" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="lose.remark" type="textarea" :rows="2" maxlength="512" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="loseVisible = false">取消</el-button>
        <el-button type="primary" @click="doLose">确定</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="drawer" :title="detail ? `${detail.code} ${detail.name}` : '商机'" size="720px">
      <template v-if="detail">
        <DocSteps :steps="detailSteps" :current="detail.stage" />
        <el-descriptions :column="2" class="head">
          <el-descriptions-item label="客户">
            <el-link type="primary" underline="never" @click="router.push(`/crm/customer/${detail.customerId}`)">{{ detail.customerShortName }}</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="状态"><StatusTag :value="detail.status" :map="OPP_STATUS" /></el-descriptions-item>
          <el-descriptions-item label="金额">{{ detail.currency }} {{ formatAmount(detail.amount) }}</el-descriptions-item>
          <el-descriptions-item label="赢率">{{ Math.round(Number(detail.winRate) * 100) }}%</el-descriptions-item>
          <el-descriptions-item label="预计成交">{{ detail.expectedDate }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{ detail.ownerName }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ detail.contactName ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="竞争对手">{{ detail.competitor || '-' }}</el-descriptions-item>
          <el-descriptions-item label="意向产品" :span="2">{{ detail.products || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.wonOrderNo" label="订单号" :span="2">{{ detail.wonOrderNo }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.status === 'LOST'" label="输单原因" :span="2">
            <DictTag type="crm_lost_reason" :value="detail.lostReason" /> {{ detail.lostRemark ?? '' }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="group-title">跟进记录</div>
        <FollowupTimeline :customer-id="detail.customerId" :opportunity-id="detail.id" />
      </template>
    </el-drawer>
  </ErpPage>
</template>

<style scoped>
.funnel { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: var(--erp-space-4); margin-bottom: var(--erp-space-4); }
.card { margin: 0; }
.card-title { color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-secondary); }
.card-value { font-size: var(--erp-font-size-metric); font-weight: var(--erp-font-weight-medium); margin: var(--erp-space-1) 0; }
.card-sub { color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-secondary); }
.head { margin-top: var(--erp-space-4); }
</style>
