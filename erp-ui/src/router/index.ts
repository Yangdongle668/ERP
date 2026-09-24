import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { modules } from '@/modules/registry'
import { useUserStore } from '@/stores/user'
import MainLayout from '@/layout/MainLayout.vue'

const PlaceholderPage = () => import('@/components/PlaceholderPage.vue')

/** 由模块定义生成路由：/<模块code>/<菜单path> */
const moduleRoutes: RouteRecordRaw[] = modules.flatMap((m) =>
  m.menus.map((menu) => ({
    path: `/${m.code}/${menu.path}`,
    name: `${m.code}.${menu.path}`,
    component: menu.component ?? PlaceholderPage,
    meta: { title: menu.title, module: m.title, permission: menu.permission, doc: m.doc ? `${m.doc}/${menu.doc ?? 'README.md'}` : undefined }
  }))
)

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: () => import('@/views/login/LoginView.vue'), meta: { public: true } },
  {
    path: '/',
    component: MainLayout,
    redirect: '/workbench/home',
    children: [
      ...moduleRoutes,
      { path: '/403', name: 'forbidden', component: () => import('@/views/error/ForbiddenView.vue') }
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
  if (!store.hasPermission(to.meta.permission as string | undefined)) {
    return { path: '/403' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - ERP` : 'ERP'
})

export default router
