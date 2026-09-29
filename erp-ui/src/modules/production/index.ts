import { defineModule } from '../types'

/**
 * 生产模块前端入口（需求 09-生产）：生产订单、工单派工、领料、退料、报工、完工入库、不良、良率、生产追溯、生产报表。
 * 列表页在侧边栏显示；新建、编辑、详情页 hidden。
 */
export default defineModule({
  code: 'production',
  title: '生产',
  icon: 'SetUp',
  order: 80,
  doc: '09-生产',
  menus: [
    { path: 'prod-order', title: '生产订单', permission: 'mfg:prod-order:query', doc: '01-生产订单.md', component: () => import('./views/ProdOrderList.vue') },
    { path: 'prod-order/new', title: '新建生产订单', permission: 'mfg:prod-order:create', hidden: true, doc: '01-生产订单.md', component: () => import('./views/ProdOrderEdit.vue') },
    { path: 'prod-order/:id/edit', title: '编辑生产订单', permission: 'mfg:prod-order:update', hidden: true, doc: '01-生产订单.md', component: () => import('./views/ProdOrderEdit.vue') },
    { path: 'prod-order/:id', title: '生产订单详情', permission: 'mfg:prod-order:query', hidden: true, doc: '01-生产订单.md', component: () => import('./views/ProdOrderDetail.vue') },

    { path: 'work-order', title: '工单派工', permission: 'mfg:work-order:query', doc: '02-工单派工.md', component: () => import('./views/WorkOrderList.vue') },

    { path: 'issue', title: '领料单', permission: 'mfg:issue:query', doc: '03-领料与退料.md', component: () => import('./views/IssueList.vue') },
    { path: 'issue/new', title: '新建领料单', permission: 'mfg:issue:create', hidden: true, doc: '03-领料与退料.md', component: () => import('./views/IssueEdit.vue') },
    { path: 'issue/:id/edit', title: '编辑领料单', permission: 'mfg:issue:update', hidden: true, doc: '03-领料与退料.md', component: () => import('./views/IssueEdit.vue') },
    { path: 'issue/:id', title: '领料单详情', permission: 'mfg:issue:query', hidden: true, doc: '03-领料与退料.md', component: () => import('./views/IssueDetail.vue') },

    { path: 'return', title: '退料单', permission: 'mfg:return:query', doc: '03-领料与退料.md', component: () => import('./views/ReturnList.vue') },
    { path: 'return/new', title: '新建退料单', permission: 'mfg:return:create', hidden: true, doc: '03-领料与退料.md', component: () => import('./views/ReturnEdit.vue') },
    { path: 'return/:id/edit', title: '编辑退料单', permission: 'mfg:return:update', hidden: true, doc: '03-领料与退料.md', component: () => import('./views/ReturnEdit.vue') },
    { path: 'return/:id', title: '退料单详情', permission: 'mfg:return:query', hidden: true, doc: '03-领料与退料.md', component: () => import('./views/ReturnDetail.vue') },

    { path: 'report', title: '报工', permission: 'mfg:report:query', doc: '04-报工.md', component: () => import('./views/ReportList.vue') },
    { path: 'report/new', title: '快速报工', permission: 'mfg:report:create', hidden: true, doc: '04-报工.md', component: () => import('./views/ReportEdit.vue') },
    { path: 'report/:id/edit', title: '编辑报工单', permission: 'mfg:report:update', hidden: true, doc: '04-报工.md', component: () => import('./views/ReportEdit.vue') },
    { path: 'report/:id', title: '报工单详情', permission: 'mfg:report:query', hidden: true, doc: '04-报工.md', component: () => import('./views/ReportDetail.vue') },

    { path: 'finish', title: '完工入库', permission: 'mfg:finish:query', doc: '05-完工入库.md', component: () => import('./views/FinishList.vue') },
    { path: 'defect', title: '不良记录', permission: 'mfg:defect:query', doc: '06-不良与良率.md', component: () => import('./views/DefectList.vue') },
    { path: 'yield', title: '良率报表', permission: 'mfg:defect:query', doc: '06-不良与良率.md', component: () => import('./views/YieldPage.vue') },
    { path: 'trace', title: '生产追溯', permission: 'mfg:trace:query', doc: '07-生产追溯.md', component: () => import('./views/TracePage.vue') },
    { path: 'report-center', title: '生产报表', permission: 'mfg:report-center:query', doc: '08-生产报表.md', component: () => import('./views/ReportCenter.vue') }
  ]
})
