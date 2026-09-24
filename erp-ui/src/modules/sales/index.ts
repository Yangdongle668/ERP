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
  doc: '04-销售.md',
  menus: [
    { path: 'rfq', title: 'RFQ', permission: 'sales:rfq:query' },
    { path: 'quotation', title: '报价', permission: 'sales:quotation:query' },
    { path: 'order', title: '销售订单', permission: 'sales:order:query' },
    { path: 'order-change', title: '订单变更', permission: 'sales:order:change' },
    { path: 'forecast', title: '销售预测', permission: 'sales:forecast:query' },
    { path: 'return', title: '销售退货', permission: 'sales:return:query' },
    { path: 'payment-plan', title: '回款', permission: 'sales:order:query' },
    { path: 'price-list', title: '价格表', permission: 'sales:price-list:query' }
  ]
})
