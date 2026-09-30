<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import { AI_PROVIDER, aiApi, type AiLog, type AiMessage, type AiSettings, type AiStatus, type Anomaly, ANOMALY_LEVEL, ANOMALY_METHOD, type Conversation, metricApi,
  type UsageRow, type WeeklyReport } from '../api/bi'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'
import AiResultView from '../components/AiResultView.vue'
import { fmtMetric } from '../components/biFormat'

/**
 * AI 分析（需求 13-04）：对话（AI 问数）/ 异常 / 周报；管理员可查看设置与用量，ai:log:view 可查看问答日志。
 * AI 只读、只建议：所有数字来自 BI 查询服务，数据表在本系统内渲染真实数值。
 */
const route = useRoute()
const user = useUserStore()
const tab = ref(typeof route.query.tab === 'string' ? route.query.tab : 'chat')
const status = ref<AiStatus>()
const topics = ref<Record<string, string>>({})
const metricNames = ref<Record<string, string>>({})

// ==================== 对话 ====================
const conversations = ref<Conversation[]>([])
const current = ref<string>()
const messages = ref<AiMessage[]>([])
const question = ref('')
const asking = ref(false)
const pending = ref<string>()
const scroller = ref<HTMLElement>()
const EXAMPLES = ['上个月哪 5 个客户的出货额下降最多？', '本季度来料合格率最低的供应商是哪家？', '近 6 个月接单额和出货额的趋势如何？', '本月各业务员的回款额排名']

async function loadConversations() {
  conversations.value = await aiApi.conversations()
}
async function openConversation(id: string) {
  current.value = id
  messages.value = await aiApi.messages(id)
  scrollDown()
}
async function newConversation() {
  const c = await aiApi.create()
  await loadConversations()
  await openConversation(c.id)
}
async function rename(c: Conversation) {
  const { value } = await ElMessageBox.prompt('会话名称', '重命名', { inputValue: c.title, inputPattern: /\S/, inputErrorMessage: '请输入名称' })
  await aiApi.rename(c.id, value)
  await loadConversations()
}
async function remove(c: Conversation) {
  await ElMessageBox.confirm(`删除会话“${c.title}”？`, '删除', { type: 'warning' })
  await aiApi.remove(c.id)
  if (current.value === c.id) {
    current.value = undefined
    messages.value = []
  }
  await loadConversations()
}
function scrollDown() {
  nextTick(() => scroller.value?.scrollTo({ top: scroller.value.scrollHeight }))
}
async function send(text?: string) {
  const q = (text ?? question.value).trim()
  if (!q || asking.value) return
  if (!current.value) {
    const c = await aiApi.create()
    current.value = c.id
  }
  asking.value = true
  pending.value = q
  question.value = ''
  scrollDown()
  try {
    await aiApi.ask(current.value, q)
  } catch (e) {
    question.value = q
    throw e
  } finally {
    asking.value = false
    pending.value = undefined
    messages.value = await aiApi.messages(current.value)
    await loadConversations()
    status.value = await aiApi.status()
    scrollDown()
  }
}
function onKey(evt: Event | KeyboardEvent) {
  const e = evt as KeyboardEvent
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    send()
  }
}
async function feedback(m: AiMessage, fb: 'UP' | 'DOWN') {
  let remark: string | undefined
  if (fb === 'DOWN') {
    const r = await ElMessageBox.prompt('哪里不对？（可选）', '反馈', { inputPlaceholder: '例如：数字与报表不一致' }).catch(() => null)
    if (!r) return
    remark = r.value
  }
  const next = m.feedback === fb ? null : fb
  await aiApi.feedback(m.id, next, remark)
  m.feedback = next ?? undefined
  ElMessage.success('感谢反馈')
}

// ==================== 异常 / 周报 / 用量 / 日志 ====================
const anomalies = ref<Anomaly[]>([])
const reports = ref<WeeklyReport[]>([])
const settings = ref<AiSettings>()
const usage = ref<UsageRow[]>([])
const logs = ref<AiLog[]>([])
const logTotal = ref(0)
const logQuery = ref({ pageNo: 1, pageSize: 20 })
const isAdmin = computed(() => user.hasPermission('ai:setting:manage'))
const canLog = computed(() => user.hasPermission('ai:log:view'))
const canWeekly = computed(() => user.hasPermission('bi:dashboard:view'))

async function loadTab(name: string) {
  if (name === 'anomaly') anomalies.value = await aiApi.anomalies()
  if (name === 'weekly' && canWeekly.value) reports.value = await aiApi.weeklyReports()
  if (name === 'usage' && isAdmin.value) [settings.value, usage.value] = await Promise.all([aiApi.settings(), aiApi.usage()])
  if (name === 'logs' && canLog.value) {
    const p = await aiApi.logs(logQuery.value)
    logs.value = p.list
    logTotal.value = p.total
  }
}
async function detect() {
  const n = await aiApi.detect()
  ElMessage.success(`检测完成：${n} 项异常`)
  await loadTab('anomaly')
}
async function generateWeekly() {
  await aiApi.generateWeekly()
  ElMessage.success('已生成上周周报')
  await loadTab('weekly')
}

onMounted(async () => {
  const [s, visible] = await Promise.all([aiApi.status(), metricApi.visible()])
  status.value = s
  topics.value = Object.fromEntries(visible.map((m) => [m.code, m.topic]))
  metricNames.value = Object.fromEntries(visible.map((m) => [m.code, m.name]))
  await loadConversations()
  if (conversations.value.length) await openConversation(conversations.value[0].id)
  if (tab.value !== 'chat') await loadTab(tab.value)
})
</script>

<template>
  <ErpPage description="用自然语言提问经营数据；AI 只能通过指标库查询有权限的数据，只提供分析和建议">
    <el-alert v-if="status && !status.configured" :title="status.message ?? 'AI 分析未启用，请联系管理员配置'" type="warning" :closable="false" show-icon />
    <el-tabs v-model="tab" @tab-change="(n: string | number) => loadTab(String(n))">
      <el-tab-pane label="对话" name="chat">
        <div class="ai-chat">
          <ErpPanel flush class="ai-chat__side">
            <div class="ai-chat__new"><el-button icon="Plus" class="ai-chat__newbtn" @click="newConversation">新建对话</el-button></div>
            <ul class="ai-convs">
              <li v-for="c in conversations" :key="c.id" :class="{ active: c.id === current }" @click="openConversation(c.id)">
                <span class="ai-convs__title" :title="c.title">{{ c.title }}</span>
                <span class="ai-convs__ops" @click.stop>
                  <ErpIconButton icon="Edit" tooltip="重命名" @click="rename(c)" />
                  <ErpIconButton icon="Delete" tooltip="删除" @click="remove(c)" />
                </span>
              </li>
            </ul>
            <ErpEmpty v-if="!conversations.length" description="还没有对话" />
          </ErpPanel>
          <ErpPanel class="ai-chat__main">
            <div ref="scroller" class="ai-msgs">
              <div v-if="!messages.length && !pending" class="ai-examples">
                <div class="ai-examples__title">可以这样问</div>
                <el-button v-for="e in EXAMPLES" :key="e" :disabled="!status?.configured" @click="send(e)">{{ e }}</el-button>
              </div>
              <div v-for="m in messages" :key="m.id" :class="['ai-msg', m.role === 'USER' ? 'is-user' : 'is-ai']">
                <div class="ai-msg__bubble">
                  <div class="ai-msg__text">{{ m.content }}</div>
                  <template v-if="m.role === 'ASSISTANT'">
                    <AiResultView v-for="(r, i) in m.results" :key="i" :result="r" :query="m.toolCalls.filter((t) => !t.error)[i]?.input" :chart="m.chart"
                                  :topics="topics" />
                    <div class="ai-msg__foot">
                      <span>{{ formatDateTime(m.createdAt, true) }}<template v-if="m.tokens"> · {{ m.tokens }} tokens</template></span>
                      <el-button link :type="m.feedback === 'UP' ? 'primary' : 'info'" @click="feedback(m, 'UP')">有用</el-button>
                      <el-button link :type="m.feedback === 'DOWN' ? 'danger' : 'info'" @click="feedback(m, 'DOWN')">没用</el-button>
                    </div>
                  </template>
                </div>
              </div>
              <div v-if="pending" class="ai-msg is-user"><div class="ai-msg__bubble"><div class="ai-msg__text">{{ pending }}</div></div></div>
              <div v-if="asking" class="ai-msg is-ai"><div class="ai-msg__bubble ai-msg__thinking">正在查询与分析…</div></div>
            </div>
            <div class="ai-input">
              <el-input v-model="question" type="textarea" :autosize="{ minRows: 2, maxRows: 6 }" maxlength="2000" resize="none"
                        :placeholder="status?.configured ? '输入问题，Enter 发送，Shift+Enter 换行' : '管理员启用 AI 分析后可以提问'"
                        :disabled="!status?.configured" @keydown="onKey" />
              <div class="ai-input__bar">
                <span class="ai-input__quota" v-if="status">今日已提问 {{ status.usedToday }} / {{ status.quota }}</span>
                <el-button type="primary" icon="Send" :loading="asking" :disabled="!status?.configured || !question.trim()" @click="send()">发送</el-button>
              </div>
            </div>
          </ErpPanel>
        </div>
      </el-tab-pane>

      <el-tab-pane label="异常" name="anomaly">
        <ErpPanel title="经营数据异常" description="每天 08:00 检测前一日：同星期 3σ 偏离、近 30 天环比变化超过 30%">
          <template #extra><el-button v-if="isAdmin" icon="Refresh" @click="detect">立即检测</el-button></template>
          <el-table :data="anomalies" row-key="id">
            <el-table-column label="检测日" width="110"><template #default="{ row }">{{ row.detectDate }}</template></el-table-column>
            <el-table-column label="级别" width="80"><template #default="{ row }"><StatusTag :value="row.level" :map="ANOMALY_LEVEL" /></template></el-table-column>
            <el-table-column label="指标" width="120"><template #default="{ row }">{{ metricNames[row.metricCode] ?? row.metricCode }}</template></el-table-column>
            <el-table-column label="对象" min-width="160" show-overflow-tooltip><template #default="{ row }">{{ row.dimLabel ?? '全公司' }}</template></el-table-column>
            <el-table-column label="方法" width="110"><template #default="{ row }">{{ ANOMALY_METHOD[row.method] }}</template></el-table-column>
            <el-table-column label="本期 / 基准" width="220" align="right">
              <template #default="{ row }">{{ fmtMetric(row.currentValue, 'AMOUNT') }} / {{ fmtMetric(row.baseValue, 'AMOUNT') }}</template>
            </el-table-column>
            <el-table-column label="变化" width="100" align="right"><template #default="{ row }">{{ Number(row.changePct) > 0 ? '+' : '' }}{{ row.changePct }}%</template></el-table-column>
            <el-table-column prop="explanation" label="解读" min-width="320" />
          </el-table>
        </ErpPanel>
      </el-tab-pane>

      <el-tab-pane v-if="canWeekly" label="周报" name="weekly">
        <ErpPanel title="经营周报" description="每周一 07:30 生成上周全公司口径的经营周报（数据范围为全部的用户可见）">
          <template #extra><el-button v-if="isAdmin" icon="Refresh" @click="generateWeekly">生成上周周报</el-button></template>
          <ErpEmpty v-if="!reports.length" description="暂无周报" />
          <el-collapse v-else>
            <el-collapse-item v-for="r in reports" :key="r.id" :title="r.title" :name="r.id">
              <div class="ai-report">{{ r.summary }}</div>
            </el-collapse-item>
          </el-collapse>
        </ErpPanel>
      </el-tab-pane>

      <el-tab-pane v-if="isAdmin" label="设置与用量" name="usage">
        <ErpPanel title="AI 设置" description="在“系统参数”中修改（BI/AI 模块，AI 分组）">
          <el-descriptions v-if="settings" :column="3" border>
            <el-descriptions-item label="启用">{{ settings.enabled ? '是' : '否' }}</el-descriptions-item>
            <el-descriptions-item label="供应商">{{ AI_PROVIDER[settings.provider] ?? settings.provider }}</el-descriptions-item>
            <el-descriptions-item label="模型">{{ settings.model ?? '未配置' }}</el-descriptions-item>
            <el-descriptions-item label="接口地址" :span="3">{{ settings.baseUrl ?? '未配置' }}</el-descriptions-item>
            <el-descriptions-item label="API Key">{{ settings.maskedKey ?? '未配置' }}（{{ settings.keySource === 'ENV' ? '环境变量' : settings.keySource === 'PARAM' ? '系统参数' : '-' }}）</el-descriptions-item>
            <el-descriptions-item label="敏感字段脱敏">{{ settings.mask ? '是' : '否' }}</el-descriptions-item>
            <el-descriptions-item label="每用户每日上限">{{ settings.quota }}</el-descriptions-item>
          </el-descriptions>
        </ErpPanel>
        <ErpPanel title="用量（近 30 天）" class="ai-gap">
          <el-table :data="usage">
            <el-table-column prop="date" label="日期" width="120" />
            <el-table-column prop="userName" label="用户" min-width="140" />
            <el-table-column prop="questions" label="提问数" width="100" align="right" />
            <el-table-column prop="tokens" label="Token 数" width="120" align="right" />
            <el-table-column prop="failures" label="失败" width="80" align="right" />
          </el-table>
        </ErpPanel>
      </el-tab-pane>

      <el-tab-pane v-if="canLog" label="问答日志" name="logs">
        <ErpPanel title="问答日志" description="保留 180 天">
          <el-table :data="logs" row-key="id">
            <el-table-column label="时间" width="160"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
            <el-table-column prop="userName" label="用户" width="120" />
            <el-table-column prop="question" label="问题" min-width="240" show-overflow-tooltip />
            <el-table-column prop="toolCalls" label="工具调用" min-width="240" show-overflow-tooltip />
            <el-table-column prop="resultRows" label="结果行" width="80" align="right" />
            <el-table-column label="耗时" width="90" align="right"><template #default="{ row }">{{ (row.latencyMs / 1000).toFixed(1) }}s</template></el-table-column>
            <el-table-column prop="tokens" label="Token" width="90" align="right" />
            <el-table-column label="结果" width="80"><template #default="{ row }">{{ row.success ? '成功' : '失败' }}</template></el-table-column>
            <el-table-column label="反馈" width="80"><template #default="{ row }">{{ row.feedback === 'UP' ? '有用' : row.feedback === 'DOWN' ? '没用' : '' }}</template></el-table-column>
            <el-table-column prop="error" label="错误" min-width="160" show-overflow-tooltip />
          </el-table>
          <ErpPagination v-model:page-no="logQuery.pageNo" v-model:page-size="logQuery.pageSize" :total="logTotal" @change="loadTab('logs')" />
        </ErpPanel>
      </el-tab-pane>
    </el-tabs>
  </ErpPage>
</template>

<style scoped>
.ai-chat { display: grid; grid-template-columns: 240px minmax(0, 1fr); gap: var(--erp-space-4); min-height: 560px; }
.ai-chat__new { padding: var(--erp-space-3); border-bottom: 1px solid var(--erp-color-border-light); }
.ai-chat__newbtn { width: 100%; }
.ai-convs { list-style: none; margin: 0; padding: var(--erp-space-2); display: flex; flex-direction: column; gap: 2px; }
.ai-convs li { display: flex; align-items: center; gap: var(--erp-space-1); padding: var(--erp-space-2); border-radius: var(--erp-radius-control); cursor: pointer; }
.ai-convs li:hover { background: var(--erp-color-hover); }
.ai-convs li.active { background: var(--erp-color-primary-bg); color: var(--erp-color-primary); }
.ai-convs__title { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: var(--erp-font-size-secondary); }
.ai-convs__ops { display: none; }
.ai-convs li:hover .ai-convs__ops { display: inline-flex; }
.ai-chat__main :deep(.erp-panel__body) { display: flex; flex-direction: column; height: 100%; }
.ai-msgs { flex: 1; overflow-y: auto; max-height: 620px; display: flex; flex-direction: column; gap: var(--erp-space-4); padding-bottom: var(--erp-space-3); }
.ai-msg { display: flex; }
.ai-msg.is-user { justify-content: flex-end; }
.ai-msg__bubble { max-width: 88%; padding: var(--erp-space-3) var(--erp-space-4); border-radius: var(--erp-radius-card); background: var(--erp-color-surface-subtle);
  border: 1px solid var(--erp-color-border-light); min-width: 0; }
.ai-msg.is-user .ai-msg__bubble { background: var(--erp-color-primary-bg); border-color: var(--erp-color-primary-bg); }
.ai-msg.is-ai .ai-msg__bubble { width: 88%; }
.ai-msg__text { white-space: pre-wrap; line-height: var(--erp-line-height); }
.ai-msg__thinking { color: var(--erp-color-text-tertiary); }
.ai-msg__foot { margin-top: var(--erp-space-2); display: flex; align-items: center; gap: var(--erp-space-3); color: var(--erp-color-text-tertiary); font-size: var(--erp-font-size-caption); }
.ai-examples { display: flex; flex-direction: column; align-items: flex-start; gap: var(--erp-space-2); padding: var(--erp-space-4) 0; }
.ai-examples :deep(.el-button + .el-button) { margin-left: 0; }
.ai-examples__title { color: var(--erp-color-text-secondary); }
.ai-input { border-top: 1px solid var(--erp-color-border-light); padding-top: var(--erp-space-3); }
.ai-input__bar { margin-top: var(--erp-space-2); display: flex; justify-content: flex-end; align-items: center; gap: var(--erp-space-3); }
.ai-input__quota { color: var(--erp-color-text-tertiary); font-size: var(--erp-font-size-caption); }
.ai-report { white-space: pre-wrap; line-height: var(--erp-line-height); }
.ai-gap { margin-top: var(--erp-section-gap); }
</style>
