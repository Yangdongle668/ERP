<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { HOME_PATH, tabKeyOf, useTabsStore, type TabItem } from '@/stores/tabs'
import { LEAVE_MESSAGE } from '@/composables/useLeaveGuard'

/**
 * 多页签栏（UI 设计规范 3.3）：已修改的页签标题前显示 ●；关闭已修改页签时提示；
 * 右键菜单：刷新、关闭、关闭其他、关闭右侧、关闭全部；工作台页签固定不可关闭。
 */
const tabs = useTabsStore()
const route = useRoute()
const router = useRouter()
const activeKey = computed(() => tabKeyOf(route))
const scrollRef = ref<HTMLElement>()

watch(activeKey, () => nextTick(() => scrollRef.value?.querySelector('.tab.active')?.scrollIntoView({ inline: 'nearest', block: 'nearest' })))

async function confirmClose(targets: TabItem[]): Promise<boolean> {
  const dirty = targets.filter((t) => t.dirty)
  if (!dirty.length) return true
  const names = dirty.map((t) => `「${t.title}」`).join('、')
  return ElMessageBox.confirm(dirty.length > 1 ? `${names}有未保存的修改，确定关闭吗？` : LEAVE_MESSAGE.replace('离开', '关闭'), '提示', { type: 'warning' })
    .then(() => true)
    .catch(() => false)
}

async function close(targets: TabItem[]) {
  const closable = targets.filter((t) => !t.fixed)
  if (!closable.length || !(await confirmClose(closable))) return
  const keys = closable.map((t) => t.key)
  const activeClosed = keys.includes(activeKey.value)
  const index = tabs.tabs.findIndex((t) => t.key === activeKey.value)
  tabs.remove(keys)
  if (activeClosed) {
    const next = tabs.tabs[Math.min(index, tabs.tabs.length - 1)] ?? tabs.tabs[tabs.tabs.length - 1]
    await router.push(next?.fullPath ?? HOME_PATH)
  }
}

function go(t: TabItem) {
  if (t.key !== activeKey.value) router.push(t.fullPath)
}

// ---------- 右键菜单 ----------
const menu = reactive({ visible: false, x: 0, y: 0, tab: null as TabItem | null })
function openMenu(e: MouseEvent, t: TabItem) {
  e.preventDefault()
  Object.assign(menu, { visible: true, x: e.clientX, y: e.clientY, tab: t })
  const hide = () => {
    menu.visible = false
    window.removeEventListener('click', hide)
  }
  setTimeout(() => window.addEventListener('click', hide))
}

async function command(cmd: 'refresh' | 'close' | 'others' | 'right' | 'all') {
  const t = menu.tab
  menu.visible = false
  if (!t) return
  const i = tabs.tabs.indexOf(t)
  switch (cmd) {
    case 'refresh':
      if (t.key !== activeKey.value) await router.push(t.fullPath)
      tabs.refresh(t.key)
      break
    case 'close': await close([t]); break
    case 'others':
      await close(tabs.tabs.filter((x) => x !== t))
      if (t.key !== activeKey.value) router.push(t.fullPath)
      break
    case 'right': await close(tabs.tabs.slice(i + 1)); break
    case 'all': await close([...tabs.tabs]); break
  }
}

function onWheel(e: WheelEvent) {
  if (scrollRef.value) scrollRef.value.scrollLeft += e.deltaY
}
</script>

<template>
  <div class="tabs-bar">
    <div ref="scrollRef" class="tabs-scroll" @wheel.prevent="onWheel">
      <div
        v-for="t in tabs.tabs"
        :key="t.key"
        :class="['tab', { active: t.key === activeKey }]"
        :title="t.title"
        @click="go(t)"
        @contextmenu="openMenu($event, t)"
        @mousedown.middle.prevent="close([t])"
      >
        <span v-if="t.dirty" class="dirty">●</span>
        <span class="title">{{ t.title }}</span>
        <el-icon v-if="!t.fixed" class="close" @click.stop="close([t])"><Close /></el-icon>
      </div>
    </div>
    <ul v-show="menu.visible" class="context-menu" :style="{ left: `${menu.x}px`, top: `${menu.y}px` }">
      <li @click="command('refresh')"><el-icon><Refresh /></el-icon>刷新</li>
      <li v-if="!menu.tab?.fixed" @click="command('close')"><el-icon><Close /></el-icon>关闭</li>
      <li @click="command('others')"><el-icon><CircleClose /></el-icon>关闭其他</li>
      <li @click="command('right')"><el-icon><Right /></el-icon>关闭右侧</li>
      <li @click="command('all')"><el-icon><FolderDelete /></el-icon>关闭全部</li>
    </ul>
  </div>
</template>

<style scoped>
.tabs-bar { height: 36px; display: flex; align-items: center; background: var(--el-bg-color); border-bottom: 1px solid var(--el-border-color-light); padding: 0 8px; }
.tabs-scroll { display: flex; gap: 4px; overflow-x: auto; overflow-y: hidden; scrollbar-width: none; flex: 1; }
.tabs-scroll::-webkit-scrollbar { display: none; }
.tab {
  display: inline-flex; align-items: center; gap: 4px; height: 26px; padding: 0 10px; border: 1px solid var(--el-border-color-light);
  border-radius: 3px; font-size: 13px; cursor: pointer; white-space: nowrap; color: var(--el-text-color-regular); background: var(--el-bg-color);
}
.tab:hover { color: var(--el-color-primary); }
.tab.active { color: #fff; background: var(--el-color-primary); border-color: var(--el-color-primary); }
.tab .title { max-width: 180px; overflow: hidden; text-overflow: ellipsis; }
.tab .dirty { color: var(--el-color-warning); font-size: 10px; }
.tab.active .dirty { color: #fff; }
.tab .close { border-radius: 50%; font-size: 12px; }
.tab .close:hover { background: var(--el-color-info-light-5); color: #fff; }
.context-menu {
  position: fixed; z-index: 3000; margin: 0; padding: 4px 0; list-style: none; background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light); border-radius: 4px; box-shadow: var(--el-box-shadow-light); font-size: 13px;
}
.context-menu li { display: flex; align-items: center; gap: 6px; padding: 6px 16px; cursor: pointer; }
.context-menu li:hover { background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
</style>
