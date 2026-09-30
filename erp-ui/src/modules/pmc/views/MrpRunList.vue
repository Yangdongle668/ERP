<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { labelOf, mrpApi, optionsOf, RUN_STATUS, RUN_TYPE_OPTIONS, type RunRow } from '../api/pmc'

defineOptions({ name: 'PmcMrpRunList' })

/** MRP 运算（需求 06-03 4.1，T1 + 运算弹窗）：后台运算，完成后通知发起人；同一时间只能有一个运算 */
const router = useRouter()
type Query = { runStatus?: string; dates?: [string, string] }
const { query, list, total, loading, load, search, reset } = useListPage<Query, RunRow>({
  api: (q) => {
    const { dates, ...rest } = q
    return mrpApi.runs({ ...rest, dateFrom: dates?.[0], dateTo: dates?.[1] } as never)
  },
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'runStatus', label: '状态', type: 'select', options: optionsOf(RUN_STATUS) },
  { prop: 'dates', label: '运算日期', type: 'daterange' }
]
const columns: TableColumn<RunRow>[] = [
  { prop: 'runNo', label: '运算号', width: 150, slot: true },
  { prop: 'runType', label: '类型', width: 90, formatter: (r) => labelOf(RUN_TYPE_OPTIONS, r.runType) },
  { prop: 'runStatus', label: '状态', width: 90, slot: true },
  { prop: 'startedAt', label: '开始', width: 150, type: 'datetime' },
  { prop: 'finishedAt', label: '结束', width: 150, type: 'datetime' },
  { prop: 'durationSeconds', label: '耗时(秒)', width: 90, align: 'right' },
  { prop: 'materialCount', label: '物料数', width: 80, align: 'right' },
  { prop: 'suggestionCount', label: '建议数', width: 80, align: 'right' },
  { prop: 'exceptionCount', label: '例外数', width: 80, align: 'right' },
  { prop: 'operatorName', label: '发起人', width: 90 },
  { prop: 'errorMsg', label: '失败原因', minWidth: 200 }
]
const asRow = (r: unknown) => r as RunRow

// ---------- 运算弹窗 ----------
const dlg = ref(false)
const form = ref<{ runType: string; orderLineIds: string; horizonDays?: number; includeForecast: boolean; includeSafety: boolean; useSubstitute: string }>({
  runType: 'FULL', orderLineIds: '', includeForecast: true, includeSafety: true, useSubstitute: ''
})
const starting = ref(false)
let timer: number | undefined
async function start() {
  const f = form.value
  const ids = f.orderLineIds.split(/[\s,，]+/).map((s) => s.trim()).filter(Boolean)
  if (f.runType === 'ORDER' && !ids.length) return ElMessage.warning('请填写要运算的销售订单行 ID')
  starting.value = true
  try {
    await mrpApi.run({ runType: f.runType, orderLineIds: ids.length ? ids : undefined, horizonDays: f.horizonDays, includeForecast: f.includeForecast,
      includeSafety: f.includeSafety, useSubstitute: f.useSubstitute === '' ? undefined : f.useSubstitute === 'Y' })
    ElMessage.success('已开始运算，完成后会通知你')
    dlg.value = false
    load()
    poll()
  } finally {
    starting.value = false
  }
}
function poll() {
  window.clearTimeout(timer)
  timer = window.setTimeout(async () => {
    await load()
    if (list.value.some((r) => r.runStatus === 'RUNNING')) poll()
  }, 2000)
}
onBeforeUnmount(() => window.clearTimeout(timer))
</script>

<template>
  <ErpPage description="MRP：按需求池（或 MPS）、BOM、库存、在途和在制逐层计算净需求，生成采购 / 生产 / 委外建议和例外信息">
    <ErpPanel>
      <template #filter><ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset" /></template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="pmc.mrp-run" :actions-width="150" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'pmc:mrp:run'" type="primary" icon="VideoPlay" @click="dlg = true">运行 MRP</el-button>
          <el-button @click="router.push('/pmc/mrp/balance')">供需平衡</el-button>
        </template>
        <template #col-runNo="{ row }">
          {{ asRow(row).runNo }} <ErpBadge v-if="asRow(row).latest" type="success" :dot="false">最新</ErpBadge>
        </template>
        <template #col-runStatus="{ row }">
          <el-progress v-if="asRow(row).runStatus === 'RUNNING'" :percentage="asRow(row).progress" :stroke-width="6" />
          <StatusTag v-else :value="asRow(row).runStatus" :map="RUN_STATUS" />
        </template>
        <template #actions="{ row }">
          <template v-if="asRow(row).runStatus === 'SUCCESS'">
            <el-button link type="primary" @click="router.push({ path: '/pmc/mrp/suggestions', query: { runId: asRow(row).id } })">查看建议</el-button>
            <el-button link type="primary" @click="router.push({ path: '/pmc/mrp/suggestions', query: { runId: asRow(row).id, tab: 'exception' } })">例外</el-button>
          </template>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="dlg" title="运行 MRP" width="520px" append-to-body>
      <el-form label-width="110px">
        <el-form-item label="运算类型">
          <el-radio-group v-model="form.runType">
            <el-radio v-for="o in RUN_TYPE_OPTIONS" :key="String(o.value)" :value="o.value">{{ o.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.runType === 'ORDER'" label="销售订单行">
          <el-input v-model="form.orderLineIds" type="textarea" :rows="2" placeholder="订单行 ID，多个用逗号分隔（可从交期回复、需求池复制）" />
        </el-form-item>
        <el-form-item label="展望期（天）"><el-input-number v-model="form.horizonDays" :min="7" :max="730" placeholder="默认取参数" controls-position="right" /></el-form-item>
        <el-form-item label="包含预测"><el-switch v-model="form.includeForecast" /></el-form-item>
        <el-form-item label="包含安全库存"><el-switch v-model="form.includeSafety" /></el-form-item>
        <el-form-item label="使用替代料">
          <el-radio-group v-model="form.useSubstitute">
            <el-radio-button value="">按参数</el-radio-button>
            <el-radio-button value="Y">使用</el-radio-button>
            <el-radio-button value="N">不使用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <p class="hint">净变更当前按全量计算；运算期间业务单据可正常操作，结果以开始时读取的数据为准。</p>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="starting" @click="start">开始运算</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.hint { color: var(--erp-color-text-secondary); }
</style>
