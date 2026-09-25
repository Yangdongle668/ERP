import { defineModule } from '../types'

/**
 * 资材模块前端入口（需求 07-资材）：供应商、采购价格、采购申请、询价比价、采购订单、到货、委外加工、采购退货、供应商对账、供应商评估、采购报表。
 * 列表页在侧边栏显示；新建、编辑、详情页 hidden。
 */
export default defineModule({
  code: 'purchase',
  title: '资材',
  icon: 'ShoppingCart',
  order: 60,
  doc: '07-资材',
  menus: [
    { path: 'supplier', title: '供应商', permission: 'pur:supplier:query', doc: '01-供应商.md', component: () => import('./views/SupplierList.vue') },
    { path: 'supplier/new', title: '新建供应商', permission: 'pur:supplier:create', hidden: true, doc: '01-供应商.md', component: () => import('./views/SupplierEdit.vue') },
    { path: 'supplier/:id/edit', title: '编辑供应商', permission: 'pur:supplier:update', hidden: true, doc: '01-供应商.md', component: () => import('./views/SupplierEdit.vue') },
    { path: 'supplier/:id', title: '供应商详情', permission: 'pur:supplier:query', hidden: true, doc: '01-供应商.md', component: () => import('./views/SupplierDetail.vue') },

    { path: 'price', title: '采购价格', permission: 'pur:price:query', doc: '02-采购价格.md', component: () => import('./views/PriceList.vue') },
    { path: 'price/adjust/new', title: '新建调价单', permission: 'pur:price:adjust', hidden: true, doc: '02-采购价格.md', component: () => import('./views/PriceAdjustEdit.vue') },
    { path: 'price/adjust/:id/edit', title: '编辑调价单', permission: 'pur:price:adjust', hidden: true, doc: '02-采购价格.md', component: () => import('./views/PriceAdjustEdit.vue') },
    { path: 'price/adjust/:id', title: '调价单详情', permission: 'pur:price:query', hidden: true, doc: '02-采购价格.md', component: () => import('./views/PriceAdjustDetail.vue') },

    { path: 'requisition', title: '采购申请', permission: 'pur:requisition:query', doc: '03-采购申请.md', component: () => import('./views/RequisitionList.vue') },
    { path: 'requisition/new', title: '新建采购申请', permission: 'pur:requisition:create', hidden: true, doc: '03-采购申请.md', component: () => import('./views/RequisitionEdit.vue') },
    { path: 'requisition/:id/edit', title: '编辑采购申请', permission: 'pur:requisition:update', hidden: true, doc: '03-采购申请.md', component: () => import('./views/RequisitionEdit.vue') },
    { path: 'requisition/:id', title: '采购申请详情', permission: 'pur:requisition:query', hidden: true, doc: '03-采购申请.md', component: () => import('./views/RequisitionDetail.vue') },

    { path: 'rfq', title: '询价比价', permission: 'pur:rfq:query', doc: '04-询价比价.md', component: () => import('./views/RfqList.vue') },
    { path: 'rfq/new', title: '新建询价单', permission: 'pur:rfq:create', hidden: true, doc: '04-询价比价.md', component: () => import('./views/RfqEdit.vue') },
    { path: 'rfq/:id/edit', title: '编辑询价单', permission: 'pur:rfq:update', hidden: true, doc: '04-询价比价.md', component: () => import('./views/RfqEdit.vue') },
    { path: 'rfq/:id', title: '询价单详情', permission: 'pur:rfq:query', hidden: true, doc: '04-询价比价.md', component: () => import('./views/RfqDetail.vue') },

    { path: 'order', title: '采购订单', permission: 'pur:order:query', doc: '05-采购订单.md', component: () => import('./views/OrderList.vue') },
    { path: 'order/new', title: '新建采购订单', permission: 'pur:order:create', hidden: true, doc: '05-采购订单.md', component: () => import('./views/OrderEdit.vue') },
    { path: 'order/change/new', title: '变更采购订单', permission: 'pur:order:change', hidden: true, doc: '05-采购订单.md', component: () => import('./views/OrderChangeEdit.vue') },
    { path: 'order/change/:id', title: '订单变更单', permission: 'pur:order:query', hidden: true, doc: '05-采购订单.md', component: () => import('./views/OrderChangeEdit.vue') },
    { path: 'order/:id/edit', title: '编辑采购订单', permission: 'pur:order:update', hidden: true, doc: '05-采购订单.md', component: () => import('./views/OrderEdit.vue') },
    { path: 'order/:id', title: '采购订单详情', permission: 'pur:order:query', hidden: true, doc: '05-采购订单.md', component: () => import('./views/OrderDetail.vue') },

    { path: 'receipt', title: '到货', permission: 'pur:receipt:query', doc: '06-到货.md', component: () => import('./views/ReceiptList.vue') },
    { path: 'receipt/new', title: '新建到货', permission: 'pur:receipt:create', hidden: true, doc: '06-到货.md', component: () => import('./views/ReceiptEdit.vue') },
    { path: 'receipt/:id/edit', title: '编辑到货单', permission: 'pur:receipt:update', hidden: true, doc: '06-到货.md', component: () => import('./views/ReceiptEdit.vue') },
    { path: 'receipt/:id', title: '到货单详情', permission: 'pur:receipt:query', hidden: true, doc: '06-到货.md', component: () => import('./views/ReceiptDetail.vue') },

    { path: 'outsourcing', title: '委外加工', permission: 'pur:outsourcing:query', doc: '07-委外加工.md', component: () => import('./views/OutsourcingList.vue') },
    { path: 'outsourcing/new', title: '新建委外单', permission: 'pur:outsourcing:create', hidden: true, doc: '07-委外加工.md', component: () => import('./views/OutsourcingEdit.vue') },
    { path: 'outsourcing/:id/edit', title: '编辑委外单', permission: 'pur:outsourcing:update', hidden: true, doc: '07-委外加工.md', component: () => import('./views/OutsourcingEdit.vue') },
    { path: 'outsourcing/:id', title: '委外单详情', permission: 'pur:outsourcing:query', hidden: true, doc: '07-委外加工.md', component: () => import('./views/OutsourcingDetail.vue') },

    { path: 'return', title: '采购退货', permission: 'pur:return:query', doc: '08-采购退货.md', component: () => import('./views/ReturnList.vue') },
    { path: 'return/new', title: '新建退货', permission: 'pur:return:create', hidden: true, doc: '08-采购退货.md', component: () => import('./views/ReturnEdit.vue') },
    { path: 'return/:id/edit', title: '编辑退货单', permission: 'pur:return:update', hidden: true, doc: '08-采购退货.md', component: () => import('./views/ReturnEdit.vue') },
    { path: 'return/:id', title: '退货单详情', permission: 'pur:return:query', hidden: true, doc: '08-采购退货.md', component: () => import('./views/ReturnDetail.vue') },

    { path: 'statement', title: '供应商对账', permission: 'pur:statement:query', doc: '09-供应商对账.md', component: () => import('./views/StatementList.vue') },
    { path: 'statement/new', title: '新建对账', permission: 'pur:statement:create', hidden: true, doc: '09-供应商对账.md', component: () => import('./views/StatementEdit.vue') },
    { path: 'statement/:id/edit', title: '编辑对账单', permission: 'pur:statement:update', hidden: true, doc: '09-供应商对账.md', component: () => import('./views/StatementEdit.vue') },
    { path: 'statement/:id', title: '对账单详情', permission: 'pur:statement:query', hidden: true, doc: '09-供应商对账.md', component: () => import('./views/StatementDetail.vue') },

    { path: 'score', title: '供应商评估', permission: 'pur:score:query', doc: '10-供应商评估.md', component: () => import('./views/ScoreList.vue') },
    { path: 'report', title: '采购报表', permission: 'pur:report:query', doc: '11-采购报表.md', component: () => import('./views/ReportPage.vue') }
  ]
})
