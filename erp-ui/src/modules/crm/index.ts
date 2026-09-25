import { defineModule } from '../types'

/** CRM 模块前端入口（需求 03-CRM）：客户、联系人、客户料号、客户信用、跟进记录、商机 */
export default defineModule({
  code: 'crm',
  title: 'CRM',
  icon: 'User',
  order: 20,
  doc: '03-CRM',
  menus: [
    { path: 'customer', title: '客户', permission: 'crm:customer:query', doc: '01-客户.md', component: () => import('./views/CustomerList.vue') },
    { path: 'customer/new', title: '新建客户', permission: 'crm:customer:create', hidden: true, doc: '01-客户.md', component: () => import('./views/CustomerEdit.vue') },
    { path: 'customer/:id/edit', title: '编辑客户', permission: 'crm:customer:update', hidden: true, doc: '01-客户.md', component: () => import('./views/CustomerEdit.vue') },
    { path: 'customer/:id', title: '客户详情', permission: 'crm:customer:query', hidden: true, doc: '01-客户.md', component: () => import('./views/CustomerDetail.vue') },
    { path: 'contact', title: '联系人', permission: 'crm:customer:query', doc: '01-客户.md', component: () => import('./views/ContactList.vue') },
    { path: 'customer-part', title: '客户料号', permission: 'crm:customer-part:query', doc: '02-客户料号对照.md', component: () => import('./views/CustomerPartList.vue') },
    { path: 'credit', title: '客户信用', permission: 'crm:credit:query', doc: '03-信用管理.md', component: () => import('./views/CreditList.vue') },
    { path: 'followup', title: '跟进记录', permission: 'crm:followup:query', doc: '04-跟进记录.md', component: () => import('./views/FollowupList.vue') },
    { path: 'opportunity', title: '商机', permission: 'crm:opportunity:query', doc: '05-商机.md', component: () => import('./views/OpportunityList.vue') }
  ]
})
