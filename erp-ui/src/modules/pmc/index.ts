import { defineModule } from '../types'

/**
 * PMC 模块前端入口（需求 06-PMC）：需求池、交期回复、MPS、MRP、排产、产能、缺料分析、交期预警、出货计划。
 * 列表页在侧边栏显示；编辑、详情页 hidden。
 */
export default defineModule({
  code: 'pmc',
  title: 'PMC',
  icon: 'Calendar',
  order: 50,
  doc: '06-PMC',
  menus: [
    { path: 'demand', title: '需求池', permission: 'pmc:demand:query', doc: '01-需求池与交期回复.md', component: () => import('./views/DemandList.vue') },
    { path: 'delivery-reply', title: '交期回复', permission: 'pmc:demand:query', doc: '01-需求池与交期回复.md', component: () => import('./views/DeliveryReply.vue') },
    { path: 'mps', title: 'MPS', permission: 'pmc:mps:query', doc: '02-MPS.md', component: () => import('./views/MpsList.vue') },
    { path: 'mps/:id', title: 'MPS 编制', permission: 'pmc:mps:query', hidden: true, doc: '02-MPS.md', component: () => import('./views/MpsEdit.vue') },
    { path: 'mrp', title: 'MRP 运算', permission: 'pmc:mrp:query', doc: '03-MRP运算.md', component: () => import('./views/MrpRunList.vue') },
    { path: 'mrp/suggestions', title: 'MRP 建议', permission: 'pmc:mrp:query', doc: '04-MRP建议处理.md', component: () => import('./views/MrpSuggestions.vue') },
    { path: 'mrp/balance', title: '供需平衡', permission: 'pmc:mrp:query', hidden: true, doc: '03-MRP运算.md', component: () => import('./views/MrpBalance.vue') },
    { path: 'schedule', title: '排产', permission: 'pmc:schedule:query', doc: '05-排产与产能.md', component: () => import('./views/SchedulePage.vue') },
    { path: 'capacity', title: '产能负荷', permission: 'pmc:capacity:query', doc: '05-排产与产能.md', component: () => import('./views/CapacityPage.vue') },
    { path: 'shortage', title: '缺料分析', permission: 'pmc:shortage:query', doc: '06-缺料分析.md', component: () => import('./views/ShortagePage.vue') },
    { path: 'alert', title: '交期预警', permission: 'pmc:alert:query', doc: '07-交期预警.md', component: () => import('./views/AlertList.vue') },
    { path: 'shipping-plan', title: '出货计划', permission: 'pmc:shipping-plan:query', doc: '08-出货计划.md', component: () => import('./views/ShippingPlanList.vue') },
    { path: 'shipping-plan/:id', title: '出货计划详情', permission: 'pmc:shipping-plan:query', hidden: true, doc: '08-出货计划.md', component: () => import('./views/ShippingPlanEdit.vue') }
  ]
})
