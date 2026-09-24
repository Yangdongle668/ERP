import { defineModule } from '../types'

/**
 * 销售模块前端入口。菜单项未指定 component 时显示“开发中”占位页；
 * 开发某个页面时在 ./views 下新建组件并在此处填写 component 即可。
 */
export default defineModule({
  code: 'sales',
  title: '销售',
  icon: 'Sell',
  order: 30,
  doc: '04-销售',
  menus: [
    { path: 'price-list', title: '价格表', permission: 'sales:price-list:query', doc: '01-价格表.md' },
    { path: 'rfq', title: 'RFQ', permission: 'sales:rfq:query', doc: '02-RFQ与报价.md' },
    { path: 'quotation', title: '报价单', permission: 'sales:quotation:query', doc: '02-RFQ与报价.md' },
    { path: 'order', title: '销售订单', permission: 'sales:order:query', doc: '03-销售订单.md' },
    { path: 'order-change', title: '订单变更', permission: 'sales:order:change', doc: '04-订单变更.md' },
    { path: 'forecast', title: '销售预测', permission: 'sales:forecast:query', doc: '05-销售预测.md' },
    { path: 'return', title: '销售退货', permission: 'sales:return:query', doc: '06-销售退货.md' },
    { path: 'payment-plan', title: '回款跟踪', permission: 'sales:order:query', doc: '07-回款跟踪.md' },
    { path: 'report', title: '销售报表', permission: 'sales:report:query', doc: '08-销售报表.md' }
  ]
})
