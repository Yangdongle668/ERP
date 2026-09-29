<script setup lang="ts">
import { computed, onActivated, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { formatQty } from '@/utils/format'
import {
  EXCEPTION_TYPE, mrpApi, num, PEG_TYPE, SUGGESTION_STATUS,
  type ExceptionRow, type PegRow, type SuggestionRow
} from '../api/pmc'

defineOptions({ name: 'PmcMrpSuggestions' })

/**
 * MRP 建议处理（需求 06-04，T1）：采购 / 生产 / 委外 / 例外四个页签；默认最近一次成功运算的待处理建议。
 * 调整数量、日期、供应商后转单；旧运算的建议只读。
 */
const route = useRoute()
const router = useRouter()
const tab = ref<string>(typeof route.query.tab === 'string' ? route.query.tab : 'PURCHASE')
const runId = ref<string | undefined>(typeof route.query.runId === 'string' ? route.query.runId : undefined)
const q = ref<{ materialId?: string; plannerId?: string; buyerId?: string; supplierId?: string; lateOnly?: boolean; statuses: string }>({ statuses: 'PENDING' })
const rows = ref<SuggestionRow[]>([])
const total = ref(0)
const page = ref({ pageNo: 1, pageSize: 50 })
const loading = ref(false)
const selected = ref<SuggestionRow[]>([])
const isException = computed(() => tab.value === 'exception')

async function load() {
  loading.value = true
  try {
    if (isException.value) {
      const r = await mrpApi.exceptions({ ...page.value, runId: runId.value, materialId: q.value.materialId, handled: exHandled.value } as never)
      exRows.value = r.list
      total.value = r.total
    } else {
      const r = await mrpApi.suggestions({ ...page.value, ...q.value, runId: runId.value, type: tab.value } as never)
      rows.value = r.list
      total.value = r.total
    }
  } finally {
    loading.value = false
  }
}
onMounted(load)
onActivated(load)
watch(tab, () => {
  page.value.pageNo = 1
  selected.value = []
  load()
})

// ---------- 调整 ----------
async function update(row: unknown, patch: { qty?: string; requiredDate?: string; supplierId?: string }) {
  try {
    const warnings = await mrpApi.update((row as SuggestionRow).id, patch)
    if (warnings.length) ElMessage.warning(warnings.join('；'))
  } finally {
    load()
  }
}

// ---------- 转单 / 忽略 ----------
const release = ref(false)
const converting = ref(false)
async function convert() {
  if (!selected.value.length) return ElMessage.warning('请勾选建议')
  converting.value = true
  try {
    const r = await mrpApi.convert(selected.value.map((s) => s.id), tab.value === 'MAKE' && release.value)
    if (r.errors.length) ElNotification({ type: 'warning', title: `成功 ${r.success} 条，失败 ${r.errors.length} 条`, message: r.errors.join('；'), duration: 10000 })
    else ElMessage.success(`已转单 ${r.success} 条`)
    load()
  } finally {
    converting.value = false
  }
}
async function ignore() {
  if (!selected.value.length) return ElMessage.warning('请勾选建议')
  const { value } = await ElMessageBox.prompt('请填写忽略原因', '忽略建议', { inputValidator: (v) => (v && v.trim() ? true : '请填写忽略原因') })
  await mrpApi.ignore(selected.value.map((s) => s.id), value)
  ElMessage.success('已忽略')
  load()
}
const docLink = (x: unknown) => docLinkOf(x as SuggestionRow)
const docLinkOf = (r: SuggestionRow) => (r.convertedDocType === 'MFG_PROD_ORDER' ? `/production/prod-order/${r.convertedDocId}` : undefined)

// ---------- 需求追溯 ----------
const pegVisible = ref(false)
const pegTitle = ref('')
const pegStack = ref<{ title: string; rows: PegRow[] }[]>([])
async function openPeg(row: unknown) {
  const r = row as SuggestionRow
  pegStack.value = [{ title: `${r.materialCode} ${r.materialName} × ${formatQty(r.qty)}`, rows: await mrpApi.pegging(r.id) }]
  pegTitle.value = '需求追溯'
  pegVisible.value = true
}
async function drill(row: unknown) {
  const p = row as PegRow
  if (!p.parentResultId) return
  pegStack.value.push({ title: `${p.parentCode} ${p.parentName ?? ''}（上层计划订单）`, rows: await mrpApi.pegging(p.parentResultId) })
}
function openSource(row: unknown) {
  const p = row as PegRow
  if (p.demandType === 'SALES_ORDER' && p.sourceId) router.push(`/sales/order/${p.sourceId}`)
}

// ---------- 例外 ----------
const exRows = ref<ExceptionRow[]>([])
const exSelected = ref<ExceptionRow[]>([])
const exHandled = ref<boolean | undefined>(false)
async function push() {
  if (!exSelected.value.length) return ElMessage.warning('请勾选例外')
  const r = await mrpApi.push(exSelected.value.map((e) => e.id))
  if (r.errors.length) ElNotification({ type: 'warning', title: `已推送 ${r.success} 条`, message: r.errors.join('；'), duration: 8000 })
  else ElMessage.success(`已推送 ${r.success} 条`)
  load()
}
async function toggleHandled(row: unknown, v: unknown) {
  const e = row as ExceptionRow
  await mrpApi.handled(e.id, Boolean(v))
  e.handled = Boolean(v)
}
const docRoute = (x: unknown) => docRouteOf(x as ExceptionRow)
const docRouteOf = (e: ExceptionRow) => (e.docType === 'MFG_PROD_ORDER' ? `/production/prod-order/${e.docId}` : undefined)
</script>

<template>
  <ErpPage description="确认 MRP 建议后转为采购申请、生产订单（已计划）或委外单；例外信息推送给采购员 / 计划员处理">
    <ErpPanel flush>
      <el-tabs v-model="tab" class="page-tabs">
        <el-tab-pane label="采购建议" name="PURCHASE" />
        <el-tab-pane label="生产建议" name="MAKE" />
        <el-tab-pane label="委外建议" name="OUTSOURCE" />
        <el-tab-pane label="例外信息" name="exception" />
      </el-tabs>
      <div class="body">
        <el-form inline @submit.prevent>
          <el-form-item label="物料"><MaterialSelect v-model="q.materialId" class="w-select" /></el-form-item>
          <template v-if="!isException">
            <el-form-item label="计划员"><UserSelect v-model="q.plannerId" /></el-form-item>
            <el-form-item v-if="tab === 'PURCHASE'" label="采购员"><UserSelect v-model="q.buyerId" /></el-form-item>
            <el-form-item label="状态">
              <el-select v-model="q.statuses" class="w-status">
                <el-option value="PENDING" label="待处理" /><el-option value="CONVERTED" label="已转单" /><el-option value="IGNORED" label="已忽略" />
                <el-option value="ALL" label="全部" />
              </el-select>
            </el-form-item>
            <el-form-item><el-checkbox v-model="q.lateOnly">仅已延迟</el-checkbox></el-form-item>
          </template>
          <el-form-item v-else label="处理">
            <el-select v-model="exHandled" clearable class="w-status" placeholder="全部"><el-option :value="false" label="未处理" /><el-option :value="true" label="已处理" /></el-select>
          </el-form-item>
          <el-form-item><el-button type="primary" :loading="loading" @click="page.pageNo = 1; load()">查询</el-button></el-form-item>
        </el-form>

        <template v-if="!isException">
          <div class="bar">
            <el-button v-perm="'pmc:mrp:convert'" type="primary" :loading="converting" @click="convert">
              {{ tab === 'PURCHASE' ? '转采购申请' : tab === 'MAKE' ? '转生产订单' : '转委外单' }}
            </el-button>
            <el-checkbox v-if="tab === 'MAKE'" v-model="release">同时下达</el-checkbox>
            <el-button v-perm="'pmc:mrp:ignore'" @click="ignore">忽略</el-button>
            <ExportButton url="/pmc/mrp/suggestions/export" :params="() => ({ ...q, runId, type: tab })" filename="MRP 建议" permission="pmc:mrp:query" />
            <span v-if="runId" class="text-muted">运算 ID {{ runId }}</span>
          </div>
          <el-table v-loading="loading" :data="rows" row-key="id" max-height="600" @selection-change="(v: SuggestionRow[]) => (selected = v)">
            <el-table-column type="selection" width="44" :selectable="(r: SuggestionRow) => r.status === 'PENDING'" />
            <el-table-column label="物料" min-width="200" fixed>
              <template #default="{ row }">
                <el-link type="primary" underline="never" @click="router.push({ path: '/pmc/mrp/balance', query: { materialId: row.materialId, runId: row.runId } })">
                  {{ row.materialCode }}
                </el-link> {{ row.materialName }}
              </template>
            </el-table-column>
            <el-table-column label="建议数量" width="140">
              <template #default="{ row }">
                <QtyInput v-if="row.status === 'PENDING'" :model-value="row.qty" :uom="row.baseUom" @change="(v?: string) => v && update(row, { qty: v })" />
                <span v-else>{{ formatQty(row.qty) }}</span>
                <div v-if="num(row.qty) !== num(row.originalQty)" class="text-muted">原 {{ formatQty(row.originalQty) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="净需求" width="90" align="right"><template #default="{ row }">{{ formatQty(row.netRequirement) }}</template></el-table-column>
            <el-table-column prop="baseUom" label="单位" width="60" />
            <el-table-column label="需求日期" width="150">
              <template #default="{ row }">
                <el-date-picker v-if="row.status === 'PENDING'" :model-value="row.requiredDate" value-format="YYYY-MM-DD" :clearable="false" class="w-full"
                                @update:model-value="(v: string) => update(row, { requiredDate: v })" />
                <span v-else>{{ row.requiredDate }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="tab === 'MAKE' ? '建议开工' : '建议下达'" width="140">
              <template #default="{ row }">
                {{ row.releaseDate }}
                <ErpBadge v-if="row.late" type="danger" :dot="false">延迟 {{ row.lateDays }} 天</ErpBadge>
              </template>
            </el-table-column>
            <el-table-column v-if="tab !== 'MAKE'" label="供应商" width="200">
              <template #default="{ row }">
                <SupplierSelect v-if="row.status === 'PENDING'" :model-value="row.supplierId" @update:model-value="(v?: string | string[]) => v && update(row, { supplierId: String(v) })" />
                <span v-else>{{ row.supplierName || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="可用 / 在途" width="120" align="right">
              <template #default="{ row }">{{ formatQty(row.availableQty) }} / {{ formatQty(row.inTransitQty) }}</template>
            </el-table-column>
            <el-table-column label="需求来源" min-width="180">
              <template #default="{ row }"><el-link type="primary" underline="never" @click="openPeg(row)">{{ row.sourceSummary || '查看' }}</el-link></template>
            </el-table-column>
            <el-table-column prop="plannerName" label="计划员" width="80" />
            <el-table-column v-if="tab === 'PURCHASE'" prop="buyerName" label="采购员" width="80" />
            <el-table-column label="状态" width="130">
              <template #default="{ row }">
                <StatusTag :value="row.status" :map="SUGGESTION_STATUS" />
                <el-link v-if="docLink(row)" type="primary" underline="never" @click="router.push(docLink(row)!)">查看单据</el-link>
                <el-tooltip v-if="row.ignoreReason" :content="row.ignoreReason"><span class="text-muted">原因</span></el-tooltip>
              </template>
            </el-table-column>
            <template #empty><ErpEmpty compact description="没有建议（运行 MRP 后显示最近一次成功运算的结果）" /></template>
          </el-table>
        </template>

        <template v-else>
          <div class="bar">
            <el-button v-perm="'pmc:mrp:convert'" type="primary" @click="push">推送处理</el-button>
            <span class="text-muted">推送后负责人（采购员 / 计划员）的工作台出现待办</span>
          </div>
          <el-table v-loading="loading" :data="exRows" row-key="id" max-height="600" @selection-change="(v: ExceptionRow[]) => (exSelected = v)">
            <el-table-column type="selection" width="44" />
            <el-table-column label="类型" width="90"><template #default="{ row }"><StatusTag :value="row.type" :map="EXCEPTION_TYPE" /></template></el-table-column>
            <el-table-column label="物料" min-width="180"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
            <el-table-column label="单据" width="160">
              <template #default="{ row }">
                <el-link v-if="docRoute(row)" type="primary" underline="never" @click="router.push(docRoute(row)!)">{{ row.docNo }}</el-link>
                <span v-else>{{ row.docNo || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="supplyDate" label="当前日期" width="105" />
            <el-table-column prop="suggestedDate" label="建议日期" width="105" />
            <el-table-column label="数量" width="90" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
            <el-table-column prop="message" label="说明" min-width="260" show-overflow-tooltip />
            <el-table-column prop="ownerName" label="负责人" width="80" />
            <el-table-column label="已推送" width="80"><template #default="{ row }">{{ row.pushedAt ? '是' : '-' }}</template></el-table-column>
            <el-table-column label="已处理" width="80">
              <template #default="{ row }"><el-checkbox :model-value="row.handled" @change="(v: unknown) => toggleHandled(row, v)" /></template>
            </el-table-column>
            <template #empty><ErpEmpty compact description="没有例外信息" /></template>
          </el-table>
        </template>
        <ErpPagination v-model:page-no="page.pageNo" v-model:page-size="page.pageSize" :total="total" @change="load" />
      </div>
    </ErpPanel>

    <el-drawer v-model="pegVisible" :title="pegTitle" size="640px" append-to-body>
      <div v-for="(lvl, i) in pegStack" :key="i" class="peg">
        <div class="peg-title">{{ i === 0 ? '' : '↑ ' }}{{ lvl.title }}</div>
        <el-table :data="lvl.rows">
          <el-table-column label="需求类型" width="110"><template #default="{ row }">{{ PEG_TYPE[row.demandType] ?? row.demandType }}</template></el-table-column>
          <el-table-column label="来源" min-width="180">
            <template #default="{ row }">
              <el-link v-if="row.parentResultId" type="primary" underline="never" @click="drill(row)">{{ row.parentCode }} 的计划订单（展开）</el-link>
              <el-link v-else-if="row.demandType === 'SALES_ORDER'" type="primary" underline="never" @click="openSource(row)">{{ row.sourceNo }}</el-link>
              <span v-else>{{ row.sourceNo || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
          <el-table-column prop="requiredDate" label="需求日期" width="110" />
        </el-table>
      </div>
    </el-drawer>
  </ErpPage>
</template>

<style scoped>
.page-tabs { padding: 0 var(--erp-space-5); }
.body { padding: 0 var(--erp-space-5) var(--erp-space-5); }
.bar { display: flex; align-items: center; gap: var(--erp-space-3); margin-bottom: var(--erp-space-3); }
.w-select { width: 220px; }
.w-status { width: 110px; }
.peg { margin-bottom: var(--erp-space-4); }
.peg-title { margin-bottom: var(--erp-space-2); font-weight: var(--erp-font-weight-medium); }
</style>
