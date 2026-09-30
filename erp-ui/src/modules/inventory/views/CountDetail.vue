<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { DocAction } from '@/components'
import { download } from '@/api/http'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatAmount, formatQty } from '@/utils/format'
import { countApi, COUNT_STATUS, docRoute, DOC_STATUS, type CountDetail, type CountLineRow } from '../api/inventory'

defineOptions({ name: 'InvCountDetail' })

/** 盘点单详情（需求 08-06 4.3，T5 变体）：生成盘点表、在线录入实盘/复盘、提交、审核 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = String(route.params.id)
const d = ref<CountDetail>()
const lines = ref<CountLineRow[]>([])
const lineTotal = ref(0)
const lq = reactive({ filter: 'ALL', keyword: '', pageNo: 1, pageSize: 50 })
const loadingLines = ref(false)
const saving = ref(false)
/** 行 ID → 未保存的录入 */
const dirty = reactive(new Map<string, CountLineRow>())
/** 行 ID → 未保存的序列号清单（序列号物料） */
const serialEdits = reactive(new Map<string, { count?: string[]; recount?: string[] }>())
const importRef = ref<{ open: () => void }>()

async function load() {
  d.value = await countApi.get(id)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
  if (d.value.countStatus !== 'DRAFT') loadLines()
}
async function loadLines() {
  loadingLines.value = true
  try {
    const r = await countApi.lines(id, lq)
    lines.value = r.list
    lineTotal.value = r.total
    dirty.clear()
    serialEdits.clear()
  } finally {
    loadingLines.value = false
  }
}

const st = computed(() => d.value?.countStatus)
const counting = computed(() => st.value === 'COUNTING')

async function generate() {
  const pending = await countApi.pendingDocs(id)
  const tip = pending.docNos.length ? `以下单据尚未确认：${pending.docNos.slice(0, 10).join('、')}${pending.docNos.length > 10 ? ' 等' : ''}，建议先处理。` : ''
  await ElMessageBox.confirm(`${tip}生成后范围内的库存将被冻结，直到盘点结束，确定吗？`, '生成盘点表', { type: 'warning' })
  const n = await countApi.generate(id)
  ElMessage.success(`已生成盘点表，共 ${n} 行`)
  load()
}

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'inv:count:create', visible: () => st.value === 'DRAFT', confirm: `确定删除 ${d.value?.docNo} 吗？`,
    handler: async () => {
      await countApi.remove(id)
      tabs.remove([tabKeyOf(route)])
      router.push('/inventory/count')
    } },
  { key: 'void', label: '作废', permission: 'inv:count:void', visible: () => st.value === 'DRAFT' || st.value === 'COUNTING',
    confirm: '作废后解除冻结，库存不变。确定作废吗？', handler: () => run(countApi.void(id), '已作废') },
  { key: 'reject', label: '驳回', permission: 'inv:count:approve', visible: () => st.value === 'SUBMITTED' && d.value?.status === 'DRAFT',
    reasonRequired: true, handler: (reason) => run(countApi.reject(id, reason), '已驳回，回到盘点中') },
  { key: 'generate', label: '生成盘点表', type: 'primary', permission: 'inv:count:create', visible: () => st.value === 'DRAFT', handler: generate },
  { key: 'submit', label: '提交', type: 'primary', permission: 'inv:count:submit', visible: () => counting.value,
    handler: async () => {
      await saveInputs(true)
      const s = await countApi.submit(id)
      ElMessage.success(s === 'SUBMITTED' ? '已提交' : '已提交审批')
      load()
    } },
  { key: 'approve', label: '审核', type: 'primary', permission: 'inv:count:approve', visible: () => st.value === 'SUBMITTED' && d.value?.status === 'DRAFT',
    confirm: '审核后生成盘盈入库单、盘亏出库单并自动过账，同时解除冻结。确定审核吗？', handler: () => run(countApi.approve(id), '已审核') }
])

async function run(p: Promise<unknown>, msg: string) {
  await p
  ElMessage.success(msg)
  load()
}

// ---------- 录入 ----------
function touch(r: CountLineRow) {
  dirty.set(r.id, r)
}
async function saveInputs(silent = false) {
  if (!dirty.size && !serialEdits.size) return
  saving.value = true
  try {
    const rows = new Map<string, CountLineRow>(dirty)
    for (const lid of serialEdits.keys()) {
      const r = lines.value.find((x) => x.id === lid)
      if (r) rows.set(lid, r)
    }
    await countApi.input(id, [...rows.values()].map((r) => {
      const e = serialEdits.get(r.id)
      // 序列号物料：数量由序列号清单决定，不提交数量
      return r.serialTracked
        ? { id: r.id, reason: r.reason, remark: r.remark, countSerials: e?.count, recountSerials: e?.recount }
        : { id: r.id, countQty: r.countQty ?? undefined, recountQty: r.recountQty ?? undefined, reason: r.reason, remark: r.remark }
    }))
    if (!silent) ElMessage.success('已保存')
    await loadLines()
    d.value = await countApi.get(id)
  } finally {
    saving.value = false
  }
}

// ---------- 序列号物料：录入序列号清单 ----------
const serialDlg = reactive({ visible: false, row: undefined as CountLineRow | undefined, kind: 'count' as 'count' | 'recount', text: '' })
const splitSerials = (t: string) => [...new Set(t.split(/[\s,，;；]+/).map((x) => x.trim()).filter(Boolean))]
function openSerials(r: CountLineRow, kind: 'count' | 'recount') {
  serialDlg.row = r
  serialDlg.kind = kind
  const e = serialEdits.get(r.id)
  serialDlg.text = ((kind === 'count' ? e?.count ?? r.countSerials : e?.recount ?? r.recountSerials) ?? []).join('\n')
  serialDlg.visible = true
}
async function saveSerials() {
  const r = serialDlg.row
  if (!r) return
  const list = splitSerials(serialDlg.text)
  if (!list.length) return ElMessage.warning('请录入序列号')
  const e = serialEdits.get(r.id) ?? {}
  if (serialDlg.kind === 'count') e.count = list
  else e.recount = list
  serialEdits.set(r.id, e)
  serialDlg.visible = false
  await saveInputs()
}
const serialTip = (r: CountLineRow) => [r.gainSerials.length ? `盘盈：${r.gainSerials.join('、')}` : '', r.lossSerials.length ? `盘亏：${r.lossSerials.join('、')}` : ''].filter(Boolean).join('\n')

// ---------- 新增盘点外物料 ----------
const addVisible = ref(false)
const addForm = ref<{ warehouseId?: string; locationId?: string; materialId?: string; batchNo?: string; countQty?: string; reason?: string; remark?: string; serialText?: string }>({})
function openAdd() {
  addForm.value = { warehouseId: d.value?.warehouseIds?.[0] }
  addVisible.value = true
}
async function addLine() {
  const { serialText, ...rest } = addForm.value
  const serialNos = serialText ? splitSerials(serialText) : undefined
  await countApi.addLine(id, { ...rest, serialNos, countQty: serialNos?.length ? String(serialNos.length) : rest.countQty })
  ElMessage.success('已新增')
  addVisible.value = false
  loadLines()
}

const diffClass = (v?: string) => (v === undefined || v === null ? '' : Number(v) > 0 ? 'text-success' : Number(v) < 0 ? 'text-danger' : '')
const canCost = computed(() => me.hasPermission('inv:stock:cost'))
const asLine = (r: unknown) => r as CountLineRow
onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d ? `盘点单 ${d.docNo}` : '盘点单'" :status="d?.countStatus" :status-map="COUNT_STATUS" :actions="actions" @back="router.push('/inventory/count')">
        <template #actions-prefix>
          <ApprovalActions v-if="d && d.countStatus === 'SUBMITTED'" biz-type="INV_COUNT" :biz-id="id" @changed="load" />
          <PrintButton v-if="d && counting" biz-type="INV_COUNT" :ids="[id]" permission="inv:count:print" label="打印盘点表" />
        </template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <el-descriptions :column="4">
          <el-descriptions-item label="类型">{{ d.countType === 'FULL' ? '全盘' : '抽盘' }}</el-descriptions-item>
          <el-descriptions-item label="仓库">{{ d.warehouseNames }}</el-descriptions-item>
          <el-descriptions-item label="范围">{{ d.scopeSummary }}</el-descriptions-item>
          <el-descriptions-item label="盲盘">{{ d.blindCount ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="单据日期">{{ d.docDate }}</el-descriptions-item>
          <el-descriptions-item label="生成盘点表">{{ d.snapshotAt?.slice(0, 16) ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="进度">{{ d.inputCount }} / {{ d.lineCount }}（差异 {{ d.diffCount }} 行，需复盘 {{ d.recountCount }} 行）</el-descriptions-item>
          <el-descriptions-item v-if="canCost" label="差异金额"><span class="num">{{ d.diffAmount ? formatAmount(d.diffAmount) : '-' }}</span></el-descriptions-item>
          <el-descriptions-item v-if="d.adjustDocs.length" label="调整单据" :span="2">
            <template v-for="x in d.adjustDocs" :key="x.id">
              <el-link type="primary" underline="never" @click="router.push(docRoute(x.docType, x.id)!)">{{ x.docNo }}</el-link>
              <StatusTag :value="x.status" :map="DOC_STATUS" class="adj" />
            </template>
          </el-descriptions-item>
          <el-descriptions-item label="备注">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel v-if="d.countStatus === 'DRAFT'">
        <ErpEmpty description="尚未生成盘点表。生成后按当前库存快照账面数量，并冻结范围内的库存" />
      </ErpPanel>
      <ErpPanel v-else title="盘点明细">
        <template #extra>
          <div class="line-tools">
            <el-radio-group v-model="lq.filter" size="small" @change="() => { lq.pageNo = 1; loadLines() }">
              <el-radio-button value="ALL">全部</el-radio-button>
              <el-radio-button value="UNINPUT">未录入</el-radio-button>
              <el-radio-button value="DIFF">有差异</el-radio-button>
              <el-radio-button value="RECOUNT">需复盘</el-radio-button>
            </el-radio-group>
            <el-input v-model="lq.keyword" placeholder="物料编码/名称" clearable class="w160" @change="() => { lq.pageNo = 1; loadLines() }" />
            <template v-if="counting">
              <el-button v-perm="'inv:count:input'" icon="Plus" @click="openAdd">新增盘点外物料</el-button>
              <el-button v-perm="'inv:count:input'" icon="Download" @click="download(`/inventory/counts/${id}/export-sheet`, {}, `盘点表_${d.docNo}.xlsx`)">导出盘点表</el-button>
              <el-button v-perm="'inv:count:input'" icon="Upload" @click="importRef?.open()">导入实盘</el-button>
              <el-button v-perm="'inv:count:input'" type="primary" icon="Save" :loading="saving" :disabled="!dirty.size" @click="saveInputs()">保存录入</el-button>
            </template>
          </div>
        </template>
        <el-table v-loading="loadingLines" :data="lines" row-key="id" max-height="600">
          <el-table-column prop="lineNo" label="#" width="50" />
          <el-table-column prop="warehouseName" label="仓库" width="100" />
          <el-table-column prop="locationCode" label="库位" width="80" />
          <el-table-column prop="materialCode" label="物料编码" width="130" />
          <el-table-column prop="materialName" label="名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="baseUom" label="单位" width="60" />
          <el-table-column prop="batchNo" label="批次" width="120" />
          <el-table-column label="账面数量" width="100" align="right">
            <template #default="{ row }"><span class="num">{{ d.bookVisible ? formatQty(asLine(row).bookQty) : '***' }}</span></template>
          </el-table-column>
          <el-table-column label="实盘数量" width="130">
            <template #default="{ row }">
              <template v-if="asLine(row).serialTracked">
                <span class="num">{{ asLine(row).countSerials.length ? formatQty(asLine(row).countQty) : '' }}</span>
                <el-button v-if="counting" link type="primary" @click="openSerials(asLine(row), 'count')">序列号</el-button>
                <el-button v-else-if="asLine(row).countSerials.length" link type="primary" @click="openSerials(asLine(row), 'count')">查看</el-button>
              </template>
              <QtyInput v-else-if="counting" v-model="asLine(row).countQty" :uom="asLine(row).baseUom" @change="touch(asLine(row))" />
              <span v-else class="num">{{ formatQty(asLine(row).countQty) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="复盘数量" width="130">
            <template #default="{ row }">
              <template v-if="asLine(row).serialTracked && asLine(row).needRecount">
                <span class="num">{{ asLine(row).recountSerials.length ? formatQty(asLine(row).recountQty) : '' }}</span>
                <el-button v-if="counting" link type="primary" @click="openSerials(asLine(row), 'recount')">序列号</el-button>
              </template>
              <QtyInput v-else-if="counting && asLine(row).needRecount" v-model="asLine(row).recountQty" :uom="asLine(row).baseUom" @change="touch(asLine(row))" />
              <span v-else class="num">{{ asLine(row).recountQty ? formatQty(asLine(row).recountQty) : '' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="差异" width="90" align="right">
            <template #default="{ row }">
              <span v-if="d.bookVisible" :class="['num', diffClass(asLine(row).diffQty)]">{{ asLine(row).diffQty ? formatQty(asLine(row).diffQty) : '' }}</span>
              <el-tooltip v-if="d.bookVisible && (asLine(row).gainSerials.length || asLine(row).lossSerials.length)" placement="top">
                <template #content><span class="serial-tip">{{ serialTip(asLine(row)) }}</span></template>
                <ErpBadge type="warning">盘盈 {{ asLine(row).gainSerials.length }} / 盘亏 {{ asLine(row).lossSerials.length }}</ErpBadge>
              </el-tooltip>
              <span v-else class="text-muted">***</span>
            </template>
          </el-table-column>
          <el-table-column v-if="canCost && d.bookVisible" label="差异金额" width="100" align="right">
            <template #default="{ row }"><span class="num">{{ asLine(row).diffAmount ? formatAmount(asLine(row).diffAmount) : '' }}</span></template>
          </el-table-column>
          <el-table-column label="差异原因" width="140">
            <template #default="{ row }">
              <DictSelect v-if="counting" v-model="asLine(row).reason" type="inv_count_diff_reason" @change="touch(asLine(row))" />
              <DictTag v-else type="inv_count_diff_reason" :value="asLine(row).reason" />
            </template>
          </el-table-column>
          <el-table-column label="" width="90">
            <template #default="{ row }">
              <ErpBadge v-if="asLine(row).needRecount" type="warning">需复盘</ErpBadge>
              <ErpBadge v-if="asLine(row).added" type="primary">盘点外</ErpBadge>
            </template>
          </el-table-column>
          <el-table-column prop="counterName" label="录入人" width="90" />
          <el-table-column label="备注" min-width="120">
            <template #default="{ row }">
              <el-input v-if="counting" v-model="asLine(row).remark" maxlength="256" @change="touch(asLine(row))" />
              <span v-else>{{ asLine(row).remark }}</span>
            </template>
          </el-table-column>
        </el-table>
        <ErpPagination v-model:page-no="lq.pageNo" v-model:page-size="lq.pageSize" :total="lineTotal" @change="loadLines" />
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="addVisible" title="新增盘点外物料" width="560px" append-to-body>
      <el-form :model="addForm" label-width="90px">
        <el-form-item label="仓库" required>
          <el-select v-model="addForm.warehouseId" class="w-full">
            <el-option v-for="(w, i) in d?.warehouseIds ?? []" :key="w" :value="w" :label="d?.warehouseNames.split('、')[i] ?? w" />
          </el-select>
        </el-form-item>
        <el-form-item label="库位"><LocationSelect v-model="addForm.locationId" :warehouse-id="addForm.warehouseId" /></el-form-item>
        <el-form-item label="物料" required><MaterialSelect v-model="addForm.materialId" class="w-full" /></el-form-item>
        <el-form-item label="批次号"><el-input v-model="addForm.batchNo" maxlength="64" placeholder="批次管理物料必填，可填写新批次号" /></el-form-item>
        <el-form-item label="序列号清单"><el-input v-model="addForm.serialText" type="textarea" :rows="3" placeholder="序列号物料必填，每行一个（数量按清单计算）" /></el-form-item>
        <el-form-item v-if="!addForm.serialText" label="实盘数量" required><QtyInput v-model="addForm.countQty" /></el-form-item>
        <el-form-item label="差异原因"><DictSelect v-model="addForm.reason" type="inv_count_diff_reason" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="addForm.remark" maxlength="256" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" @click="addLine">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="serialDlg.visible" :title="`${serialDlg.kind === 'count' ? '实盘' : '复盘'}序列号 — ${serialDlg.row?.materialCode ?? ''}`" width="560px" append-to-body>
      <p v-if="serialDlg.row?.bookSerials" class="serial-book">账面 {{ serialDlg.row.bookSerials.length }} 个：{{ serialDlg.row.bookSerials.join('、') || '无' }}</p>
      <el-input v-model="serialDlg.text" type="textarea" :rows="10" :disabled="!counting" placeholder="每行一个序列号，也可用逗号 / 空格分隔" />
      <p class="serial-count">共 {{ splitSerials(serialDlg.text).length }} 个（数量按清单计算）</p>
      <template #footer>
        <el-button @click="serialDlg.visible = false">{{ counting ? '取消' : '关闭' }}</el-button>
        <el-button v-if="counting" type="primary" @click="saveSerials">保存</el-button>
      </template>
    </el-dialog>

    <ImportDialog ref="importRef" title="导入实盘" :base="`/inventory/counts/${id}`" template-name="盘点表" allow-partial @done="loadLines" />
  </ErpPage>
</template>

<style scoped>
.serial-tip { white-space: pre-line; }
.serial-book { margin: 0 0 var(--erp-space-2); color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-secondary); word-break: break-all; }
.serial-count { margin: var(--erp-space-2) 0 0; color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-secondary); }
.line-tools { display: flex; align-items: center; gap: var(--erp-space-2); flex-wrap: wrap; }
.adj { margin: 0 var(--erp-space-3) 0 var(--erp-space-1); }
</style>
