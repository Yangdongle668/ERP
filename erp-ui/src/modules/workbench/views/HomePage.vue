<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { modules } from '@/modules/registry'
import MetricCard from '../components/MetricCard.vue'
import {
  ALERT_LEVEL, alertApi, homeApi, noticeApi, TODO_CATEGORY, todoApi, type ActiveNotice, type Alert, type CardVO, type Summary, type Todo
} from '../api/workbench'

defineOptions({ name: 'WbHomePage' })

/**
 * 工作台首页（需求 02-01）：欢迎区计数、我的待办、看板卡片（按权限、可编辑布局）、快捷入口与最近访问、公告、预警；
 * 登录后存在未读重要公告时弹窗（WB-NTC-R02）。
 */
const router = useRouter()
const me = useUserStore()
const summary = ref<Summary>()
const todos = ref<Todo[]>([])
const cards = ref<CardVO[]>([])
const notices = ref<ActiveNotice[]>([])
const alerts = ref<Alert[]>([])
const shortcuts = ref<string[]>([])

const now = new Date()
const greeting = computed(() => {
  const h = now.getHours()
  return h < 11 ? '早上好' : h < 13 ? '中午好' : h < 18 ? '下午好' : '晚上好'
})
const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')} 星期${'日一二三四五六'[now.getDay()]}`

/** 当前用户可访问的菜单（快捷入口候选） */
const menus = computed(() => modules.flatMap((m) => m.menus.filter((x) => !x.hidden && !x.path.includes(':') && me.hasPermission(x.permission))
  .map((x) => ({ route: `/${m.code}/${x.path}`, title: x.title, module: m.title }))))
const DEFAULT_SHORTCUTS = ['/sales/order', '/purchase/order', '/inventory/stock', '/shipping/notice', '/engineering/material', '/pmc/mrp/suggestions',
  '/production/prod-order', '/quality/iqc', '/finance/receivable', '/finance/receipt']
const shortcutItems = computed(() => {
  const routes = shortcuts.value.length ? shortcuts.value : DEFAULT_SHORTCUTS.filter((r) => menus.value.some((m) => m.route === r)).slice(0, 6)
  return routes.map((r) => menus.value.find((m) => m.route === r)).filter((m): m is { route: string; title: string; module: string } => !!m)
})

// 最近访问（本地记录，最多 5 个）
const RECENT_KEY = 'erp.wb.recent'
const recent = ref<{ route: string; title: string }[]>([])
function readRecent() {
  try {
    recent.value = JSON.parse(localStorage.getItem(RECENT_KEY) || '[]')
  } catch {
    recent.value = []
  }
}
let tracking = false
function trackRecent() {
  if (tracking) return
  tracking = true
  router.afterEach((to) => {
    const m = menus.value.find((x) => x.route === to.path)
    if (!m || to.path === '/workbench/home') return
    const list = [{ route: m.route, title: m.title }, ...recent.value.filter((r) => r.route !== m.route)].slice(0, 5)
    recent.value = list
    try {
      localStorage.setItem(RECENT_KEY, JSON.stringify(list))
    } catch {
      /* 存储不可用时忽略 */
    }
  })
}

async function loadAll() {
  const [s, t, c, n, a, sc] = await Promise.allSettled([homeApi.summary(), todoApi.page({ pageNo: 1, pageSize: 8, status: 'PENDING' }),
    homeApi.cards(), noticeApi.active(), alertApi.page({ pageNo: 1, pageSize: 5 }), homeApi.shortcuts()])
  if (s.status === 'fulfilled') summary.value = s.value
  if (t.status === 'fulfilled') todos.value = t.value.list
  if (c.status === 'fulfilled') cards.value = c.value
  if (n.status === 'fulfilled') notices.value = n.value.slice(0, 6)
  if (a.status === 'fulfilled') alerts.value = a.value.list
  if (sc.status === 'fulfilled') shortcuts.value = sc.value
}

// 重要公告弹窗
const popup = ref<ActiveNotice[]>([])
const popupVisible = ref(false)
async function checkImportant() {
  try {
    popup.value = await noticeApi.active(true)
    popupVisible.value = popup.value.length > 0
  } catch {
    popup.value = []
  }
}
async function confirmRead() {
  await Promise.all(popup.value.map((n) => noticeApi.read(n.id)))
  popupVisible.value = false
}

onMounted(() => {
  readRecent()
  trackRecent()
  loadAll()
  checkImportant()
})

// 公告查看
const noticeVisible = ref(false)
const current = ref<ActiveNotice>()
async function openNotice(n: ActiveNotice) {
  current.value = n
  noticeVisible.value = true
  if (!n.read) {
    await noticeApi.read(n.id)
    n.read = true
  }
}

// 编辑布局
const editing = ref(false)
const draft = ref<CardVO[]>([])
function startEdit() {
  draft.value = cards.value.map((c) => ({ ...c }))
  editing.value = true
}
function move(i: number, d: number) {
  const j = i + d
  if (j < 0 || j >= draft.value.length) return
  const list = [...draft.value]
  ;[list[i], list[j]] = [list[j], list[i]]
  draft.value = list
}
async function saveLayout() {
  await homeApi.saveLayout(draft.value.map((c) => ({ code: c.code, visible: c.visible })))
  cards.value = draft.value
  editing.value = false
  ElMessage.success('布局已保存')
}
async function resetLayout() {
  await homeApi.resetLayout()
  cards.value = await homeApi.cards()
  editing.value = false
  ElMessage.success('已恢复默认布局')
}
const visibleCards = computed(() => cards.value.filter((c) => c.visible))

// 编辑快捷入口
const scVisible = ref(false)
const scDraft = ref<string[]>([])
function editShortcuts() {
  scDraft.value = shortcutItems.value.map((s) => s.route)
  scVisible.value = true
}
async function saveShortcuts() {
  if (scDraft.value.length > 12) return ElMessage.warning('快捷入口最多 12 个')
  await homeApi.saveShortcuts(scDraft.value)
  shortcuts.value = [...scDraft.value]
  scVisible.value = false
}

function openTodo(t: Todo) {
  router.push(t.link || t.route || '/workbench/todo')
}
</script>

<template>
  <ErpPage>
    <template #actions>
      <template v-if="editing">
        <el-button @click="editing = false">取消</el-button>
        <el-button @click="resetLayout">恢复默认</el-button>
        <el-button type="primary" @click="saveLayout">保存布局</el-button>
      </template>
      <el-button v-else icon="Edit" @click="startEdit">编辑布局</el-button>
    </template>

    <ErpPanel>
      <div class="welcome">
        <div>
          <div class="hello">{{ greeting }}，{{ me.user?.realName ?? me.user?.username }}</div>
          <div class="date">{{ today }}</div>
        </div>
        <span class="grow" />
        <div class="counter" @click="router.push({ path: '/workbench/todo', query: { category: 'APPROVAL' } })">
          <b>{{ summary?.approvals ?? '-' }}</b><span>待审批</span>
        </div>
        <div class="counter" @click="router.push({ path: '/workbench/todo', query: { category: 'TASK' } })">
          <b>{{ summary?.tasks ?? '-' }}</b><span>待处理</span>
        </div>
        <div class="counter" @click="router.push('/workbench/alert')"><b>{{ summary?.alerts ?? '-' }}</b><span>预警</span></div>
        <div class="counter" @click="router.push('/workbench/message')"><b>{{ summary?.unreadMessages ?? '-' }}</b><span>未读消息</span></div>
      </div>
    </ErpPanel>

    <div class="grid">
      <div class="main">
        <ErpPanel title="我的待办">
          <template #extra><el-link type="primary" underline="never" @click="router.push('/workbench/todo')">查看全部</el-link></template>
          <ErpEmpty v-if="!todos.length" description="暂无待办" compact />
          <div v-for="t in todos" :key="t.id" class="todo" @click="openTodo(t)">
            <StatusTag :value="t.category" :map="TODO_CATEGORY" />
            <span class="todo-title">{{ t.title }}</span>
            <span v-if="t.overdue" class="red">已超期</span>
            <span class="time">{{ t.createdAt?.slice(5, 16) }}</span>
          </div>
        </ErpPanel>

        <ErpPanel v-if="editing" title="编辑布局（勾选显示，调整顺序）">
          <div v-for="(c, i) in draft" :key="c.code" class="edit-row">
            <el-checkbox v-model="c.visible">{{ c.name }}</el-checkbox>
            <span class="grow" />
            <ErpIconButton icon="Up" tooltip="上移" :disabled="i === 0" @click="move(i, -1)" />
            <ErpIconButton icon="Down" tooltip="下移" :disabled="i === draft.length - 1" @click="move(i, 1)" />
          </div>
        </ErpPanel>
        <ErpPanel v-else title="看板">
          <ErpEmpty v-if="!visibleCards.length" description="没有可显示的看板卡片" compact />
          <div class="cards">
            <MetricCard v-for="c in visibleCards" :key="c.code" :card="c" @open="(r) => router.push(r)" />
          </div>
        </ErpPanel>
      </div>

      <div class="side">
        <ErpPanel title="快捷入口">
          <template #extra><el-link type="primary" underline="never" @click="editShortcuts">编辑</el-link></template>
          <div class="shortcuts">
            <el-button v-for="s in shortcutItems" :key="s.route" @click="router.push(s.route)">{{ s.title }}</el-button>
          </div>
          <template v-if="recent.length">
            <div class="sub-title">最近访问</div>
            <div class="recent">
              <el-link v-for="r in recent" :key="r.route" type="primary" underline="never" @click="router.push(r.route)">{{ r.title }}</el-link>
            </div>
          </template>
        </ErpPanel>
        <ErpPanel title="公告">
          <ErpEmpty v-if="!notices.length" description="暂无公告" compact />
          <div v-for="n in notices" :key="n.id" class="notice" @click="openNotice(n)">
            <span v-if="n.important" class="red">[重要]</span>
            <span :class="['notice-title', { unread: !n.read }]">{{ n.title }}</span>
            <span class="time">{{ n.publishAt?.slice(5, 10) }}</span>
          </div>
        </ErpPanel>
        <ErpPanel title="预警">
          <template #extra><el-link type="primary" underline="never" @click="router.push('/workbench/alert')">查看全部</el-link></template>
          <ErpEmpty v-if="!alerts.length" description="暂无未处理预警" compact />
          <div v-for="a in alerts" :key="a.id" class="alert" @click="a.route ? router.push(a.route) : router.push('/workbench/alert')">
            <StatusTag :value="a.level" :map="ALERT_LEVEL" />
            <span class="todo-title">{{ a.title }}</span>
          </div>
        </ErpPanel>
      </div>
    </div>

    <el-dialog v-model="noticeVisible" :title="current?.title" width="640px">
      <!-- 内容已在服务端过滤（WB-NTC-R01） -->
      <div class="notice-body" v-html="current?.content" />
    </el-dialog>

    <el-dialog v-model="popupVisible" title="重要公告" width="640px" :close-on-click-modal="false">
      <div v-for="n in popup" :key="n.id" class="popup-item">
        <h3>{{ n.title }}</h3>
        <div class="notice-body" v-html="n.content" />
      </div>
      <template #footer><el-button type="primary" @click="confirmRead">我已阅读</el-button></template>
    </el-dialog>

    <el-dialog v-model="scVisible" title="编辑快捷入口（最多 12 个）" width="640px">
      <el-checkbox-group v-model="scDraft" class="sc-grid">
        <el-checkbox v-for="m in menus" :key="m.route" :value="m.route" :disabled="!scDraft.includes(m.route) && scDraft.length >= 12">
          {{ m.module }} / {{ m.title }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="scVisible = false">取消</el-button>
        <el-button type="primary" @click="saveShortcuts">保存（{{ scDraft.length }}）</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.welcome { display: flex; align-items: center; gap: var(--erp-space-6); flex-wrap: wrap; }
.hello { font-size: var(--erp-font-size-page-title); font-weight: var(--erp-font-weight-semibold); }
.date { color: var(--erp-color-text-secondary); margin-top: var(--erp-space-1); }
.grow { flex: 1; }
.counter { display: flex; flex-direction: column; align-items: center; cursor: pointer; min-width: 64px; }
.counter b { font-size: var(--erp-font-size-metric); font-weight: var(--erp-font-weight-semibold); color: var(--erp-color-primary); }
.counter span { color: var(--erp-color-text-secondary); }
.grid { display: grid; grid-template-columns: 2fr 1fr; gap: var(--erp-section-gap); }
@media (max-width: 1366px) { .grid { grid-template-columns: 1fr; } }
.main, .side { display: flex; flex-direction: column; gap: var(--erp-section-gap); min-width: 0; }
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: var(--erp-space-3); }
.todo, .notice, .alert { display: flex; align-items: center; gap: var(--erp-space-2); padding: var(--erp-space-2) 0; cursor: pointer;
  border-bottom: 1px solid var(--erp-color-border-light); }
.todo:hover, .notice:hover, .alert:hover { background: var(--erp-color-hover); }
.todo-title, .notice-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.unread { font-weight: var(--erp-font-weight-medium); }
.time { color: var(--erp-color-text-tertiary); font-size: var(--erp-font-size-caption); }
.red { color: var(--erp-color-error); }
.shortcuts { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--erp-space-2); }
.shortcuts .el-button { margin: 0; }
.sub-title { margin: var(--erp-space-3) 0 var(--erp-space-1); color: var(--erp-color-text-secondary); }
.recent { display: flex; gap: var(--erp-space-3); flex-wrap: wrap; }
.edit-row { display: flex; align-items: center; gap: var(--erp-space-2); padding: var(--erp-space-1) 0; }
.sc-grid { display: grid; grid-template-columns: 1fr 1fr; max-height: 420px; overflow: auto; }
.popup-item + .popup-item { margin-top: var(--erp-space-4); }
.notice-body { line-height: 1.7; }
</style>
