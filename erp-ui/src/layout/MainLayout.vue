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
    <el-aside :width="collapsed ? 'var(--erp-sidebar-collapsed-width)' : 'var(--erp-sidebar-width)'" :class="['aside', { 'is-collapsed': collapsed }]">
      <div class="brand" :title="systemName">
        <span class="brand__mark">{{ systemName.slice(0, 1) }}</span>
        <span v-show="!collapsed" class="brand__name">{{ systemName }}</span>
      </div>
      <el-scrollbar class="aside__menu">
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
        <ErpIconButton :icon="collapsed ? 'Expand' : 'Fold'" :tooltip="collapsed ? '展开侧边栏' : '收起侧边栏'" @click="collapsed = !collapsed" />
        <MenuSearch :modules="visibleModules" />
        <div class="erp-spacer" />
        <TodoBell />
        <span class="header__divider" />
        <el-dropdown trigger="click" placement="bottom-end">
          <button type="button" class="user">
            <span class="user__avatar">{{ (store.user?.realName ?? store.user?.username ?? '?').slice(0, 1) }}</span>
            <span class="user__name">{{ store.user?.realName ?? store.user?.username }}</span>
            <el-icon class="user__arrow"><ArrowDown /></el-icon>
          </button>
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

/* ---------- 侧边栏 ---------- */
.aside {
  background: var(--erp-color-sidebar); border-right: 1px solid var(--erp-color-border); display: flex; flex-direction: column;
  overflow: hidden; transition: width var(--erp-duration) var(--erp-ease);
}
.brand { height: var(--erp-header-height); display: flex; align-items: center; gap: 10px; padding: 0 18px; flex-shrink: 0; overflow: hidden; }
.aside.is-collapsed .brand { padding: 0; justify-content: center; }
.brand__mark {
  width: 28px; height: 28px; border-radius: var(--erp-radius-control); background: var(--erp-color-text); color: var(--erp-color-surface);
  display: inline-flex; align-items: center; justify-content: center; font-size: var(--erp-font-size-body); font-weight: var(--erp-font-weight-semibold); flex-shrink: 0;
}
.brand__name { font-size: var(--erp-font-size-section-title); font-weight: var(--erp-font-weight-semibold); color: var(--erp-color-text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.aside__menu { flex: 1; }
.aside :deep(.el-menu) { border-right: none; padding: 4px 8px 16px; }
.aside :deep(.el-menu--collapse) { width: 100%; padding: 4px 8px; }
.aside :deep(.el-sub-menu__title) {
  border-radius: var(--erp-radius-control); color: var(--erp-color-text); font-weight: var(--erp-font-weight-medium); height: 40px; line-height: 40px;
  padding-left: 12px !important;
}
.aside :deep(.el-sub-menu__title .el-icon) { font-size: var(--erp-icon-size); color: var(--erp-color-text-secondary); margin-right: 10px; }
.aside :deep(.el-sub-menu.is-active > .el-sub-menu__title .el-icon) { color: var(--el-color-primary); }
.aside :deep(.el-sub-menu__title:hover), .aside :deep(.el-menu-item:hover) { background: var(--erp-color-hover); }
.aside :deep(.el-menu-item) {
  height: 36px; line-height: 36px; margin: 2px 0; border-radius: var(--erp-radius-control); padding-left: 38px !important;
  color: var(--erp-color-text-secondary); transition: background-color var(--erp-duration-fast) var(--erp-ease), color var(--erp-duration-fast) var(--erp-ease);
}
.aside :deep(.el-menu-item.is-active) { background: var(--erp-color-primary-bg); color: var(--el-color-primary); font-weight: var(--erp-font-weight-medium); }
.aside :deep(.el-menu--collapse .el-sub-menu__title) { padding: 0 !important; justify-content: center; }
.aside :deep(.el-menu--collapse .el-sub-menu__title .el-icon) { margin: 0; }

/* ---------- 头部 ---------- */
.right { min-width: 0; }
.header {
  height: var(--erp-header-height); display: flex; align-items: center; gap: var(--erp-space-2); padding: 0 16px 0 12px;
  border-bottom: 1px solid var(--erp-color-border); background: var(--erp-color-surface);
}
.header__divider { width: 1px; height: 20px; background: var(--erp-color-border); margin: 0 4px; }
.user {
  display: inline-flex; align-items: center; gap: 8px; height: 36px; padding: 0 8px 0 4px; border: none; border-radius: var(--erp-radius-control);
  background: transparent; cursor: pointer; color: var(--erp-color-text); font-family: inherit; font-size: var(--erp-font-size-body);
  transition: background-color var(--erp-duration-fast) var(--erp-ease);
}
.user:hover { background: var(--erp-color-hover); }
.user__avatar {
  width: 28px; height: 28px; border-radius: 50%; background: var(--erp-color-active); color: var(--erp-color-text-secondary);
  display: inline-flex; align-items: center; justify-content: center; font-size: var(--erp-font-size-caption); font-weight: var(--erp-font-weight-medium);
}
.user__name { white-space: nowrap; max-width: 120px; overflow: hidden; text-overflow: ellipsis; }
.user__arrow { color: var(--erp-color-text-tertiary); font-size: var(--erp-icon-size-sm); }

/* ---------- 内容区 ---------- */
.main { background: var(--erp-color-bg); padding: var(--erp-page-padding-y) var(--erp-page-padding-x) var(--erp-space-6); }
</style>
