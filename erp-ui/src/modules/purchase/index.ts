import { defineModule } from '../types'

/**
 * 资材模块前端入口。菜单项未指定 component 时显示“开发中”占位页；
 * 开发某个页面时在 ./views 下新建组件并在此处填写 component 即可。
 */
export default defineModule({
  code: 'purchase',
  title: '资材',
  icon: 'ShoppingCart',
  order: 60,
  doc: '07-资材采购.md',
  menus: [
    { path: 'supplier', title: '供应商', permission: 'pur:supplier:query' },
    { path: 'price', title: '供应商价格', permission: 'pur:price:query' },
    { path: 'requisition', title: '采购申请', permission: 'pur:requisition:query' },
    { path: 'rfq', title: '询价', permission: 'pur:rfq:query' },
    { path: 'order', title: '采购订单', permission: 'pur:order:query' },
    { path: 'receipt', title: '到货', permission: 'pur:receipt:query' },
    { path: 'outsourcing', title: '委外', permission: 'pur:outsourcing:query' },
    { path: 'return', title: '采购退货', permission: 'pur:return:query' },
    { path: 'statement', title: '对账', permission: 'pur:statement:query' },
    { path: 'score', title: '供应商评估', permission: 'pur:score:query' }
  ]
})
