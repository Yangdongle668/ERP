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
  doc: '07-资材',
  menus: [
    { path: 'supplier', title: '供应商', permission: 'pur:supplier:query', doc: '01-供应商.md' },
    { path: 'price', title: '采购价格', permission: 'pur:price:query', doc: '02-采购价格.md' },
    { path: 'requisition', title: '采购申请', permission: 'pur:requisition:query', doc: '03-采购申请.md' },
    { path: 'rfq', title: '询价比价', permission: 'pur:rfq:query', doc: '04-询价比价.md' },
    { path: 'order', title: '采购订单', permission: 'pur:order:query', doc: '05-采购订单.md' },
    { path: 'receipt', title: '到货', permission: 'pur:receipt:query', doc: '06-到货.md' },
    { path: 'outsourcing', title: '委外加工', permission: 'pur:outsourcing:query', doc: '07-委外加工.md' },
    { path: 'return', title: '采购退货', permission: 'pur:return:query', doc: '08-采购退货.md' },
    { path: 'statement', title: '供应商对账', permission: 'pur:statement:query', doc: '09-供应商对账.md' },
    { path: 'score', title: '供应商评估', permission: 'pur:score:query', doc: '10-供应商评估.md' },
    { path: 'report', title: '采购报表', permission: 'pur:report:query', doc: '11-采购报表.md' }
  ]
})
