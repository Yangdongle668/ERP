import { defineModule } from '../types'

/**
 * 财务模块前端入口。菜单项未指定 component 时显示“开发中”占位页；
 * 开发某个页面时在 ./views 下新建组件并在此处填写 component 即可。
 */
export default defineModule({
  code: 'finance',
  title: '财务',
  icon: 'Money',
  order: 110,
  doc: '12-财务.md',
  menus: [
    { path: 'receivable', title: '应收', permission: 'fin:receivable:query' },
    { path: 'receipt', title: '收款', permission: 'fin:receipt:query' },
    { path: 'payable', title: '应付', permission: 'fin:payable:query' },
    { path: 'payment', title: '付款', permission: 'fin:payment:query' },
    { path: 'voucher', title: '凭证', permission: 'fin:voucher:query' },
    { path: 'cost', title: '成本', permission: 'fin:cost:query' },
    { path: 'profit', title: '利润', permission: 'fin:profit:query' }
  ]
})
