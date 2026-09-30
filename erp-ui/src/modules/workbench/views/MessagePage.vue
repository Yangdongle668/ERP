<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDateTime } from '@/utils/format'
import { MSG_TYPES, messageApi, type Message, type UnreadCount } from '../api/workbench'

defineOptions({ name: 'WbMessagePage' })

/** 消息中心（需求 02-03 3.1）：左侧类型筛选（显示未读数），右侧列表；点击展开详情并标记已读 */
const router = useRouter()
const type = ref<string>('')
const onlyUnread = ref(false)
const list = ref<Message[]>([])
const total = ref(0)
const pageNo = ref(1)
const loading = ref(false)
const unread = ref<UnreadCount>({ total: 0, byType: {} })
const expanded = ref<string>()

async function load() {
  loading.value = true
  try {
    const r = await messageApi.page({ pageNo: pageNo.value, pageSize: 20, type: type.value || undefined, read: onlyUnread.value ? false : undefined })
    list.value = r.list
    total.value = r.total
    unread.value = await messageApi.unread()
  } finally {
    loading.value = false
  }
}
onMounted(load)
function pick(t: string) {
  type.value = t
  pageNo.value = 1
  load()
}
async function toggle(m: Message) {
  expanded.value = expanded.value === m.id ? undefined : m.id
  if (!m.read) {
    await messageApi.read(m.id)
    m.read = true
    unread.value = await messageApi.unread()
  }
}
async function readAll() {
  const r = await messageApi.readAll(type.value || undefined)
  ElMessage.success(`已标记 ${r.count} 条为已读`)
  load()
}
async function deleteRead() {
  await ElMessageBox.confirm('删除全部已读消息？', '删除已读', { type: 'warning' })
  const r = await messageApi.deleteRead()
  ElMessage.success(`已删除 ${r.count} 条`)
  load()
}
</script>

<template>
  <ErpPage description="审批结果、任务、提醒、系统通知；消息保留天数见系统参数 wb.message.retention-days">
    <div class="layout">
      <ErpPanel class="types" flush>
        <div :class="['type', { active: type === '' }]" @click="pick('')">全部<el-badge v-if="unread.total" :value="unread.total" :max="99" /></div>
        <div v-for="t in MSG_TYPES" :key="String(t.value)" :class="['type', { active: type === t.value }]" @click="pick(String(t.value))">
          {{ t.label }}<el-badge v-if="unread.byType[String(t.value)]" :value="unread.byType[String(t.value)]" :max="99" />
        </div>
      </ErpPanel>
      <ErpPanel>
        <div class="bar">
          <el-checkbox v-model="onlyUnread" @change="load">只看未读</el-checkbox>
          <span class="grow" />
          <el-button @click="readAll">全部已读</el-button>
          <el-button @click="deleteRead">删除已读</el-button>
        </div>
        <div v-loading="loading">
          <ErpEmpty v-if="!list.length" description="暂无消息" />
          <div v-for="m in list" :key="m.id" class="msg" @click="toggle(m)">
            <div class="head">
              <span :class="['dot', { on: !m.read }]" />
              <span :class="['title', { unread: !m.read }]">{{ m.title }}</span>
              <span class="time">{{ formatDateTime(m.createdAt, true) }}</span>
            </div>
            <div v-if="expanded === m.id" class="content">
              <div>{{ m.content || '（无内容）' }}</div>
              <el-button v-if="m.route" link type="primary" @click.stop="router.push(m.route)">查看</el-button>
            </div>
            <div v-else-if="m.content" class="summary">{{ m.content }}</div>
          </div>
        </div>
        <el-pagination v-if="total > 20" v-model:current-page="pageNo" :total="total" :page-size="20" layout="prev, pager, next" @current-change="load" />
      </ErpPanel>
    </div>
  </ErpPage>
</template>

<style scoped>
.layout { display: grid; grid-template-columns: 200px 1fr; gap: var(--erp-section-gap); align-items: start; }
.type { display: flex; justify-content: space-between; align-items: center; padding: var(--erp-space-2) var(--erp-space-4); cursor: pointer; }
.type:hover { background: var(--erp-color-hover); }
.type.active { background: var(--erp-color-primary-bg); color: var(--erp-color-primary); }
.bar { display: flex; align-items: center; gap: var(--erp-space-2); margin-bottom: var(--erp-space-2); }
.grow { flex: 1; }
.msg { padding: var(--erp-space-3) 0; border-bottom: 1px solid var(--erp-color-border-light); cursor: pointer; }
.head { display: flex; align-items: center; gap: var(--erp-space-2); }
.dot { width: 8px; height: 8px; border-radius: 50%; }
.dot.on { background: var(--erp-color-primary); }
.title { flex: 1; }
.unread { font-weight: var(--erp-font-weight-medium); }
.time { color: var(--erp-color-text-tertiary); font-size: var(--erp-font-size-caption); }
.summary { color: var(--erp-color-text-secondary); margin-left: var(--erp-space-4); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.content { margin: var(--erp-space-2) 0 0 var(--erp-space-4); color: var(--erp-color-text-secondary); white-space: pre-wrap; }
</style>
