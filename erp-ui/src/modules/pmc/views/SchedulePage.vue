<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { formatQty, today, toDateString } from '@/utils/format'
import { capacityApi, scheduleApi, type ApplyRow, type ScheduleRow, type SimulateResult, type WorkCenterSimple } from '../api/pmc'

defineOptions({ name: 'PmcSchedulePage' })

/**
 * 排产（需求 06-05 4.3）：按工作中心的甘特图；拖动工序块调整开始日期（左右）与工作中心（上下拖到其他行），点击打开调整 / 锁定弹窗；
 * 插单模拟；应用到生产订单。图例：蓝色按期、红色延期、虚线边框锁定（锁定的工序不能拖动）。
 */
const router = useRouter()
const addDays = (d: string, n: number) => {
  const x = new Date(d)
  x.setDate(x.getDate() + n)
  return toDateString(x)
}
const range = ref<[string, string]>([addDays(today(), -1), addDays(today(), 20)])
const deptId = ref<string>()
const mode = ref<string>('FINITE')
const rows = ref<ScheduleRow[]>([])
const loading = ref(false)
const wcs = ref<WorkCenterSimple[]>([])

async function load() {
  loading.value = true
  try {
    rows.value = await scheduleApi.list({ from: range.value[0], to: range.value[1], deptId: deptId.value })
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  wcs.value = await capacityApi.workCenters()
  load()
})

const days = computed(() => {
  const out: string[] = []
  for (let d = range.value[0]; d <= range.value[1]; d = addDays(d, 1)) out.push(d)
  return out
})
const spanMs = computed(() => new Date(addDays(range.value[1], 1)).getTime() - new Date(range.value[0]).getTime())
const lanes = computed(() => {
  const map = new Map<string, { id: string; name: string; items: ScheduleRow[] }>()
  for (const r of rows.value) {
    const key = r.workCenterId ?? '-'
    if (!map.has(key)) map.set(key, { id: key, name: r.workCenterName ?? '未指定', items: [] })
    map.get(key)!.items.push(r)
  }
  return [...map.values()].sort((a, b) => a.name.localeCompare(b.name))
})
function style(r: ScheduleRow) {
  const start = new Date(r.schedStart.replace(' ', 'T')).getTime()
  const end = Math.max(new Date(r.schedEnd.replace(' ', 'T')).getTime(), start + 3600 * 1000)
  const from = new Date(range.value[0]).getTime()
  const left = Math.max(0, ((start - from) / spanMs.value) * 100)
  const width = Math.max(0.8, ((end - Math.max(start, from)) / spanMs.value) * 100)
  return { left: `${left}%`, width: `${Math.min(width, 100 - left)}%` }
}

// ---------- 运行 ----------
const running = ref(false)
async function run() {
  running.value = true
  try {
    const r = await scheduleApi.run({ deptId: deptId.value, mode: mode.value })
    ElMessage.success(`已排产 ${r.orderCount} 张订单、${r.operationCount} 道工序，延期 ${r.lateCount} 张`)
    load()
  } finally {
    running.value = false
  }
}

// ---------- 工序块 ----------
const cur = ref<ScheduleRow>()
const curVisible = ref(false)
const adj = ref<{ startDate?: string; workCenterId?: string }>({})
function open(r: ScheduleRow) {
  cur.value = r
  adj.value = { startDate: r.schedStart.slice(0, 10), workCenterId: r.workCenterId }
  curVisible.value = true
}
async function saveAdjust() {
  if (!cur.value || !adj.value.startDate) return
  await scheduleApi.adjust(cur.value.id, { startDate: adj.value.startDate, workCenterId: adj.value.workCenterId })
  ElMessage.success('已调整，后续工序顺延')
  curVisible.value = false
  load()
}
async function toggleLock() {
  if (!cur.value) return
  await scheduleApi.lock(cur.value.id, !cur.value.locked)
  curVisible.value = false
  load()
}

// ---------- 拖动 ----------
const me = useUserStore()
const canAdjust = computed(() => me.hasPermission('pmc:schedule:adjust'))
interface Drag { row: ScheduleRow; lane: string; target: string; startX: number; startY: number; dayPx: number; days: number; dy: number; moved: boolean }
const drag = ref<Drag | null>(null)
const draggable = (r: ScheduleRow) => canAdjust.value && !r.locked

function onDown(e: PointerEvent, r: ScheduleRow, lane: string) {
  if (e.button !== 0) return
  const el = e.currentTarget as HTMLElement
  const track = el.parentElement as HTMLElement
  drag.value = { row: r, lane, target: lane, startX: e.clientX, startY: e.clientY, dayPx: track.clientWidth / Math.max(1, days.value.length), days: 0, dy: 0,
    moved: false }
  el.setPointerCapture(e.pointerId)
}
function onMove(e: PointerEvent) {
  const d = drag.value
  if (!d) return
  const dx = e.clientX - d.startX
  const dy = e.clientY - d.startY
  if (!d.moved && Math.abs(dx) < 4 && Math.abs(dy) < 4) return
  d.moved = true
  if (!draggable(d.row)) return
  d.days = Math.round(dx / d.dayPx)
  d.dy = dy
  const lane = (document.elementFromPoint(e.clientX, e.clientY) as HTMLElement | null)?.closest<HTMLElement>('[data-lane]')?.dataset.lane
  if (lane) d.target = lane
}
async function onUp() {
  const d = drag.value
  drag.value = null
  if (!d) return
  if (!d.moved) return open(d.row)
  if (!draggable(d.row)) return
  const changeWc = d.target !== d.lane && d.target !== '-'
  if (d.days === 0 && !changeWc) return
  const startDate = addDays(d.row.schedStart.slice(0, 10), d.days)
  const workCenterId = changeWc ? d.target : d.row.workCenterId
  await scheduleApi.adjust(d.row.id, { startDate, workCenterId })
  const wcName = changeWc ? lanes.value.find((l) => l.id === d.target)?.name : undefined
  ElMessage.success(`${d.row.prodOrderNo} 工序 ${d.row.operationSeq} 已调整到 ${startDate}${wcName ? `（${wcName}）` : ''}，后续工序顺延`)
  load()
}
function dragStyle(r: ScheduleRow) {
  const d = drag.value
  if (!d || d.row.id !== r.id || !d.moved || !draggable(r)) return {}
  return { transform: `translate(${d.days * d.dayPx}px, ${d.dy}px)` }
}

// ---------- 插单模拟 ----------
const simVisible = ref(false)
const sim = ref<{ prodOrderId?: string; priority: number }>({ priority: 1 })
const simResult = ref<SimulateResult>()
const orderOptions = computed(() => {
  const m = new Map<string, string>()
  rows.value.forEach((r) => m.set(r.prodOrderId, `${r.prodOrderNo} ${r.materialCode}`))
  return [...m.entries()].map(([value, label]) => ({ value, label }))
})
async function simulate() {
  if (!sim.value.prodOrderId) return ElMessage.warning('请选择订单')
  simResult.value = await scheduleApi.simulate(sim.value.prodOrderId, sim.value.priority)
}
async function confirmInsert() {
  if (!sim.value.prodOrderId) return
  await scheduleApi.confirm(sim.value.prodOrderId, sim.value.priority)
  ElMessage.success('已插单并锁定该订单的工序')
  simVisible.value = false
  simResult.value = undefined
  load()
}

// ---------- 应用 ----------
const applyVisible = ref(false)
const applyRows = ref<ApplyRow[]>([])
const applySel = ref<ApplyRow[]>([])
async function openApply() {
  applyRows.value = await scheduleApi.applyPreview()
  applyVisible.value = true
}
async function apply() {
  const ids = applySel.value.map((r) => r.prodOrderId)
  if (!ids.length) return ElMessage.warning('请勾选订单')
  const n = await scheduleApi.apply(ids)
  ElNotification({ type: 'success', title: '已应用', message: `${n} 张生产订单的计划开工 / 完工已更新`, duration: 5000 })
  applyVisible.value = false
  load()
}
</script>

<template>
  <ErpPage description="有限产能：按优先级、需求日期把工序排到工作中心的具体日期；无限产能：从需求日期倒排，只用于负荷分析">
    <ErpPanel>
      <div class="bar">
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" :clearable="false" @change="load" />
        <OrgTreeSelect v-model="deptId" only-dept class="dept" placeholder="车间" @change="load" />
        <el-select v-model="mode" class="mode">
          <el-option value="FINITE" label="有限产能" /><el-option value="INFINITE" label="无限产能" />
        </el-select>
        <el-button v-perm="'pmc:schedule:run'" type="primary" :loading="running" @click="run">运行排产</el-button>
        <el-button v-perm="'pmc:schedule:apply'" @click="openApply">应用到生产订单</el-button>
        <el-button v-perm="'pmc:schedule:run'" @click="simVisible = true">插单模拟</el-button>
        <span class="legend"><i class="lg ok" />按期 <i class="lg late" />延期 <i class="lg lock" />锁定</span>
      </div>
      <div v-loading="loading" class="gantt">
        <div class="row head">
          <div class="lane-name">工作中心</div>
          <div class="track">
            <div v-for="d in days" :key="d" class="day" :class="{ sun: new Date(d).getDay() === 0, today: d === today() }">{{ d.slice(5) }}</div>
          </div>
        </div>
        <div v-for="l in lanes" :key="l.id" class="row" :data-lane="l.id" :class="{ drop: drag?.moved && drag.target === l.id && drag.target !== drag.lane }">
          <div class="lane-name">{{ l.name }}</div>
          <div class="track">
            <div v-for="d in days" :key="d" class="day bg" :class="{ sun: new Date(d).getDay() === 0 }" />
            <div v-for="r in l.items" :key="r.id" class="block" :class="{ late: r.late, locked: r.locked, movable: draggable(r), dragging: drag?.moved && drag.row.id === r.id }"
                 :style="[style(r), dragStyle(r)]"
                 :title="`${r.prodOrderNo} ${r.materialCode} 工序 ${r.operationSeq} ${r.operation ?? ''}\n数量 ${formatQty(r.qty)}，负荷 ${r.loadHours}h\n${r.schedStart} ~ ${r.schedEnd}\n需求日期 ${r.dueDate ?? '-'}${draggable(r) ? '\n拖动调整日期 / 工作中心，点击查看' : ''}`"
                 @pointerdown="onDown($event, r, l.id)" @pointermove="onMove" @pointerup="onUp" @pointercancel="drag = null">
              {{ r.locked ? '锁 ' : '' }}{{ r.prodOrderNo }}({{ r.operationSeq }})
            </div>
          </div>
        </div>
        <ErpEmpty v-if="!lanes.length && !loading" compact description="该范围没有排产结果，点击“运行排产”" />
      </div>
    </ErpPanel>

    <el-dialog v-model="curVisible" :title="cur ? `${cur.prodOrderNo} 工序 ${cur.operationSeq} ${cur.operation ?? ''}` : ''" width="520px" append-to-body>
      <el-descriptions v-if="cur" :column="2" border>
        <el-descriptions-item label="产品">{{ cur.materialCode }} {{ cur.materialName }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ formatQty(cur.qty) }}</el-descriptions-item>
        <el-descriptions-item label="开始">{{ cur.schedStart }}</el-descriptions-item>
        <el-descriptions-item label="结束">{{ cur.schedEnd }}</el-descriptions-item>
        <el-descriptions-item label="负荷">{{ cur.loadHours }} h</el-descriptions-item>
        <el-descriptions-item label="需求日期"><span :class="{ 'text-danger': cur.late }">{{ cur.dueDate ?? '-' }}</span></el-descriptions-item>
      </el-descriptions>
      <el-form v-if="cur && !cur.locked" label-width="90px" class="adj">
        <el-form-item label="开始日期"><el-date-picker v-model="adj.startDate" value-format="YYYY-MM-DD" :clearable="false" /></el-form-item>
        <el-form-item label="工作中心">
          <el-select v-model="adj.workCenterId" class="w-full"><el-option v-for="w in wcs" :key="w.id" :value="w.id" :label="`${w.code} ${w.name}`" /></el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="router.push(`/production/prod-order/${cur?.prodOrderId}`)">查看订单</el-button>
        <el-button v-perm="'pmc:schedule:adjust'" @click="toggleLock">{{ cur?.locked ? '解锁' : '锁定' }}</el-button>
        <el-button v-if="cur && !cur.locked" v-perm="'pmc:schedule:adjust'" type="primary" @click="saveAdjust">调整</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="simVisible" title="插单模拟" width="760px" append-to-body @closed="simResult = undefined">
      <el-form inline>
        <el-form-item label="订单">
          <el-select v-model="sim.prodOrderId" filterable class="order"><el-option v-for="o in orderOptions" :key="o.value" :value="o.value" :label="o.label" /></el-select>
        </el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="sim.priority" :min="1" :max="9" controls-position="right" /></el-form-item>
        <el-form-item><el-button type="primary" @click="simulate">模拟</el-button></el-form-item>
      </el-form>
      <template v-if="simResult">
        <p class="hint">{{ simResult.prodOrderNo }} 插单后预计完工 {{ simResult.newEnd ?? '-' }}；以下订单因此推迟（未保存）：</p>
        <el-table :data="simResult.delayed">
          <el-table-column prop="prodOrderNo" label="订单" width="150" />
          <el-table-column prop="materialCode" label="产品" width="130" />
          <el-table-column prop="oldEnd" label="原完工" width="110" />
          <el-table-column prop="newEnd" label="新完工" width="110" />
          <el-table-column label="延期天数" width="90" align="right"><template #default="{ row }"><span class="text-danger">{{ row.delayDays }}</span></template></el-table-column>
          <el-table-column label="需求日期" width="120">
            <template #default="{ row }">{{ row.dueDate ?? '-' }} <ErpBadge v-if="row.lateAfter" type="danger" :dot="false">延期</ErpBadge></template>
          </el-table-column>
          <template #empty><ErpEmpty compact description="没有订单因此推迟" /></template>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="simVisible = false">取消</el-button>
        <el-button v-if="simResult" v-perm="'pmc:schedule:run'" type="primary" @click="confirmInsert">确认插单</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="applyVisible" title="应用到生产订单" width="780px" append-to-body>
      <p class="hint">把排产的开工 / 完工写回生产订单计划日期（锁定的订单不应用；已下达订单的修改记录操作日志）。</p>
      <el-table :data="applyRows" @selection-change="(v: ApplyRow[]) => (applySel = v)">
        <el-table-column type="selection" width="44" />
        <el-table-column prop="prodOrderNo" label="订单" width="150" />
        <el-table-column prop="materialCode" label="产品" width="130" />
        <el-table-column label="原计划" min-width="180"><template #default="{ row }">{{ row.planStart }} ~ {{ row.planEnd }}</template></el-table-column>
        <el-table-column label="排产" min-width="180"><template #default="{ row }"><strong>{{ row.newStart }} ~ {{ row.newEnd }}</strong></template></el-table-column>
        <template #empty><ErpEmpty compact description="排产结果与生产订单计划日期一致" /></template>
      </el-table>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" @click="apply">应用</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.bar { display: flex; flex-wrap: wrap; align-items: center; gap: var(--erp-space-2); margin-bottom: var(--erp-space-3); }
.dept { width: 200px; }
.mode { width: 120px; }
.order { width: 260px; }
.legend { display: inline-flex; align-items: center; gap: var(--erp-space-1); margin-left: auto; color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-caption); }
.lg { display: inline-block; width: 14px; height: 10px; border-radius: var(--erp-radius-xs); margin-left: var(--erp-space-2); }
.lg.ok { background: var(--erp-color-primary-bg); border: 1px solid var(--erp-color-primary); }
.lg.late { background: var(--erp-color-error-bg); border: 1px solid var(--erp-color-error); }
.lg.lock { border: 1px dashed var(--erp-color-text-secondary); }
.gantt { overflow-x: auto; border: 1px solid var(--erp-color-border-light); border-radius: var(--erp-radius-card); }
.row { display: flex; min-width: 1000px; border-bottom: 1px solid var(--erp-color-border-light); }
.row.head { background: var(--erp-color-surface-subtle); font-size: var(--erp-font-size-caption); color: var(--erp-color-text-secondary); }
.lane-name { width: 140px; flex: none; padding: var(--erp-space-2); border-right: 1px solid var(--erp-color-border-light); }
.track { position: relative; flex: 1; display: flex; min-height: 40px; }
.day { flex: 1; text-align: center; padding: var(--erp-space-1) 0; border-right: 1px solid var(--erp-color-border-light); }
.day.bg.sun, .day.sun { background: var(--erp-color-surface-subtle); }
.day.today { color: var(--erp-color-primary); font-weight: var(--erp-font-weight-semibold); }
.block {
  position: absolute; top: 8px; height: 24px; padding: 0 var(--erp-space-1); overflow: hidden; white-space: nowrap; text-overflow: ellipsis; cursor: pointer;
  font-size: var(--erp-font-size-caption); line-height: 24px; border-radius: var(--erp-radius-xs);
  background: var(--erp-color-primary-bg); border: 1px solid var(--erp-color-primary); color: var(--erp-color-text);
}
.block.late { background: var(--erp-color-error-bg); border-color: var(--erp-color-error); }
.block.locked { border-style: dashed; }
.block.movable { cursor: grab; touch-action: none; }
.block.dragging { z-index: 2; cursor: grabbing; opacity: 0.85; box-shadow: var(--erp-shadow-float); }
.row.drop .track { background: var(--erp-color-primary-bg); }
.adj { margin-top: var(--erp-space-4); }
.hint { color: var(--erp-color-text-secondary); }
</style>
