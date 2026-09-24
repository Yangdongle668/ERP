import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { modules } from '@/modules/registry'
import type { ModuleMenu } from '@/modules/types'
import { useUserStore } from '@/stores/user'
import MainLayout from '@/layout/MainLayout.vue'

const PlaceholderPage = () => import('@/components/PlaceholderPage.vue')

/** 需求文档路径：模块目录 + 功能文件 */
function docPath(moduleDoc: string | undefined, menuDoc: string | undefined) {
  return moduleDoc ? `${moduleDoc}/${menuDoc ?? 'README.md'}` : undefined
}

/** 隐藏页面（详情、编辑）所属的列表菜单标题，用于面包屑 */
function parentOf(menus: ModuleMenu[], menu: ModuleMenu) {
  if (!menu.hidden) return undefined
  return menus.filter((x) => !x.hidden && menu.path.startsWith(`${x.path}/`)).sort((a, b) => b.path.length - a.path.length)[0]?.title
}

/** 由模块定义生成路由：/<模块code>/<菜单path> */
const moduleRoutes: RouteRecordRaw[] = modules.flatMap((m) =>
  m.menus.map((menu) => ({
    path: `/${m.code}/${menu.path}`,
    name: `${m.code}.${menu.path}`,
    component: menu.component ?? PlaceholderPage,
    meta: { title: menu.title, module: m.title, parentTitle: parentOf(m.menus, menu), permission: menu.permission, doc: docPath(m.doc, menu.doc) }
  }))
)

/** 修改密码前可以访问的页面 */
const PASSWORD_ALLOWED = new Set(['/change-password', '/login'])

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: () => import('@/views/auth/LoginView.vue'), meta: { public: true, title: '登录' } },
  { path: '/change-password', name: 'change-password', component: () => import('@/views/auth/ChangePasswordView.vue'), meta: { title: '修改密码', noTab: true } },
  {
    path: '/',
    component: MainLayout,
    redirect: '/workbench/home',
    children: [
      ...moduleRoutes,
      { path: '/profile', name: 'profile', component: () => import('@/views/profile/ProfileView.vue'), meta: { title: '个人中心' } },
      { path: '/403', name: 'forbidden', component: () => import('@/views/error/ForbiddenView.vue'), meta: { title: '无权限' } }
    ]
  },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/error/NotFoundView.vue'), meta: { public: true } }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(async (to) => {
  if (to.meta.public) return true
  const store = useUserStore()
  if (!store.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (!store.user) {
    try {
      await store.loadUser()
    } catch {
      store.clear()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  if (store.mustChangePassword && !PASSWORD_ALLOWED.has(to.path)) {
    return { path: '/change-password' }
  }
  if (!store.hasPermission(to.meta.permission as string | undefined)) {
    return { path: '/403' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - ERP` : 'ERP'
})

export default router
