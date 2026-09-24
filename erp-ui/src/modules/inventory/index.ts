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
  doc: '08-仓库.md',
  menus: [
    { path: 'stock', title: '库存查询', permission: 'inv:stock:query' },
    { path: 'warehouse', title: '仓库管理', permission: 'inv:warehouse:query' },
    { path: 'in', title: '入库', permission: 'inv:in:query' },
    { path: 'out', title: '出库', permission: 'inv:out:query' },
    { path: 'transfer', title: '调拨', permission: 'inv:transfer:query' },
    { path: 'count', title: '盘点', permission: 'inv:count:query' },
    { path: 'batch', title: '批次', permission: 'inv:stock:query' },
    { path: 'analysis', title: '库存分析', permission: 'inv:stock:query' }
  ]
})
