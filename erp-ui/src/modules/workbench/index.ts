import { defineModule } from '../types'

/**
 * 工作台模块前端入口。菜单项未指定 component 时显示“开发中”占位页；
 * 开发某个页面时在 ./views 下新建组件并在此处填写 component 即可。
 */
export default defineModule({
  code: 'workbench',
  title: '工作台',
  icon: 'HomeFilled',
  order: 10,
  doc: '02-工作台',
  menus: [
    { path: 'home', title: '工作台', doc: '01-首页.md' },
    { path: 'todo', title: '我的待办', doc: '02-待办与审批.md' },
    { path: 'message', title: '消息中心', doc: '03-消息与公告.md' },
    { path: 'notice', title: '公告管理', permission: 'wb:notice:manage', doc: '03-消息与公告.md' },
    { path: 'alert', title: '预警中心', doc: '04-预警中心.md' }
  ]
})
