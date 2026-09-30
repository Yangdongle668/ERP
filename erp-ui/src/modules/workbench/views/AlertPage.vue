<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { ALERT_LEVEL, ALERT_STATUS, ALERT_TYPES, alertApi, labelOf, type Alert, type AlertStats } from '../api/workbench'

defineOptions({ name: 'WbAlertPage' })

/**
 * 预警中心（需求 02-04）：只显示接收人包含自己的预警；顶部未处理数量按级别统计，点击筛选；
 * 处理需填写说明，忽略需填写原因（7 天内同一条件同级别不再提醒，级别升高时重新打开）；条件消除后自动变为已消除。
 */
const router = useRouter()
type Query = { level?: string; alertType?: string; statuses?: string[] }
const { query, list, total, loading, load, search, reset } = useListPage<Query, Alert>({
  api: (q) => alertApi.page({ ...q, statuses: q.statuses?.length ? q.statuses.join(',') : undefined } as never),
  defaultQuery: () => ({ statuses: ['OPEN'] }),
  refreshOnActivated: true
})
const stats = ref<AlertStats>({ critical: 0, warning: 0, info: 0 })
async function loadStats() {
  stats.value = await alertApi.stats()
}
onMounted(loadStats)
const fields: SearchField[] = [
  { prop: 'level', label: '级别', type: 'select', options: Object.entries(ALERT_LEVEL).map(([value, s]) => ({ value, label: s.label })) },
  { prop: 'alertType', label: '类型', type: 'select', options: ALERT_TYPES },
  { prop: 'statuses', label: '状态', type: 'select', multiple: true, options: Object.entries(ALERT_STATUS).map(([value, s]) => ({ value, label: s.label })) }
]
const columns: TableColumn<Alert>[] = [
  { prop: 'level', label: '级别', width: 70, type: 'status', statusMap: ALERT_LEVEL },
  { prop: 'alertType', label: '类型', width: 120, formatter: (r) => labelOf(ALERT_TYPES, r.alertType) },
  { prop: 'title', label: '标题', minWidth: 220 },
  { prop: 'content', label: '内容', minWidth: 220 },
  { prop: 'firstRaisedAt', label: '首次预警', width: 150, type: 'datetime' },
  { prop: 'lastRaisedAt', label: '最近更新', width: 150, type: 'datetime' },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: ALERT_STATUS },
  { prop: 'handleRemark', label: '处理说明', width: 160 }
]
function filterLevel(level: string) {
  query.level = level
  query.statuses = ['OPEN']
  search()
}
async function act(a: Alert, kind: 'handle' | 'ignore') {
  const title = kind === 'handle' ? '处理预警' : '忽略预警'
  const label = kind === 'handle' ? '处理说明' : '忽略原因'
  const { value } = await ElMessageBox.prompt(kind === 'ignore' ? '忽略后 7 天内同一条件同级别不再提醒' : a.title, title, {
    inputPlaceholder: `请填写${label}`, inputValidator: (v) => (!!v && !!v.trim()) || `请填写${label}`
  })
  if (kind === 'handle') await alertApi.handle(a.id, value)
  else await alertApi.ignore(a.id, value)
  ElMessage.success(kind === 'handle' ? '已处理' : '已忽略')
  load()
  loadStats()
}
</script>

<template>
  <ErpPage description="各模块发出的预警（库存、临期、待检超时、交期延期、证书到期、应收逾期等）；条件消除后自动关闭">
    <div class="stats">
      <div class="stat critical" @click="filterLevel('CRITICAL')"><b>{{ stats.critical }}</b><span>严重</span></div>
      <div class="stat warning" @click="filterLevel('WARNING')"><b>{{ stats.warning }}</b><span>警告</span></div>
      <div class="stat info" @click="filterLevel('INFO')"><b>{{ stats.info }}</b><span>提示</span></div>
    </div>
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset" />
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="wb.alert" @refresh="load">
        <template #actions="{ row }">
          <el-button v-if="row.route" link type="primary" @click="router.push(row.route)">查看</el-button>
          <template v-if="row.status === 'OPEN'">
            <el-button link type="primary" @click="act(row as Alert, 'handle')">处理</el-button>
            <el-button link @click="act(row as Alert, 'ignore')">忽略</el-button>
          </template>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--erp-section-gap); }
.stat { display: flex; align-items: baseline; gap: var(--erp-space-2); padding: var(--erp-space-4); background: var(--erp-color-surface);
  border: 1px solid var(--erp-color-border); border-radius: var(--erp-radius-card); cursor: pointer; }
.stat b { font-size: var(--erp-font-size-metric); font-weight: var(--erp-font-weight-semibold); }
.stat span { color: var(--erp-color-text-secondary); }
.critical b { color: var(--erp-color-error); }
.warning b { color: var(--erp-color-warning); }
.info b { color: var(--erp-color-text-secondary); }
</style>
