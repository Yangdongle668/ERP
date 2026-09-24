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
  doc: '03-CRM.md',
  menus: [
    { path: 'customer', title: '客户', permission: 'crm:customer:query' },
    { path: 'contact', title: '联系人', permission: 'crm:customer:query' },
    { path: 'opportunity', title: '商机', permission: 'crm:opportunity:query' },
    { path: 'followup', title: '跟进记录', permission: 'crm:followup:query' }
  ]
})
