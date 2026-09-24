import { defineModule } from '../types'

/**
 * 生产模块前端入口。菜单项未指定 component 时显示“开发中”占位页；
 * 开发某个页面时在 ./views 下新建组件并在此处填写 component 即可。
 */
export default defineModule({
  code: 'production',
  title: '生产',
  icon: 'SetUp',
  order: 80,
  doc: '09-生产.md',
  menus: [
    { path: 'prod-order', title: '生产订单', permission: 'mfg:prod-order:query' },
    { path: 'work-order', title: '工单', permission: 'mfg:work-order:query' },
    { path: 'issue', title: '领料', permission: 'mfg:issue:query' },
    { path: 'return', title: '退料', permission: 'mfg:return:query' },
    { path: 'report', title: '报工', permission: 'mfg:report:query' },
    { path: 'defect', title: '不良', permission: 'mfg:defect:query' },
    { path: 'yield', title: '良率', permission: 'mfg:report:query' },
    { path: 'trace', title: '生产追溯', permission: 'mfg:trace:query' }
  ]
})
