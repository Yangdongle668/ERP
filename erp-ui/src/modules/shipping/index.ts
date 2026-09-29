import { defineModule } from '../types'

/**
 * 出货模块前端入口（需求 11-出货）：出货通知 → 拣货 → 装箱 / OQC → 出货单 → 单证 → 物流跟踪，以及出货报表。
 * 编辑、详情、拣货录入页 hidden。
 */
export default defineModule({
  code: 'shipping',
  title: '出货',
  icon: 'Van',
  order: 100,
  doc: '11-出货',
  menus: [
    { path: 'notice', title: '出货通知', permission: 'shp:notice:query', doc: '01-出货通知.md', component: () => import('./views/NoticeList.vue') },
    { path: 'notice/new', title: '新建出货通知', permission: 'shp:notice:create', hidden: true, doc: '01-出货通知.md', component: () => import('./views/NoticeEdit.vue') },
    { path: 'notice/:id/edit', title: '编辑出货通知', permission: 'shp:notice:update', hidden: true, doc: '01-出货通知.md', component: () => import('./views/NoticeEdit.vue') },
    { path: 'notice/:id', title: '出货通知详情', permission: 'shp:notice:query', hidden: true, doc: '01-出货通知.md', component: () => import('./views/NoticeDetail.vue') },
    { path: 'picking', title: '拣货单', permission: 'shp:picking:query', doc: '02-拣货与装箱.md', component: () => import('./views/PickingList.vue') },
    { path: 'picking/:id', title: '拣货', permission: 'shp:picking:query', hidden: true, doc: '02-拣货与装箱.md', component: () => import('./views/PickingEntry.vue') },
    { path: 'packing', title: '装箱', permission: 'shp:packing:query', doc: '02-拣货与装箱.md', component: () => import('./views/PackingPage.vue') },
    { path: 'shipment', title: '出货单', permission: 'shp:shipment:query', doc: '03-出货单.md', component: () => import('./views/ShipmentList.vue') },
    { path: 'shipment/:id', title: '出货单详情', permission: 'shp:shipment:query', hidden: true, doc: '03-出货单.md', component: () => import('./views/ShipmentDetail.vue') },
    { path: 'packing-list', title: 'Packing List', permission: 'shp:document:query', doc: '04-出货单证.md', component: () => import('./views/PackingListList.vue') },
    { path: 'packing-list/:id', title: 'Packing List', permission: 'shp:document:query', hidden: true, doc: '04-出货单证.md', component: () => import('./views/PackingListEdit.vue') },
    { path: 'invoice', title: 'Invoice', permission: 'shp:document:query', doc: '04-出货单证.md', component: () => import('./views/InvoiceList.vue') },
    { path: 'invoice/:id', title: 'Invoice', permission: 'shp:document:query', hidden: true, doc: '04-出货单证.md', component: () => import('./views/InvoiceEdit.vue') },
    { path: 'customs', title: '报关资料', permission: 'shp:document:query', doc: '04-出货单证.md', component: () => import('./views/CustomsList.vue') },
    { path: 'customs/:id', title: '报关资料', permission: 'shp:document:query', hidden: true, doc: '04-出货单证.md', component: () => import('./views/CustomsEdit.vue') },
    { path: 'logistics', title: '物流跟踪', permission: 'shp:logistics:query', doc: '05-物流跟踪.md', component: () => import('./views/LogisticsList.vue') },
    { path: 'forwarder', title: '货代', permission: 'shp:forwarder:manage', doc: '05-物流跟踪.md', component: () => import('./views/ForwarderList.vue') },
    { path: 'report', title: '出货报表', permission: 'shp:report:query', doc: '06-出货报表.md', component: () => import('./views/ReportPage.vue') }
  ]
})
