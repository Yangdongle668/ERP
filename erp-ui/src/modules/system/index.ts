import { defineModule } from '../types'

/**
 * 系统管理模块前端入口。菜单项未指定 component 时显示“开发中”占位页；
 * 开发某个页面时在 ./views 下新建组件并在此处填写 component 即可。
 */
export default defineModule({
  code: 'system',
  title: '系统管理',
  icon: 'Setting',
  order: 900,
  doc: '01-系统管理',
  menus: [
    { path: 'org', title: '组织架构', permission: 'system:org:query', doc: '01-组织架构.md', component: () => import('./views/OrgList.vue') },
    { path: 'user', title: '用户', permission: 'system:user:query', doc: '02-用户管理.md', component: () => import('./views/UserList.vue') },
    { path: 'user/new', title: '新建用户', permission: 'system:user:create', hidden: true, component: () => import('./views/UserEdit.vue'), doc: '02-用户管理.md' },
    { path: 'user/:id/edit', title: '编辑用户', permission: 'system:user:update', hidden: true, component: () => import('./views/UserEdit.vue'), doc: '02-用户管理.md' },
    { path: 'role', title: '角色', permission: 'system:role:query', doc: '03-角色与权限.md', component: () => import('./views/RoleList.vue') },
    { path: 'dict', title: '数据字典', permission: 'system:dict:query', doc: '04-数据字典.md', component: () => import('./views/DictList.vue') },
    { path: 'code-rule', title: '编码规则', permission: 'system:code-rule:query', doc: '05-编码规则.md', component: () => import('./views/CodeRuleList.vue') },
    { path: 'uom', title: '计量单位', permission: 'system:uom:query', doc: '06-计量单位.md', component: () => import('./views/UomList.vue') },
    { path: 'currency', title: '币别汇率', permission: 'system:currency:query', doc: '07-币别汇率.md', component: () => import('./views/CurrencyPage.vue') },
    { path: 'payment-term', title: '付款条件', permission: 'system:payment-term:query', doc: '14-付款条件与贸易基础数据.md', component: () => import('./views/PaymentTermList.vue') },
    { path: 'workflow', title: '审批流', permission: 'system:workflow:query', doc: '08-审批流.md' },
    { path: 'workflow-instance', title: '审批监控', permission: 'system:workflow:monitor', doc: '08-审批流.md' },
    { path: 'print-template', title: '打印模板', permission: 'system:print:query', doc: '09-打印模板.md' },
    { path: 'param', title: '系统参数', permission: 'system:param:query', doc: '10-系统参数.md', component: () => import('./views/ParamPage.vue') },
    { path: 'log', title: '日志审计', permission: 'system:log:query', doc: '11-日志审计.md', component: () => import('./views/LogPage.vue') },
    { path: 'job', title: '定时任务', permission: 'system:job:query', doc: '12-附件与任务中心.md', component: () => import('./views/JobList.vue') },
    { path: 'task', title: '任务中心', doc: '12-附件与任务中心.md', component: () => import('./views/TaskCenter.vue') }
  ]
})
