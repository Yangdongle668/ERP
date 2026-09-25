import { defineModule } from '../types'

/** 仓库模块前端入口（需求 08-仓库）。入库单、出库单、调拨单的列表与详情共用 DocList / DocDetail，类型由路由决定。 */
const docList = () => import('./views/DocList.vue')
const docDetail = () => import('./views/DocDetail.vue')

export default defineModule({
  code: 'inventory',
  title: '仓库',
  icon: 'Box',
  order: 70,
  doc: '08-仓库',
  menus: [
    { path: 'stock', title: '库存查询', permission: 'inv:stock:query', doc: '08-库存查询与报表.md', component: () => import('./views/StockQuery.vue') },
    { path: 'in', title: '入库单', permission: 'inv:in:query', doc: '03-入库单.md', component: docList },
    { path: 'in/new', title: '新建其他入库', permission: 'inv:in:create', hidden: true, doc: '03-入库单.md', component: () => import('./views/StockInEdit.vue') },
    { path: 'in/:id/edit', title: '入库单', permission: 'inv:in:query', hidden: true, doc: '03-入库单.md', component: () => import('./views/StockInEdit.vue') },
    { path: 'in/:id', title: '入库单详情', permission: 'inv:in:query', hidden: true, doc: '03-入库单.md', component: docDetail },
    { path: 'out', title: '出库单', permission: 'inv:out:query', doc: '04-出库单.md', component: docList },
    { path: 'out/new', title: '新建其他出库', permission: 'inv:out:create', hidden: true, doc: '04-出库单.md', component: () => import('./views/StockOutEdit.vue') },
    { path: 'out/:id/edit', title: '出库单', permission: 'inv:out:query', hidden: true, doc: '04-出库单.md', component: () => import('./views/StockOutEdit.vue') },
    { path: 'out/:id', title: '出库单详情', permission: 'inv:out:query', hidden: true, doc: '04-出库单.md', component: docDetail },
    { path: 'transfer', title: '调拨单', permission: 'inv:transfer:query', doc: '05-调拨单.md', component: docList },
    { path: 'transfer/new', title: '新建调拨', permission: 'inv:transfer:create', hidden: true, doc: '05-调拨单.md', component: () => import('./views/TransferEdit.vue') },
    { path: 'transfer/:id/edit', title: '调拨单', permission: 'inv:transfer:query', hidden: true, doc: '05-调拨单.md', component: () => import('./views/TransferEdit.vue') },
    { path: 'transfer/:id', title: '调拨单详情', permission: 'inv:transfer:query', hidden: true, doc: '05-调拨单.md', component: docDetail },
    { path: 'count', title: '盘点', permission: 'inv:count:query', doc: '06-盘点.md', component: () => import('./views/CountList.vue') },
    { path: 'count/:id', title: '盘点单详情', permission: 'inv:count:query', hidden: true, doc: '06-盘点.md', component: () => import('./views/CountDetail.vue') },
    { path: 'batch', title: '批次与序列号', permission: 'inv:stock:query', doc: '07-批次与序列号.md', component: () => import('./views/BatchList.vue') },
    { path: 'analysis', title: '库存报表', permission: 'inv:stock:query', doc: '08-库存查询与报表.md', component: () => import('./views/Analysis.vue') },
    { path: 'warehouse', title: '仓库与库位', permission: 'inv:warehouse:query', doc: '01-仓库与库位.md', component: () => import('./views/WarehouseList.vue') },
    { path: 'period', title: '期初与月结', permission: 'inv:period:query', doc: '09-期初与月结.md', component: () => import('./views/PeriodList.vue') }
  ]
})
