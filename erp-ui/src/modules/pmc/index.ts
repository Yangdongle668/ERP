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
  doc: '06-PMC',
  menus: [
    { path: 'demand', title: '需求池', permission: 'pmc:demand:query', doc: '01-需求池与交期回复.md' },
    { path: 'delivery-reply', title: '交期回复', permission: 'pmc:demand:query', doc: '01-需求池与交期回复.md' },
    { path: 'mps', title: 'MPS', permission: 'pmc:mps:query', doc: '02-MPS.md' },
    { path: 'mrp', title: 'MRP 运算', permission: 'pmc:mrp:query', doc: '03-MRP运算.md' },
    { path: 'mrp/suggestions', title: 'MRP 建议', permission: 'pmc:mrp:query', doc: '04-MRP建议处理.md' },
    { path: 'schedule', title: '排产', permission: 'pmc:schedule:query', doc: '05-排产与产能.md' },
    { path: 'capacity', title: '产能负荷', permission: 'pmc:capacity:query', doc: '05-排产与产能.md' },
    { path: 'shortage', title: '缺料分析', permission: 'pmc:shortage:query', doc: '06-缺料分析.md' },
    { path: 'alert', title: '交期预警', permission: 'pmc:alert:query', doc: '07-交期预警.md' },
    { path: 'shipping-plan', title: '出货计划', permission: 'pmc:shipping-plan:query', doc: '08-出货计划.md' }
  ]
})
