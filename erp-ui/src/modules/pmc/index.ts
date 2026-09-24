import { defineModule } from '../types'

/**
 * PMC模块前端入口。菜单项未指定 component 时显示“开发中”占位页；
 * 开发某个页面时在 ./views 下新建组件并在此处填写 component 即可。
 */
export default defineModule({
  code: 'pmc',
  title: 'PMC',
  icon: 'Calendar',
  order: 50,
  doc: '06-PMC.md',
  menus: [
    { path: 'demand', title: '销售需求', permission: 'pmc:demand:query' },
    { path: 'mps', title: 'MPS', permission: 'pmc:mps:query' },
    { path: 'mrp', title: 'MRP', permission: 'pmc:mrp:query' },
    { path: 'schedule', title: '排产', permission: 'pmc:schedule:query' },
    { path: 'capacity', title: '产能', permission: 'pmc:capacity:query' },
    { path: 'shortage', title: '缺料分析', permission: 'pmc:shortage:query' },
    { path: 'alert', title: '交期预警', permission: 'pmc:alert:query' },
    { path: 'shipping-plan', title: '出货计划', permission: 'pmc:shipping-plan:query' }
  ]
})
