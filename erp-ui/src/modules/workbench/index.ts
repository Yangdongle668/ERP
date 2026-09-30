import { defineModule } from '../types'

/**
 * 工作台模块前端入口（需求 02-工作台）：首页（看板卡片、待办、快捷入口、公告、预警）、我的待办、消息中心、公告管理、预警中心。
 */
export default defineModule({
  code: 'workbench',
  title: '工作台',
  icon: 'HomeFilled',
  order: 10,
  doc: '02-工作台',
  menus: [
    { path: 'home', title: '工作台', doc: '01-首页.md', component: () => import('./views/HomePage.vue') },
    { path: 'todo', title: '我的待办', doc: '02-待办与审批.md', component: () => import('./views/TodoPage.vue') },
    { path: 'message', title: '消息中心', doc: '03-消息与公告.md', component: () => import('./views/MessagePage.vue') },
    { path: 'notice', title: '公告管理', permission: 'wb:notice:manage', doc: '03-消息与公告.md', component: () => import('./views/NoticeList.vue') },
    { path: 'alert', title: '预警中心', doc: '04-预警中心.md', component: () => import('./views/AlertPage.vue') }
  ]
})
