import { defineModule } from '../types'

/**
 * 仓库模块前端入口。菜单项未指定 component 时显示“开发中”占位页；
 * 开发某个页面时在 ./views 下新建组件并在此处填写 component 即可。
 */
export default defineModule({
  code: 'inventory',
  title: '仓库',
  icon: 'Box',
  order: 70,
  doc: '08-仓库',
  menus: [
    { path: 'warehouse', title: '仓库与库位', permission: 'inv:warehouse:query', doc: '01-仓库与库位.md' },
    { path: 'in', title: '入库单', permission: 'inv:in:query', doc: '03-入库单.md' },
    { path: 'out', title: '出库单', permission: 'inv:out:query', doc: '04-出库单.md' },
    { path: 'transfer', title: '调拨单', permission: 'inv:transfer:query', doc: '05-调拨单.md' },
    { path: 'count', title: '盘点', permission: 'inv:count:query', doc: '06-盘点.md' },
    { path: 'batch', title: '批次查询', permission: 'inv:stock:query', doc: '07-批次与序列号.md' },
    { path: 'stock', title: '库存查询', permission: 'inv:stock:query', doc: '08-库存查询与报表.md' },
    { path: 'analysis', title: '库存报表', permission: 'inv:stock:query', doc: '08-库存查询与报表.md' },
    { path: 'period', title: '期初与月结', permission: 'inv:period:query', doc: '09-期初与月结.md' }
  ]
})
