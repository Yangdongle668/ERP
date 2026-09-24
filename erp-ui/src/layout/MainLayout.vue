<script setup lang="ts">
import { computed, defineComponent, onBeforeUnmount, onMounted, ref, watch, type VNode } from 'vue'
import { useRoute, useRouter, type RouteLocationNormalizedLoaded } from 'vue-router'
import { ElMessage } from 'element-plus'
import { modules } from '@/modules/registry'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'
import { cacheNameOf, tabKeyOf, useTabsStore } from '@/stores/tabs'
import { CONCURRENT_EVENT } from '@/api/http'
import { authApi } from '@/api/auth'
import { systemCommonApi } from '@/api/system'
import TabsBar from './TabsBar.vue'
import MenuSearch from './MenuSearch.vue'
import TodoBell from './TodoBell.vue'

/** 整体布局（UI 设计规范第 3 节）：侧边栏、头部、多页签栏、内容区 */
const COLLAPSE_KEY = 'erp.sidebarCollapsed'
const route = useRoute()
const router = useRouter()
const store = useUserStore()
const dict = useDictStore()
const tabs = useTabsStore()
const systemName = ref('ERP 系统')

const collapsed = ref(readCollapsed())
function readCollapsed() {
  try {
    return localStorage.getItem(COLLAPSE_KEY) === '1'
  } catch {
    return false
  }
}
watch(collapsed, (v) => {
  try {
    localStorage.setItem(COLLAPSE_KEY, v ? '1' : '0')
  } catch {
    /* ignore */
  }
})

/** 只显示有权限的菜单；一个子菜单都没有权限的模块整体隐藏 */
const visibleModules = computed(() =>
  modules
    .map((m) => ({ ...m, menus: m.menus.filter((x) => !x.hidden && store.hasPermission(x.permission)) }))
    .filter((m) => m.menus.length > 0)
)

/** 当前页面对应的菜单（详情页等隐藏页面高亮其所属列表菜单） */
const activeMenu = computed(() => {
  const m = modules.find((x) => x.code === route.path.split('/')[1])
  if (!m) return route.path
  const exact = m.menus.find((x) => `/${m.code}/${x.path}` === route.path && !x.hidden)
  if (exact) return route.path
  const parent = m.menus.filter((x) => !x.hidden && route.path.startsWith(`/${m.code}/${x.path}/`)).sort((a, b) => b.path.length - a.path.length)[0]
  return parent ? `/${m.code}/${parent.path}` : route.path
})

// ---------- 页签缓存 ----------
watch(() => route.fullPath, () => tabs.open(route), { immediate: true })

/** 每个页签一个具名包装组件，keep-alive 按名称缓存；关闭页签时从 include 移除即销毁 */
const wrappers = new Map<string, ReturnType<typeof defineComponent>>()
const latest = new Map<string, VNode>()
function wrap(vnode: VNode, r: RouteLocationNormalizedLoaded) {
  const name = cacheNameOf(tabKeyOf(r))
  latest.set(name, vnode)
  let w = wrappers.get(name)
  if (!w) {
    w = defineComponent({ name, setup: () => () => latest.get(name) })
    wrappers.set(name, w)
  }
  return w
}
watch(() => tabs.tabs.map((t) => t.cacheName), (names) => {
  const alive = new Set(names)
  for (const n of [...wrappers.keys()]) {
    if (!alive.has(n)) {
      wrappers.delete(n)
      latest.delete(n)
    }
  }
})
const viewKey = (r: RouteLocationNormalizedLoaded) => {
  const k = tabKeyOf(r)
  return `${k}#${tabs.versions[k] ?? 0}`
}

const onConcurrent = () => tabs.refresh(tabKeyOf(route))

// ---------- 用户菜单 ----------
async function logout() {
  await store.logout()
  tabs.clear()
  dict.reset()
  router.push('/login')
}

async function toggleLanguage() {
  const language = store.user?.language === 'en' ? 'zh-CN' : 'en'
  await authApi.updateProfile({ language })
  await store.loadUser()
  ElMessage.success(language === 'en' ? 'Language: English（国际化完成后生效）' : '已切换为中文')
}

// ---------- 无操作自动退出（参数 sys.session.idle-timeout-minutes） ----------
let idleMinutes = 0
let lastActive = Date.now()
let idleTimer = 0
const markActive = () => (lastActive = Date.now())
const ACTIVITY = ['mousemove', 'keydown', 'mousedown', 'wheel', 'touchstart'] as const

onMounted(async () => {
  dict.load()
  dict.startPolling()
  window.addEventListener(CONCURRENT_EVENT, onConcurrent)
  const p = await systemCommonApi.publicParams().catch(() => undefined)
  if (p?.systemName) systemName.value = p.systemName
  idleMinutes = p?.idleTimeoutMinutes ?? 0
  if (idleMinutes > 0) {
    ACTIVITY.forEach((e) => window.addEventListener(e, markActive, { passive: true }))
    idleTimer = window.setInterval(() => {
      if (Date.now() - lastActive > idleMinutes * 60000) {
        ElMessage.warning(`超过 ${idleMinutes} 分钟无操作，已自动退出`)
        logout()
      }
    }, 30000)
  }
})

onBeforeUnmount(() => {
  dict.stopPolling()
  window.removeEventListener(CONCURRENT_EVENT, onConcurrent)
  ACTIVITY.forEach((e) => window.removeEventListener(e, markActive))
  window.clearInterval(idleTimer)
})
</script>

<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '216px'" class="aside">
      <div class="logo" :title="systemName">{{ collapsed ? systemName.slice(0, 1) : systemName }}</div>
      <el-scrollbar>
        <el-menu :default-active="activeMenu" :collapse="collapsed" :collapse-transition="false" router unique-opened>
          <el-sub-menu v-for="m in visibleModules" :key="m.code" :index="m.code">
            <template #title>
              <el-icon><component :is="m.icon" /></el-icon>
              <span>{{ m.title }}</span>
            </template>
            <el-menu-item v-for="menu in m.menus" :key="menu.path" :index="`/${m.code}/${menu.path}`">
              {{ menu.title }}
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </el-scrollbar>
    </el-aside>
    <el-container class="right">
      <el-header class="header">
        <el-button text @click="collapsed = !collapsed">
          <el-icon><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
        </el-button>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item v-if="route.meta.module">{{ route.meta.module }}</el-breadcrumb-item>
          <el-breadcrumb-item>{{ tabs.customTitles[tabKeyOf(route)] ?? route.meta.title }}</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="spacer" />
        <MenuSearch :modules="visibleModules" />
        <TodoBell />
        <el-dropdown trigger="click">
          <span class="user">
            <el-icon><UserFilled /></el-icon>{{ store.user?.realName ?? store.user?.username }}<el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item icon="User" @click="router.push('/profile')">个人中心</el-dropdown-item>
              <el-dropdown-item icon="Lock" @click="router.push({ path: '/profile', query: { tab: 'password' } })">修改密码</el-dropdown-item>
              <el-dropdown-item icon="Switch" @click="toggleLanguage">{{ store.user?.language === 'en' ? '切换为中文' : 'Switch to English' }}</el-dropdown-item>
              <el-dropdown-item icon="SwitchButton" divided @click="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <TabsBar />
      <el-main class="main">
        <router-view v-slot="{ Component, route: r }">
          <keep-alive :include="tabs.cachedNames" :max="20">
            <component :is="wrap(Component, r)" v-if="Component" :key="viewKey(r)" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout { height: 100vh; }
.aside { background: var(--el-bg-color); border-right: 1px solid var(--el-border-color-light); transition: width .2s; display: flex; flex-direction: column; overflow: hidden; }
.aside .el-menu { border-right: none; }
.logo { height: 56px; line-height: 56px; text-align: center; font-weight: 600; font-size: 18px; color: var(--el-color-primary); flex-shrink: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; padding: 0 8px; }
.right { min-width: 0; }
.header { height: 56px; display: flex; align-items: center; gap: 12px; border-bottom: 1px solid var(--el-border-color-light); background: var(--el-bg-color); }
.spacer { flex: 1; }
.user { cursor: pointer; display: inline-flex; align-items: center; gap: 4px; }
.main { background: var(--el-bg-color-page); padding: 16px; }
</style>
