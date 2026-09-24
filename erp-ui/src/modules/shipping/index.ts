import { defineModule } from '../types'

/**
 * 出货模块前端入口。菜单项未指定 component 时显示“开发中”占位页；
 * 开发某个页面时在 ./views 下新建组件并在此处填写 component 即可。
 */
export default defineModule({
  code: 'shipping',
  title: '出货',
  icon: 'Van',
  order: 100,
  doc: '11-出货.md',
  menus: [
    { path: 'notice', title: '出货通知', permission: 'shp:notice:query' },
    { path: 'picking', title: '拣货单', permission: 'shp:picking:query' },
    { path: 'packing', title: '装箱', permission: 'shp:packing:query' },
    { path: 'packing-list', title: 'Packing List', permission: 'shp:packing-list:query' },
    { path: 'invoice', title: 'Invoice', permission: 'shp:invoice:query' },
    { path: 'shipment', title: '出货单', permission: 'shp:shipment:query' },
    { path: 'customs', title: '报关', permission: 'shp:customs:query' }
  ]
})
