<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { modules } from '@/modules/registry'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const collapsed = ref(false)

/** 只显示有权限的菜单；一个子菜单都没有权限的模块整体隐藏 */
const visibleModules = computed(() =>
  modules
    .map((m) => ({ ...m, menus: m.menus.filter((x) => !x.hidden && store.hasPermission(x.permission)) }))
    .filter((m) => m.menus.length > 0)
)

function logout() {
  store.clear()
  router.push('/login')
}
</script>

<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '216px'" class="aside">
      <div class="logo">{{ collapsed ? 'E' : 'ERP 系统' }}</div>
      <el-scrollbar>
        <el-menu :default-active="route.path" :collapse="collapsed" router unique-opened>
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
    <el-container>
      <el-header class="header">
        <el-button text @click="collapsed = !collapsed">
          <el-icon><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
        </el-button>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item>{{ route.meta.module }}</el-breadcrumb-item>
          <el-breadcrumb-item>{{ route.meta.title }}</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="spacer" />
        <el-dropdown>
          <span class="user">{{ store.user?.realName ?? store.user?.username }}</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout { height: 100vh; }
.aside { background: var(--el-bg-color); border-right: 1px solid var(--el-border-color-light); transition: width .2s; display: flex; flex-direction: column; }
.aside .el-menu { border-right: none; }
.logo { height: 56px; line-height: 56px; text-align: center; font-weight: 600; font-size: 18px; color: var(--el-color-primary); flex-shrink: 0; }
.header { display: flex; align-items: center; gap: 12px; border-bottom: 1px solid var(--el-border-color-light); background: var(--el-bg-color); }
.spacer { flex: 1; }
.user { cursor: pointer; }
.main { background: var(--el-bg-color-page); }
</style>
