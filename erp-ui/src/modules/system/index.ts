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
  doc: '01-系统管理.md',
  menus: [
    { path: 'user', title: '用户', permission: 'system:user:query' },
    { path: 'role', title: '角色', permission: 'system:role:query' },
    { path: 'org', title: '组织架构', permission: 'system:org:query' },
    { path: 'dict', title: '数据字典', permission: 'system:dict:query' },
    { path: 'code-rule', title: '编码规则', permission: 'system:code-rule:query' },
    { path: 'param', title: '系统参数', permission: 'system:param:query' },
    { path: 'log', title: '日志审计', permission: 'system:log:query' }
  ]
})
