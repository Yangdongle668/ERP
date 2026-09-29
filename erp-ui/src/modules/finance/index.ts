import { defineModule } from '../types'

/**
 * 财务模块前端入口（需求 12-财务）：应收 → 收款核销、应付 → 进项发票 → 付款申请 → 付款，账龄与往来对账单，财务基础设置。
 * 凭证、成本核算、月结在第 2 批实现（暂显示“开发中”占位页）。编辑、详情页 hidden。
 */
export default defineModule({
  code: 'finance',
  title: '财务',
  icon: 'Money',
  order: 110,
  doc: '12-财务',
  menus: [
    { path: 'receivable', title: '应收单', permission: 'fin:receivable:query', doc: '02-应收.md', component: () => import('./views/ReceivableList.vue') },
    { path: 'receivable/other/new', title: '新建其他应收', permission: 'fin:receivable:create-other', hidden: true, doc: '02-应收.md', component: () => import('./views/OtherDocEdit.vue') },
    { path: 'receivable/other/:id/edit', title: '编辑其他应收', permission: 'fin:receivable:create-other', hidden: true, doc: '02-应收.md', component: () => import('./views/OtherDocEdit.vue') },
    { path: 'receivable/invoice', title: '销项发票', permission: 'fin:receivable:query', doc: '02-应收.md', component: () => import('./views/SalesInvoiceList.vue') },
    { path: 'receivable/invoice/new', title: '开票登记', permission: 'fin:receivable:invoice', hidden: true, doc: '02-应收.md', component: () => import('./views/SalesInvoiceEdit.vue') },
    { path: 'receivable/:id', title: '应收单详情', permission: 'fin:receivable:query', hidden: true, doc: '02-应收.md', component: () => import('./views/ReceivableDetail.vue') },
    { path: 'receipt', title: '收款单', permission: 'fin:receipt:query', doc: '03-收款与核销.md', component: () => import('./views/ReceiptList.vue') },
    { path: 'receipt/new', title: '新建收款', permission: 'fin:receipt:create', hidden: true, doc: '03-收款与核销.md', component: () => import('./views/ReceiptEdit.vue') },
    { path: 'receipt/:id/edit', title: '编辑收款', permission: 'fin:receipt:update', hidden: true, doc: '03-收款与核销.md', component: () => import('./views/ReceiptEdit.vue') },
    { path: 'receipt/verify', title: '收款核销', permission: 'fin:receipt:verify', doc: '03-收款与核销.md', component: () => import('./views/VerifyPage.vue') },
    { path: 'receipt/:id', title: '收款单详情', permission: 'fin:receipt:query', hidden: true, doc: '03-收款与核销.md', component: () => import('./views/ReceiptDetail.vue') },
    { path: 'payable', title: '应付单', permission: 'fin:payable:query', doc: '04-应付与发票.md', component: () => import('./views/PayableList.vue') },
    { path: 'payable/other/new', title: '新建其他应付', permission: 'fin:payable:create-other', hidden: true, doc: '04-应付与发票.md', component: () => import('./views/OtherDocEdit.vue') },
    { path: 'payable/other/:id/edit', title: '编辑其他应付', permission: 'fin:payable:create-other', hidden: true, doc: '04-应付与发票.md', component: () => import('./views/OtherDocEdit.vue') },
    { path: 'payable/invoice', title: '进项发票', permission: 'fin:payable:query', doc: '04-应付与发票.md', component: () => import('./views/PurchaseInvoiceList.vue') },
    { path: 'payable/invoice/new', title: '登记进项发票', permission: 'fin:payable:invoice', hidden: true, doc: '04-应付与发票.md', component: () => import('./views/PurchaseInvoiceEdit.vue') },
    { path: 'payable/invoice/:id', title: '进项发票详情', permission: 'fin:payable:query', hidden: true, doc: '04-应付与发票.md', component: () => import('./views/PurchaseInvoiceDetail.vue') },
    { path: 'payable/:id', title: '应付单详情', permission: 'fin:payable:query', hidden: true, doc: '04-应付与发票.md', component: () => import('./views/PayableDetail.vue') },
    { path: 'payment/request', title: '付款申请', permission: 'fin:payment-request:query', doc: '05-付款.md', component: () => import('./views/RequestList.vue') },
    { path: 'payment/request/new', title: '新建付款申请', permission: 'fin:payment-request:create', hidden: true, doc: '05-付款.md', component: () => import('./views/RequestEdit.vue') },
    { path: 'payment/request/:id/edit', title: '编辑付款申请', permission: 'fin:payment-request:create', hidden: true, doc: '05-付款.md', component: () => import('./views/RequestEdit.vue') },
    { path: 'payment/request/:id', title: '付款申请详情', permission: 'fin:payment-request:query', hidden: true, doc: '05-付款.md', component: () => import('./views/RequestDetail.vue') },
    { path: 'payment', title: '付款单', permission: 'fin:payment:query', doc: '05-付款.md', component: () => import('./views/PaymentList.vue') },
    { path: 'payment/new', title: '新建付款', permission: 'fin:payment:create', hidden: true, doc: '05-付款.md', component: () => import('./views/PaymentEdit.vue') },
    { path: 'payment/:id/edit', title: '编辑付款', permission: 'fin:payment:create', hidden: true, doc: '05-付款.md', component: () => import('./views/PaymentEdit.vue') },
    { path: 'payment/verify', title: '付款核销', permission: 'fin:payment:verify', doc: '05-付款.md', component: () => import('./views/VerifyPage.vue') },
    { path: 'payment/:id', title: '付款单详情', permission: 'fin:payment:query', hidden: true, doc: '05-付款.md', component: () => import('./views/PaymentDetail.vue') },
    { path: 'voucher', title: '凭证', permission: 'fin:voucher:query', doc: '06-凭证.md' },
    { path: 'cost', title: '成本核算', permission: 'fin:cost:query', doc: '07-成本核算.md' },
    { path: 'report', title: '财务报表', permission: 'fin:report:query', doc: '08-利润与报表.md', component: () => import('./views/ReportPage.vue') },
    { path: 'close', title: '月结', permission: 'fin:close:query', doc: '09-月结.md' },
    { path: 'setting', title: '财务设置', permission: 'fin:setting:query', doc: '01-财务基础设置.md', component: () => import('./views/SettingPage.vue') }
  ]
})
