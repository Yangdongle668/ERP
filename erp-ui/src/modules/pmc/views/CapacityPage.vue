<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { formatQty, today, toDateString } from '@/utils/format'
import { capacityApi, num, pct, type CalendarDay, type CalendarMonth, type LoadDetail, type LoadRow, type WorkCenterSimple } from '../api/pmc'

defineOptions({ name: 'PmcCapacityPage' })

/** 产能日历与负荷分析（需求 06-05 4.1、4.2）：例外日（节假日、加班、停机）橙色；负荷率 > 100% 红色 */
const tab = ref<'calendar' | 'load'>('calendar')
const wcs = ref<WorkCenterSimple[]>([])
const wc = ref<string>('0')
const month = ref(today().slice(0, 7))
const cal = ref<CalendarMonth>()
async function loadCal() {
  cal.value = await capacityApi.month(wc.value, month.value)
}
onMounted(async () => {
  wcs.value = await capacityApi.workCenters()
  loadCal()
})
const blanks = computed(() => {
  if (!cal.value?.days.length) return 0
  const first = new Date(cal.value.days[0].date).getDay()
  return (first + 6) % 7
})

// ---------- 编辑 ----------
const edit = ref<{ visible: boolean; batch: boolean; date?: string; range?: [string, string]; hours?: string; reason?: string; restore: boolean }>({
  visible: false, batch: false, restore: false
})
function openDay(d: CalendarDay) {
  edit.value = { visible: true, batch: false, date: d.date, hours: d.availableHours, reason: d.reason, restore: false }
}
function openBatch() {
  edit.value = { visible: true, batch: true, range: [today(), today()], hours: '0', reason: 'HOLIDAY', restore: false }
}
async function saveEdit() {
  const e = edit.value
  const hours = e.restore ? null : e.hours
  if (!e.restore && (hours === undefined || hours === '')) return ElMessage.warning('请填写可用工时')
  if (e.batch) await capacityApi.batch({ workCenterId: wc.value, from: e.range![0], to: e.range![1], hours, reason: e.reason })
  else await capacityApi.save({ workCenterId: wc.value, date: e.date!, hours, reason: e.reason })
  ElMessage.success('已保存')
  edit.value.visible = false
  loadCal()
}

// ---------- 负荷分析 ----------
const addDays = (d: string, n: number) => {
  const x = new Date(d)
  x.setDate(x.getDate() + n)
  return toDateString(x)
}
const loadRange = ref<[string, string]>([today(), addDays(today(), 13)])
const deptId = ref<string>()
const loads = ref<LoadRow[]>([])
const loadLoading = ref(false)
async function loadLoad() {
  loadLoading.value = true
  try {
    loads.value = await capacityApi.load({ from: loadRange.value[0], to: loadRange.value[1], deptId: deptId.value })
  } finally {
    loadLoading.value = false
  }
}
const detail = ref<{ visible: boolean; title: string; rows: LoadDetail[] }>({ visible: false, title: '', rows: [] })
async function openDetail(row: unknown, date: string) {
  const r = row as LoadRow
  detail.value = { visible: true, title: `${r.workCenterName} ${date} 负荷明细`, rows: await capacityApi.loadDetail(r.workCenterId, date) }
}
function onTab(t: string | number) {
  if (t === 'load' && !loads.value.length) loadLoad()
}
</script>

<template>
  <ErpPage description="产能日历：未设置例外时周一至周六取工作中心日产能、周日休息；全厂例外（节假日）对所有工作中心生效">
    <ErpPanel flush>
      <el-tabs v-model="tab" class="page-tabs" @tab-change="onTab">
        <el-tab-pane label="产能日历" name="calendar">
          <div class="bar">
            <el-select v-model="wc" class="wc" @change="loadCal">
              <el-option value="0" label="全厂（节假日）" />
              <el-option v-for="w in wcs" :key="w.id" :value="w.id" :label="`${w.code} ${w.name}`" />
            </el-select>
            <el-date-picker v-model="month" type="month" value-format="YYYY-MM" :clearable="false" @change="loadCal" />
            <el-button v-perm="'pmc:capacity:update'" @click="openBatch">批量设置</el-button>
          </div>
          <div class="cal">
            <div v-for="w in ['一', '二', '三', '四', '五', '六', '日']" :key="w" class="wk">周{{ w }}</div>
            <div v-for="i in blanks" :key="`b${i}`" class="cell empty" />
            <div v-for="d in cal?.days ?? []" :key="d.date" class="cell" :class="{ ex: d.exception, over: num(d.loadRate) > 1 }" @click="openDay(d)">
              <div class="date">{{ d.date.slice(8) }}</div>
              <div class="hours num">{{ d.availableHours ?? '-' }} h</div>
              <div v-if="wc !== '0'" class="load">负荷 {{ formatQty(d.loadHours, 1) }} h · {{ pct(d.loadRate) }}</div>
              <div v-if="d.reason" class="reason"><DictTag type="pmc_calendar_reason" :value="d.reason" /></div>
            </div>
          </div>
        </el-tab-pane>
        <el-tab-pane label="负荷分析" name="load">
          <div class="bar">
            <el-date-picker v-model="loadRange" type="daterange" value-format="YYYY-MM-DD" :clearable="false" />
            <OrgTreeSelect v-model="deptId" only-dept class="wc" placeholder="车间" />
            <el-button type="primary" :loading="loadLoading" @click="loadLoad">查询</el-button>
          </div>
          <el-table v-loading="loadLoading" :data="loads" border max-height="600">
            <el-table-column label="工作中心" width="160" fixed><template #default="{ row }">{{ row.workCenterCode }} {{ row.workCenterName }}</template></el-table-column>
            <el-table-column v-for="(c, i) in loads[0]?.cells ?? []" :key="c.date" :label="c.date.slice(5)" width="100" align="right">
              <template #default="{ row }">
                <el-link underline="never" :class="{ 'text-danger': row.cells[i].overloaded }" @click="openDetail(row, row.cells[i].date)">
                  {{ formatQty(row.cells[i].loadHours, 1) }}/{{ formatQty(row.cells[i].capacityHours, 1) }}
                </el-link>
              </template>
            </el-table-column>
            <el-table-column label="合计" width="120" align="right" fixed="right">
              <template #default="{ row }">{{ formatQty(row.totalLoad, 1) }}/{{ formatQty(row.totalCapacity, 1) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </ErpPanel>

    <el-dialog v-model="edit.visible" :title="edit.batch ? '批量设置产能' : `产能 ${edit.date}`" width="460px" append-to-body>
      <el-form label-width="90px">
        <el-form-item v-if="edit.batch" label="日期范围"><el-date-picker v-model="edit.range" type="daterange" value-format="YYYY-MM-DD" :clearable="false" /></el-form-item>
        <el-form-item label="恢复默认"><el-switch v-model="edit.restore" /></el-form-item>
        <template v-if="!edit.restore">
          <el-form-item label="可用工时"><NumberInput v-model="edit.hours" :precision="2" :min="0" :max="24" placeholder="0 表示停工" /></el-form-item>
          <el-form-item label="原因"><DictSelect v-model="edit.reason" type="pmc_calendar_reason" /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="edit.visible = false">取消</el-button>
        <el-button v-perm="'pmc:capacity:update'" type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detail.visible" :title="detail.title" width="720px" append-to-body>
      <el-table :data="detail.rows">
        <el-table-column prop="prodOrderNo" label="订单" width="150" />
        <el-table-column label="产品" min-width="160"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
        <el-table-column label="工序" width="120"><template #default="{ row }">{{ row.operationSeq }} {{ row.operation }}</template></el-table-column>
        <el-table-column label="工时" width="90" align="right"><template #default="{ row }">{{ formatQty(row.hours, 2) }}</template></el-table-column>
        <el-table-column label="来源" width="90"><template #default="{ row }">{{ row.scheduled ? '排产' : '计划均摊' }}</template></el-table-column>
      </el-table>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.page-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
.bar { display: flex; flex-wrap: wrap; align-items: center; gap: var(--erp-space-2); margin-bottom: var(--erp-space-3); }
.wc { width: 220px; }
.cal { display: grid; grid-template-columns: repeat(7, 1fr); gap: var(--erp-space-1); }
.wk { text-align: center; color: var(--erp-color-text-secondary); padding: var(--erp-space-1); }
.cell { min-height: 84px; padding: var(--erp-space-2); border: 1px solid var(--erp-color-border-light); border-radius: var(--erp-radius-control); cursor: pointer; }
.cell.empty { border: none; cursor: default; }
.cell:hover:not(.empty) { background: var(--erp-color-hover); }
.cell.ex { background: var(--erp-color-warning-bg); }
.cell.over .load { color: var(--erp-color-error); }
.date { font-weight: var(--erp-font-weight-medium); }
.hours { font-size: var(--erp-font-size-section-title); }
.load, .reason { font-size: var(--erp-font-size-caption); color: var(--erp-color-text-secondary); }
</style>
