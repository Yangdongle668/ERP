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
  doc: '09-生产',
  menus: [
    { path: 'prod-order', title: '生产订单', permission: 'mfg:prod-order:query', doc: '01-生产订单.md' },
    { path: 'work-order', title: '工单派工', permission: 'mfg:work-order:query', doc: '02-工单派工.md' },
    { path: 'issue', title: '领料单', permission: 'mfg:issue:query', doc: '03-领料与退料.md' },
    { path: 'return', title: '退料单', permission: 'mfg:return:query', doc: '03-领料与退料.md' },
    { path: 'report', title: '报工', permission: 'mfg:report:query', doc: '04-报工.md' },
    { path: 'finish', title: '完工入库', permission: 'mfg:finish:query', doc: '05-完工入库.md' },
    { path: 'defect', title: '不良记录', permission: 'mfg:defect:query', doc: '06-不良与良率.md' },
    { path: 'yield', title: '良率报表', permission: 'mfg:defect:query', doc: '06-不良与良率.md' },
    { path: 'trace', title: '生产追溯', permission: 'mfg:trace:query', doc: '07-生产追溯.md' },
    { path: 'report-center', title: '生产报表', permission: 'mfg:report-center:query', doc: '08-生产报表.md' }
  ]
})
