import { defineModule } from '../types'

/**
 * 销售模块前端入口（需求 04-销售）：RFQ、报价、销售订单、订单变更、销售预测、销售退货、回款跟踪、价格表、销售报表。
 * 列表页在侧边栏显示；新建、编辑、详情页 hidden。
 */
export default defineModule({
  code: 'sales',
  title: '销售',
  icon: 'Sell',
  order: 30,
  doc: '04-销售',
  menus: [
    { path: 'rfq', title: 'RFQ', permission: 'sales:rfq:query', doc: '02-RFQ与报价.md', component: () => import('./views/RfqList.vue') },
    { path: 'rfq/new', title: '新建 RFQ', permission: 'sales:rfq:create', hidden: true, doc: '02-RFQ与报价.md', component: () => import('./views/RfqEdit.vue') },
    { path: 'rfq/:id/edit', title: '编辑 RFQ', permission: 'sales:rfq:update', hidden: true, doc: '02-RFQ与报价.md', component: () => import('./views/RfqEdit.vue') },
    { path: 'rfq/:id', title: 'RFQ 详情', permission: 'sales:rfq:query', hidden: true, doc: '02-RFQ与报价.md', component: () => import('./views/RfqDetail.vue') },

    { path: 'quotation', title: '报价单', permission: 'sales:quotation:query', doc: '02-RFQ与报价.md', component: () => import('./views/QuotationList.vue') },
    { path: 'quotation/new', title: '新建报价单', permission: 'sales:quotation:create', hidden: true, doc: '02-RFQ与报价.md', component: () => import('./views/QuotationEdit.vue') },
    { path: 'quotation/:id/edit', title: '编辑报价单', permission: 'sales:quotation:update', hidden: true, doc: '02-RFQ与报价.md', component: () => import('./views/QuotationEdit.vue') },
    { path: 'quotation/:id', title: '报价单详情', permission: 'sales:quotation:query', hidden: true, doc: '02-RFQ与报价.md', component: () => import('./views/QuotationDetail.vue') },

    { path: 'order', title: '销售订单', permission: 'sales:order:query', doc: '03-销售订单.md', component: () => import('./views/OrderList.vue') },
    { path: 'order/new', title: '新建销售订单', permission: 'sales:order:create', hidden: true, doc: '03-销售订单.md', component: () => import('./views/OrderEdit.vue') },
    { path: 'order/:id/edit', title: '编辑销售订单', permission: 'sales:order:update', hidden: true, doc: '03-销售订单.md', component: () => import('./views/OrderEdit.vue') },
    { path: 'order/:id', title: '销售订单详情', permission: 'sales:order:query', hidden: true, doc: '03-销售订单.md', component: () => import('./views/OrderDetail.vue') },

    { path: 'order-change', title: '订单变更', permission: 'sales:order:change', doc: '04-订单变更.md', component: () => import('./views/ChangeList.vue') },
    { path: 'order-change/:id/edit', title: '编辑订单变更', permission: 'sales:order:change', hidden: true, doc: '04-订单变更.md', component: () => import('./views/ChangeDetail.vue') },
    { path: 'order-change/:id', title: '订单变更详情', permission: 'sales:order:query', hidden: true, doc: '04-订单变更.md', component: () => import('./views/ChangeDetail.vue') },

    { path: 'forecast', title: '销售预测', permission: 'sales:forecast:query', doc: '05-销售预测.md', component: () => import('./views/ForecastList.vue') },
    { path: 'forecast/new', title: '新建预测', permission: 'sales:forecast:create', hidden: true, doc: '05-销售预测.md', component: () => import('./views/ForecastEdit.vue') },
    { path: 'forecast/:id/edit', title: '编辑预测', permission: 'sales:forecast:update', hidden: true, doc: '05-销售预测.md', component: () => import('./views/ForecastEdit.vue') },
    { path: 'forecast/:id', title: '预测详情', permission: 'sales:forecast:query', hidden: true, doc: '05-销售预测.md', component: () => import('./views/ForecastDetail.vue') },

    { path: 'return', title: '销售退货', permission: 'sales:return:query', doc: '06-销售退货.md', component: () => import('./views/ReturnList.vue') },
    { path: 'return/new', title: '新建退货', permission: 'sales:return:create', hidden: true, doc: '06-销售退货.md', component: () => import('./views/ReturnEdit.vue') },
    { path: 'return/:id/edit', title: '编辑退货单', permission: 'sales:return:update', hidden: true, doc: '06-销售退货.md', component: () => import('./views/ReturnEdit.vue') },
    { path: 'return/:id', title: '退货单详情', permission: 'sales:return:query', hidden: true, doc: '06-销售退货.md', component: () => import('./views/ReturnDetail.vue') },

    { path: 'payment-plan', title: '回款跟踪', permission: 'sales:order:query', doc: '07-回款跟踪.md', component: () => import('./views/PaymentPlanList.vue') },

    { path: 'price-list', title: '价格表', permission: 'sales:price-list:query', doc: '01-价格表.md', component: () => import('./views/PriceListList.vue') },
    { path: 'price-list/new', title: '新建价格表', permission: 'sales:price-list:create', hidden: true, doc: '01-价格表.md', component: () => import('./views/PriceListEdit.vue') },
    { path: 'price-list/:id/edit', title: '编辑价格表', permission: 'sales:price-list:update', hidden: true, doc: '01-价格表.md', component: () => import('./views/PriceListEdit.vue') },
    { path: 'price-list/:id', title: '价格表详情', permission: 'sales:price-list:query', hidden: true, doc: '01-价格表.md', component: () => import('./views/PriceListDetail.vue') },

    { path: 'report', title: '销售报表', permission: 'sales:report:query', doc: '08-销售报表.md', component: () => import('./views/ReportPage.vue') }
  ]
})
