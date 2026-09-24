<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { SearchField, StatusMap, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { modules } from '@/modules/registry'
import { formatDateTime, today } from '@/utils/format'
import { logApi, logQueryParams, type LogQuery, type LoginLogRow, type OperLogRow } from '../api/log'

defineOptions({ name: 'SystemLogPage' })

/** 日志审计（01-11，T1 两个页签）：登录日志、操作日志；时间默认今天 */
const tab = ref<'login' | 'oper'>('login')
const todayRange = (): [string, string] => [`${today()} 00:00:00`, `${today()} 23:59:59`]

// ---------- 登录日志 ----------
const LOGIN_RESULTS = [
  { value: 'SUCCESS', label: '成功' },
  { value: 'BAD_CREDENTIALS', label: '密码错误' },
  { value: 'CAPTCHA_ERROR', label: '验证码错误' },
  { value: 'LOCKED', label: '已锁定' },
  { value: 'DISABLED', label: '已停用' },
  { value: 'EXPIRED', label: '密码过期' }
]
const LOGIN_RESULT_STATUS: StatusMap = Object.fromEntries(
  LOGIN_RESULTS.map((r) => [r.value, { label: r.label, type: r.value === 'SUCCESS' ? 'success' : 'danger' }])
)
const LOGIN_TYPES = [{ value: 'LOGIN', label: '登录' }, { value: 'LOGOUT', label: '退出' }]

const login = useListPage<Omit<LogQuery, 'pageNo' | 'pageSize'>, LoginLogRow>({
  api: (q) => logApi.loginLogs(q as LogQuery),
  defaultQuery: () => ({ time: todayRange() })
})

const loginFields: SearchField[] = [
  { prop: 'username', label: '用户名' },
  { prop: 'result', label: '结果', type: 'select', options: LOGIN_RESULTS },
  { prop: 'ip', label: 'IP', placeholder: '前缀匹配' },
  { prop: 'time', label: '时间', type: 'datetimerange' }
]

const loginColumns: TableColumn<LoginLogRow>[] = [
  { prop: 'createdAt', label: '时间', width: 160, formatter: (r) => formatDateTime(r.createdAt) },
  { prop: 'username', label: '用户名', width: 120 },
  { prop: 'realName', label: '姓名', width: 100 },
  { prop: 'type', label: '类型', width: 80, type: 'enum', options: LOGIN_TYPES },
  { prop: 'result', label: '结果', width: 100, type: 'status', statusMap: LOGIN_RESULT_STATUS },
  { prop: 'ip', label: 'IP', width: 130 },
  { prop: 'browser', label: '浏览器', width: 120 },
  { prop: 'os', label: '操作系统', minWidth: 120 }
]

// ---------- 操作日志 ----------
const OPER_RESULTS = [
  { value: 'SUCCESS', label: '成功' },
  { value: 'BIZ_ERROR', label: '业务失败' },
  { value: 'SYSTEM_ERROR', label: '系统异常' }
]
const OPER_RESULT_STATUS: StatusMap = {
  SUCCESS: { label: '成功', type: 'success' },
  BIZ_ERROR: { label: '业务失败', type: 'warning' },
  SYSTEM_ERROR: { label: '系统异常', type: 'danger' }
}
const moduleOptions = computed(() => modules.map((m) => ({ value: m.code, label: m.title })))

const oper = useListPage<Omit<LogQuery, 'pageNo' | 'pageSize'>, OperLogRow>({
  api: (q) => logApi.operLogs(q as LogQuery),
  defaultQuery: () => ({ time: todayRange() }),
  immediate: false
})

const operFields = computed<SearchField[]>(() => [
  { prop: 'userId', label: '操作人', type: 'user' },
  { prop: 'moduleCode', label: '模块', type: 'select', options: moduleOptions.value },
  { prop: 'action', label: '操作' },
  { prop: 'result', label: '结果', type: 'select', options: OPER_RESULTS },
  { prop: 'traceId', label: '追踪号' },
  { prop: 'time', label: '时间', type: 'datetimerange' }
])

const operColumns: TableColumn<OperLogRow>[] = [
  { prop: 'createdAt', label: '时间', width: 160, formatter: (r) => formatDateTime(r.createdAt) },
  { prop: 'realName', label: '操作人', width: 100, formatter: (r) => r.realName || r.username || '-' },
  { prop: 'moduleName', label: '模块', width: 90 },
  { prop: 'action', label: '操作', width: 160 },
  { prop: 'result', label: '结果', width: 90, type: 'status', statusMap: OPER_RESULT_STATUS },
  { prop: 'errorMsg', label: '错误信息', minWidth: 180 },
  { prop: 'durationMs', label: '耗时(ms)', width: 90, align: 'right', slot: true },
  { prop: 'ip', label: 'IP', width: 130 },
  { prop: 'traceId', label: '追踪号', width: 130, slot: true }
]

let operLoaded = false
function onTab(name: string | number) {
  if (name === 'oper' && !operLoaded) {
    operLoaded = true
    oper.load()
  }
}

// ---------- 详情 ----------
const detailVisible = ref(false)
const detail = ref<OperLogRow>()
async function openDetail(r: OperLogRow) {
  detail.value = r
  detailVisible.value = true
  detail.value = await logApi.operLog(r.id)
}

const prettyParams = computed(() => {
  const p = detail.value?.params
  if (!p) return ''
  try {
    return JSON.stringify(JSON.parse(p), null, 2)
  } catch {
    return p
  }
})

function copy(text?: string) {
  if (!text) return
  navigator.clipboard?.writeText(text).then(() => ElMessage.success('已复制'), () => ElMessage.warning('复制失败，请手动选择复制'))
}

const asOper = (r: unknown) => r as OperLogRow
</script>

<template>
  <ErpPage description="登录与操作审计；时间默认今天，超过保留天数的日志每天凌晨自动清理">
  <ErpPanel class="log-panel">
    <el-tabs v-model="tab" @tab-change="onTab">
      <el-tab-pane label="登录日志" name="login">
        <ErpSearchForm v-model="login.query" :fields="loginFields" :loading="login.loading.value" @search="login.search" @reset="login.reset" />
        <ErpTable :columns="loginColumns" :data="login.list.value" :loading="login.loading.value" storage-key="system.login-log" @refresh="login.load">
          <template #toolbar-right>
            <ExportButton url="/system/login-logs/export" :params="() => logQueryParams({ ...(login.query as LogQuery) })" filename="登录日志" permission="system:log:export" />
          </template>
        </ErpTable>
        <ErpPagination v-model:page-no="login.query.pageNo" v-model:page-size="login.query.pageSize" :total="login.total.value" @change="login.load" />
      </el-tab-pane>

      <el-tab-pane label="操作日志" name="oper">
        <ErpSearchForm v-model="oper.query" :fields="operFields" :loading="oper.loading.value" @search="oper.search" @reset="oper.reset" />
        <ErpTable :columns="operColumns" :data="oper.list.value" :loading="oper.loading.value" storage-key="system.oper-log" :actions-width="80" @refresh="oper.load">
          <template #toolbar-right>
            <ExportButton url="/system/oper-logs/export" :params="() => logQueryParams({ ...(oper.query as LogQuery) })" filename="操作日志" permission="system:log:export" />
          </template>
          <template #col-durationMs="{ row }">
            <span :class="{ slow: asOper(row).durationMs > 3000 }">{{ asOper(row).durationMs }}</span>
          </template>
          <template #col-traceId="{ row }">
            <el-link v-if="asOper(row).traceId" type="primary" underline="never" class="mono" @click="copy(asOper(row).traceId)">{{ asOper(row).traceId }}</el-link>
          </template>
          <template #actions="{ row }">
            <el-button link type="primary" @click="openDetail(asOper(row))">详情</el-button>
          </template>
        </ErpTable>
        <ErpPagination v-model:page-no="oper.query.pageNo" v-model:page-size="oper.query.pageSize" :total="oper.total.value" @change="oper.load" />
      </el-tab-pane>
    </el-tabs>
  </ErpPanel>

  <el-drawer v-model="detailVisible" title="操作日志详情" size="640px" append-to-body>
    <template v-if="detail">
      <el-descriptions :column="2" border class="detail">
        <el-descriptions-item label="时间">{{ formatDateTime(detail.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.realName || '-' }}（{{ detail.username || '-' }}）</el-descriptions-item>
        <el-descriptions-item label="模块">{{ detail.moduleName || detail.moduleCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作">{{ detail.action }}</el-descriptions-item>
        <el-descriptions-item label="结果"><StatusTag :value="detail.result" :map="OPER_RESULT_STATUS" /></el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detail.durationMs }} ms</el-descriptions-item>
        <el-descriptions-item label="请求" :span="2">{{ detail.method }} {{ detail.path }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ detail.ip || '-' }}</el-descriptions-item>
        <el-descriptions-item label="追踪号">
          <el-link v-if="detail.traceId" type="primary" underline="never" @click="copy(detail.traceId)">{{ detail.traceId }}</el-link>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.result !== 'SUCCESS'" label="错误" :span="2">
          <span class="error">{{ detail.errorCode ? `[${detail.errorCode}] ` : '' }}{{ detail.errorMsg || '-' }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <div class="group-title params-title">请求参数</div>
      <pre v-if="prettyParams" class="params">{{ prettyParams }}</pre>
      <el-empty v-else description="无参数" :image-size="60" />
    </template>
  </el-drawer>
  </ErpPage>
</template>

<style scoped>
.log-panel :deep(.el-tabs__header) { margin: -8px 0 16px; }
.slow { color: var(--el-color-warning); font-weight: var(--erp-font-weight-medium); }
.error { color: var(--el-color-danger); }
.params-title { margin-top: 16px; }
.params { font-family: var(--erp-font-family-mono); background: var(--erp-color-surface-subtle); border: 1px solid var(--erp-color-border); padding: 12px; border-radius: var(--erp-radius-xs); font-size: var(--erp-font-size-caption); max-height: 60vh; overflow: auto; white-space: pre-wrap; word-break: break-all; }
</style>
