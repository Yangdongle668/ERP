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
  doc: '11-出货',
  menus: [
    { path: 'notice', title: '出货通知', permission: 'shp:notice:query', doc: '01-出货通知.md' },
    { path: 'picking', title: '拣货', permission: 'shp:picking:query', doc: '02-拣货与装箱.md' },
    { path: 'packing', title: '装箱', permission: 'shp:packing:query', doc: '02-拣货与装箱.md' },
    { path: 'shipment', title: '出货单', permission: 'shp:shipment:query', doc: '03-出货单.md' },
    { path: 'packing-list', title: 'Packing List', permission: 'shp:document:query', doc: '04-出货单证.md' },
    { path: 'invoice', title: 'Invoice', permission: 'shp:document:query', doc: '04-出货单证.md' },
    { path: 'customs', title: '报关资料', permission: 'shp:document:query', doc: '04-出货单证.md' },
    { path: 'logistics', title: '物流跟踪', permission: 'shp:logistics:query', doc: '05-物流跟踪.md' },
    { path: 'forwarder', title: '货代', permission: 'shp:logistics:query', doc: '05-物流跟踪.md' },
    { path: 'report', title: '出货报表', permission: 'shp:report:query', doc: '06-出货报表.md' }
  ]
})
