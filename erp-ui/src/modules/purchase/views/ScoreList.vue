<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { useUserStore } from '@/stores/user'
import { formatQty } from '@/utils/format'
import { INSPECT_STATUS, joinList } from '../api/common'
import { GRADE_OPTIONS, SCORE_STATUS, SCORE_STATUS_OPTIONS, scoreApi, type ScoreDetail, type ScoreQuery, type ScoreRow } from '../api/score'

defineOptions({ name: 'PurScoreList' })

/** 供应商评估（需求 07-10 4，T1）：计算质量/交期得分 → 手工评价格/服务 → 发布更新供应商等级 */
const me = useUserStore()
const reasonRef = ref<{ open: (o: { title?: string; tip?: string }) => Promise<string | undefined> }>()

const lastMonth = () => {
  const d = new Date()
  d.setMonth(d.getMonth() - 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

type Query = Omit<ScoreQuery, 'pageNo' | 'pageSize' | 'statuses'> & { statuses?: string[] }
const { query, list, total, loading, selection, load, search, reset, onSelectionChange } = useListPage<Query, ScoreRow>({
  api: (q) => scoreApi.page({ ...q, statuses: joinList(q.statuses) } as ScoreQuery),
  defaultQuery: () => ({ period: lastMonth() })
})

const fields: SearchField[] = [
  { prop: 'period', label: '评估期', placeholder: '2026-09 或 2026-Q3' },
  { prop: 'supplierId', label: '供应商', type: 'slot' },
  { prop: 'grade', label: '等级', type: 'select', options: GRADE_OPTIONS },
  { prop: 'statuses', label: '状态', type: 'select', options: SCORE_STATUS_OPTIONS, multiple: true }
]

const GRADE_CLASS: Record<string, string> = { A: 'text-success', B: 'grade-b', C: 'text-warning', D: 'text-danger' }
const canEdit = computed(() => me.hasPermission('pur:score:update'))

const columns: TableColumn<ScoreRow>[] = [
  { prop: 'supplierName', label: '供应商', width: 150, formatter: (r) => `${r.supplierCode} ${r.supplierName}` },
  { prop: 'period', label: '评估期', width: 90 },
  { prop: 'lots', label: '批次(合格/总数)', width: 120, align: 'right', formatter: (r) => `${r.lotPassCount}/${r.lotCount}` },
  { prop: 'qualityScore', label: '质量', width: 80, type: 'qty' },
  { prop: 'lines', label: '交期行(准时/应到)', width: 130, align: 'right', formatter: (r) => `${r.ontimeLineCount}/${r.dueLineCount}` },
  { prop: 'deliveryScore', label: '交期', width: 80, type: 'qty' },
  { prop: 'priceScore', label: '价格', width: 110, slot: true },
  { prop: 'serviceScore', label: '服务', width: 110, slot: true },
  { prop: 'totalScore', label: '总分', width: 80, type: 'qty' },
  { prop: 'grade', label: '等级', width: 60, slot: true },
  { prop: 'comment', label: '评语', minWidth: 180, slot: true },
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: SCORE_STATUS }
]

const asRow = (r: unknown) => r as ScoreRow
const editableRow = (r: ScoreRow) => canEdit.value && r.status !== 'PUBLISHED'

/** 失焦保存手工评分 */
async function saveRow(r: ScoreRow) {
  const saved = await scoreApi.update(r.id, { priceScore: r.priceScore || undefined, serviceScore: r.serviceScore || undefined, comment: r.comment, version: r.version })
    .catch(() => undefined)
  if (saved) Object.assign(r, saved)
  else load()
}

// ---------- 计算 ----------
const calcVisible = ref(false)
const calcPeriod = ref(lastMonth())
const calculating = ref(false)
async function calculate() {
  if (!/^\d{4}-(\d{2}|Q[1-4])$/.test(calcPeriod.value)) return ElMessage.warning('评估期格式为 2026-09 或 2026-Q3')
  calculating.value = true
  try {
    const r = await scoreApi.calculate(calcPeriod.value)
    calcVisible.value = false
    ElMessage.success(`已计算 ${r.count} 家供应商`)
    query.period = calcPeriod.value
    search()
  } finally {
    calculating.value = false
  }
}

// ---------- 发布 / 撤销 ----------
async function publish() {
  const rows = selection.value.filter((r) => r.status === 'SCORED')
  if (!rows.length) return ElMessage.warning('请勾选已评分的记录')
  const n = await scoreApi.publish(rows.map((r) => r.id))
  ElMessage.success(`已发布 ${n} 条，供应商等级已更新`)
  load()
}
async function unpublish(r: ScoreRow) {
  const reason = await reasonRef.value?.open({ title: '撤销发布', tip: '撤销后可重新修改评分，供应商等级不会自动恢复。' })
  if (!reason) return
  await scoreApi.unpublish(r.id, reason)
  ElMessage.success('已撤销发布')
  load()
}

// ---------- 明细 / 趋势 ----------
const drawer = ref(false)
const current = ref<ScoreRow>()
const detail = ref<ScoreDetail>()
const trend = ref<{ period: string; totalScore?: string; grade?: string }[]>([])
async function openDetail(r: ScoreRow) {
  current.value = r
  drawer.value = true
  detail.value = undefined
  const [d, t] = await Promise.all([scoreApi.details(r.id), scoreApi.trend(r.supplierId)])
  detail.value = d
  trend.value = t
}
</script>

<template>
  <ErpPage description="按月或季度评估供应商：质量、交期自动计算，价格、服务手工评分；发布后更新供应商等级">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-supplierId><SupplierSelect v-model="query.supplierId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" selection storage-key="pur.score" :actions-width="120"
                @selection-change="onSelectionChange" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'pur:score:calculate'" type="primary" icon="Play" @click="calcVisible = true">计算</el-button>
          <el-button v-perm="'pur:score:calculate'" :disabled="!selection.length" @click="publish">发布</el-button>
        </template>
        <template #col-priceScore="{ row }">
          <NumberInput v-if="editableRow(asRow(row))" v-model="asRow(row).priceScore" :precision="2" trim-zeros :max="100" @change="saveRow(asRow(row))" />
          <span v-else class="num">{{ asRow(row).priceScore ?? '-' }}</span>
        </template>
        <template #col-serviceScore="{ row }">
          <NumberInput v-if="editableRow(asRow(row))" v-model="asRow(row).serviceScore" :precision="2" trim-zeros :max="100" @change="saveRow(asRow(row))" />
          <span v-else class="num">{{ asRow(row).serviceScore ?? '-' }}</span>
        </template>
        <template #col-grade="{ row }"><strong :class="GRADE_CLASS[asRow(row).grade ?? '']">{{ asRow(row).grade ?? '-' }}</strong></template>
        <template #col-comment="{ row }">
          <el-input v-if="editableRow(asRow(row))" v-model="asRow(row).comment" maxlength="512" @change="saveRow(asRow(row))" />
          <span v-else>{{ asRow(row).comment || '-' }}</span>
        </template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '明细', handler: () => openDetail(asRow(row)) },
            { label: '撤销发布', permission: 'pur:score:update', visible: asRow(row).status === 'PUBLISHED', handler: () => unpublish(asRow(row)) }
          ]" />
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="calcVisible" title="计算评分" width="480px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="评估期"><el-input v-model="calcPeriod" placeholder="2026-09 或 2026-Q3" /></el-form-item>
      </el-form>
      <p class="form-tip">为期内有到货批次或应到订单行的合格/暂停供应商计算质量和交期得分；已发布的不重算</p>
      <template #footer>
        <el-button @click="calcVisible = false">取消</el-button>
        <el-button type="primary" :loading="calculating" @click="calculate">计算</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="drawer" :title="current ? `${current.supplierName} ${current.period} 评估明细` : '评估明细'" size="720px">
      <template v-if="detail">
        <div class="group-title">不合格批次</div>
        <el-table :data="detail.lots">
          <el-table-column prop="receiptNo" label="到货单" width="150" />
          <el-table-column label="物料" min-width="180"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
          <el-table-column label="数量" width="90" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
          <el-table-column label="结果" width="90"><template #default="{ row }"><StatusTag :value="row.inspectStatus" :map="INSPECT_STATUS" /></template></el-table-column>
          <el-table-column prop="judgedDate" label="判定日期" width="110" />
          <template #empty><ErpEmpty compact description="没有不合格批次" /></template>
        </el-table>
        <div class="group-title">延误订单行</div>
        <el-table :data="detail.delayedLines">
          <el-table-column label="订单" width="160"><template #default="{ row }">{{ row.orderNo }}-{{ row.lineNo }}</template></el-table-column>
          <el-table-column label="物料" min-width="160"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
          <el-table-column prop="dueDate" label="应到日期" width="110" />
          <el-table-column prop="firstReceivedDate" label="首次到货" width="110" />
          <el-table-column label="到货/订购" width="120" align="right"><template #default="{ row }">{{ formatQty(row.receivedQty) }}/{{ formatQty(row.qty) }}</template></el-table-column>
          <template #empty><ErpEmpty compact description="没有延误" /></template>
        </el-table>
        <div class="group-title">近 12 期总分</div>
        <el-table :data="trend">
          <el-table-column prop="period" label="评估期" width="100" />
          <el-table-column label="总分" min-width="200">
            <template #default="{ row }"><el-progress :percentage="Number(row.totalScore ?? 0)" :stroke-width="8" :format="() => row.totalScore ?? '-'" /></template>
          </el-table-column>
          <el-table-column label="等级" width="70"><template #default="{ row }"><strong :class="GRADE_CLASS[row.grade ?? '']">{{ row.grade ?? '-' }}</strong></template></el-table-column>
          <template #empty><ErpEmpty compact /></template>
        </el-table>
      </template>
      <el-skeleton v-else :rows="6" animated />
    </el-drawer>

    <ReasonDialog ref="reasonRef" />
  </ErpPage>
</template>

<style scoped>
.grade-b { color: var(--erp-color-primary); }
.group-title { margin: var(--erp-space-4) 0 var(--erp-space-2); }
</style>
