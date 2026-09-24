import { defineModule } from '../types'

/**
 * CRM模块前端入口。菜单项未指定 component 时显示“开发中”占位页；
 * 开发某个页面时在 ./views 下新建组件并在此处填写 component 即可。
 */
export default defineModule({
  code: 'crm',
  title: 'CRM',
  icon: 'User',
  order: 20,
  doc: '03-CRM',
  menus: [
    { path: 'customer', title: '客户', permission: 'crm:customer:query', doc: '01-客户.md' },
    { path: 'contact', title: '联系人', permission: 'crm:customer:query', doc: '01-客户.md' },
    { path: 'customer-part', title: '客户料号', permission: 'crm:customer-part:query', doc: '02-客户料号对照.md' },
    { path: 'credit', title: '客户信用', permission: 'crm:credit:query', doc: '03-信用管理.md' },
    { path: 'followup', title: '跟进记录', permission: 'crm:followup:query', doc: '04-跟进记录.md' },
    { path: 'opportunity', title: '商机', permission: 'crm:opportunity:query', doc: '05-商机.md' }
  ]
})
