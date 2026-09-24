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
  doc: '02-工作台.md',
  menus: [
    { path: 'home', title: '工作台' },
    { path: 'todo', title: '我的待办' },
    { path: 'message', title: '消息通知' },
    { path: 'alert', title: '预警中心', permission: 'workbench:alert:handle' }
  ]
})
