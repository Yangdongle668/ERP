<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatQty } from '@/utils/format'
import { mpsApi, num, PLAN_STATUS, type CapRow, type MpsDetail, type MpsMatrix } from '../api/pmc'

defineOptions({ name: 'PmcMpsEdit' })

/**
 * MPS 编制（需求 06-02 第 3 节，专用矩阵）：每个物料 4 行——需求、在制完工（只读）、计划生产（可编辑）、预计结存（< 安全库存橙色、< 0 红色）；
 * 过去的周锁定。
 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<MpsDetail>()
const m = ref<MpsMatrix>()
const dirty = ref(false)
const saving = ref(false)
const cap = ref<CapRow[]>()

async function load() {
  d.value = await mpsApi.get(id.value)
  m.value = await mpsApi.matrix(id.value)
  dirty.value = false
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}
onMounted(load)
const draft = computed(() => d.value?.mpsStatus === 'DRAFT')

function recompute() {
  if (!m.value) return
  for (const r of m.value.rows) {
    let proj = num(r.openingQty)
    for (const c of r.cells) {
      proj = proj + num(c.plannedQty) + num(c.wipQty) - num(c.demandQty)
      c.projectedQty = String(proj)
    }
  }
  dirty.value = true
}
const projClass = (r: { safetyStock: string }, v: string) => (num(v) < 0 ? 'neg' : num(v) < num(r.safetyStock) ? 'low' : '')

async function save() {
  if (!m.value) return
  saving.value = true
  try {
    m.value = await mpsApi.saveMatrix(id.value, m.value.rows.map((r) => ({
      materialId: r.materialId, cells: r.cells.map((c) => ({ week: c.week, plannedQty: c.plannedQty, remark: c.remark }))
    })))
    dirty.value = false
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}
async function generate() {
  if (dirty.value) await save()
  m.value = await mpsApi.generate(id.value)
  ElMessage.success('已按需求生成计划')
}
const addId = ref<string>()
async function addMaterial() {
  if (!addId.value || !m.value) return
  if (m.value.rows.some((r) => r.materialId === addId.value)) return ElMessage.warning('该物料已在计划中')
  if (dirty.value) await save()
  m.value = await mpsApi.generate(id.value, [addId.value])
  addId.value = undefined
}
async function removeRow(materialId: string) {
  if (!m.value) return
  m.value.rows = m.value.rows.filter((r) => r.materialId !== materialId)
  await save()
}
async function capacityCheck() {
  if (dirty.value) await save()
  cap.value = await mpsApi.capacityCheck(id.value)
}

const actions = computed<DocAction[]>(() => [
  { key: 'close', label: '关闭', permission: 'pmc:mps:publish', visible: () => d.value?.mpsStatus === 'PUBLISHED', confirm: '关闭后 MRP 不再使用该 MPS，确定吗？',
    handler: async () => {
      await mpsApi.close(id.value)
      ElMessage.success('已关闭')
      load()
    } },
  { key: 'copy', label: '复制调整', permission: 'pmc:mps:create', visible: () => !draft.value, handler: async () => {
    const nid = await mpsApi.copy(id.value)
    ElMessage.success('已复制为新草稿，发布后替代本 MPS')
    router.push(`/pmc/mps/${nid}`)
  } },
  { key: 'publish', label: '发布', type: 'primary', permission: 'pmc:mps:publish', visible: () => draft.value,
    confirm: '发布后周期重叠的已发布 MPS 自动关闭，确定吗？', handler: async () => {
      if (dirty.value) await save()
      await mpsApi.publish(id.value)
      ElMessage.success('已发布')
      load()
    } }
])
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d ? `${d.docNo} ${d.title}` : 'MPS'" :status="d?.mpsStatus" :status-map="PLAN_STATUS" :actions="actions" @back="router.push('/pmc/mps')">
        <template #extra><span v-if="d" class="text-muted">{{ d.startWeek }} ~ {{ d.endWeek }}</span></template>
      </DocPageHeader>
    </template>

    <ErpPanel v-if="m" flush>
      <div class="bar">
        <template v-if="draft">
          <MaterialSelect v-model="addId" :types="['FINISHED', 'SEMI_FINISHED']" class="add" placeholder="添加物料（自制件）" />
          <el-button v-perm="'pmc:mps:update'" icon="Plus" @click="addMaterial">添加物料</el-button>
          <el-button v-perm="'pmc:mps:update'" @click="generate">按需求生成</el-button>
        </template>
        <el-button @click="capacityCheck">产能检查</el-button>
        <el-button v-if="draft" v-perm="'pmc:mps:update'" type="primary" :loading="saving" :disabled="!dirty" @click="save">保存</el-button>
        <span class="text-muted">计划 = max(0, 需求 + 安全库存 − 上周结存 − 在制)，按 MPQ 取整</span>
      </div>
      <div class="grid-wrap">
        <table class="grid">
          <thead>
            <tr>
              <th class="sticky">物料</th>
              <th class="sticky2">项目</th>
              <th v-for="w in m.weeks" :key="w">{{ w }}</th>
              <th>合计</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="r in m.rows" :key="r.materialId">
              <tr v-for="(kind, i) in ['需求', '在制完工', '计划生产', '预计结存']" :key="kind" :class="{ first: i === 0 }">
                <td v-if="i === 0" rowspan="4" class="sticky mat">
                  <div class="code">{{ r.materialCode }}</div>
                  <div>{{ r.materialName }}</div>
                  <div class="text-muted">期初 {{ formatQty(r.openingQty) }}，安全库存 {{ formatQty(r.safetyStock) }}</div>
                  <el-button v-if="draft" link type="danger" @click="removeRow(r.materialId)">移除</el-button>
                </td>
                <td class="sticky2 kind">{{ kind }}</td>
                <td v-for="c in r.cells" :key="c.week" class="num" :class="i === 3 ? projClass(r, c.projectedQty) : ''">
                  <template v-if="i === 0">{{ formatQty(c.demandQty) }}</template>
                  <template v-else-if="i === 1">{{ formatQty(c.wipQty) }}</template>
                  <template v-else-if="i === 2">
                    <QtyInput v-if="draft && !c.locked" v-model="c.plannedQty" :uom="r.baseUom" borderless @change="recompute" />
                    <span v-else>{{ formatQty(c.plannedQty) }}</span>
                  </template>
                  <template v-else>{{ formatQty(c.projectedQty) }}</template>
                </td>
                <td class="num total">
                  <template v-if="i === 0">{{ formatQty(r.cells.reduce((s, c) => s + num(c.demandQty), 0)) }}</template>
                  <template v-else-if="i === 1">{{ formatQty(r.cells.reduce((s, c) => s + num(c.wipQty), 0)) }}</template>
                  <template v-else-if="i === 2">{{ formatQty(r.cells.reduce((s, c) => s + num(c.plannedQty), 0)) }}</template>
                </td>
              </tr>
            </template>
            <tr v-if="!m.rows.length"><td :colspan="m.weeks.length + 3"><ErpEmpty compact description="点击“按需求生成”加入期间内有需求的自制件，或手工添加物料" /></td></tr>
          </tbody>
        </table>
      </div>
    </ErpPanel>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <ErpPanel v-if="cap" title="产能检查" description="按产品工艺路线换算的工作中心周负荷（小时），超出产能的周标红">
      <el-table :data="cap">
        <el-table-column label="工作中心" width="180" fixed><template #default="{ row }">{{ row.workCenterCode }} {{ row.workCenterName }}</template></el-table-column>
        <el-table-column v-for="(w, i) in m?.weeks ?? []" :key="w" :label="w" width="120" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.cells[i]?.overloaded }">{{ formatQty(row.cells[i]?.loadHours, 1) }} / {{ formatQty(row.cells[i]?.capacityHours, 1) }}</span>
          </template>
        </el-table-column>
        <template #empty><ErpEmpty compact description="计划物料没有工艺路线或计划为 0" /></template>
      </el-table>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.bar { display: flex; flex-wrap: wrap; align-items: center; gap: var(--erp-space-2); padding: var(--erp-space-4) var(--erp-space-5); }
.add { width: 260px; }
.grid-wrap { overflow: auto; max-height: 70vh; padding: 0 var(--erp-space-5) var(--erp-space-5); }
.grid { border-collapse: collapse; min-width: 100%; }
.grid th, .grid td { border: 1px solid var(--erp-color-border-light); padding: var(--erp-space-1) var(--erp-space-2); white-space: nowrap; }
.grid th { background: var(--erp-color-surface-subtle); font-weight: var(--erp-font-weight-medium); position: sticky; top: 0; z-index: 1; }
.grid td.num { text-align: right; min-width: 96px; }
.grid tr.first td { border-top: 2px solid var(--erp-color-border); }
.sticky { position: sticky; left: 0; background: var(--erp-color-surface); z-index: 2; min-width: 180px; }
.sticky2 { position: sticky; left: 180px; background: var(--erp-color-surface); z-index: 2; }
.mat { vertical-align: top; }
.code { font-weight: var(--erp-font-weight-medium); }
.kind { color: var(--erp-color-text-secondary); }
.total { background: var(--erp-color-surface-subtle); }
.low { color: var(--erp-color-warning); }
.neg { color: var(--erp-color-error); }
</style>
