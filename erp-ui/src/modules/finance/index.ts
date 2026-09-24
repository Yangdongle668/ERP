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
  doc: '12-财务',
  menus: [
    { path: 'setting', title: '财务设置', permission: 'fin:setting:query', doc: '01-财务基础设置.md' },
    { path: 'receivable', title: '应收', permission: 'fin:receivable:query', doc: '02-应收.md' },
    { path: 'receipt', title: '收款与核销', permission: 'fin:receipt:query', doc: '03-收款与核销.md' },
    { path: 'payable', title: '应付与发票', permission: 'fin:payable:query', doc: '04-应付与发票.md' },
    { path: 'payment', title: '付款', permission: 'fin:payment:query', doc: '05-付款.md' },
    { path: 'voucher', title: '凭证', permission: 'fin:voucher:query', doc: '06-凭证.md' },
    { path: 'cost', title: '成本核算', permission: 'fin:cost:query', doc: '07-成本核算.md' },
    { path: 'report', title: '财务报表', permission: 'fin:report:query', doc: '08-利润与报表.md' },
    { path: 'close', title: '月结', permission: 'fin:close:query', doc: '09-月结.md' }
  ]
})
