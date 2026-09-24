import { defineModule } from '../types'

/**
 * 研发工程模块前端入口。菜单项未指定 component 时显示“开发中”占位页；
 * 开发某个页面时在 ./views 下新建组件并在此处填写 component 即可。
 */
export default defineModule({
  code: 'engineering',
  title: '研发工程',
  icon: 'Opportunity',
  order: 40,
  doc: '05-研发工程.md',
  menus: [
    { path: 'project', title: '项目', permission: 'eng:project:query' },
    { path: 'material', title: '物料', permission: 'eng:material:query', component: () => import('./views/MaterialList.vue') },
    { path: 'bom', title: 'BOM', permission: 'eng:bom:query' },
    { path: 'routing', title: '工艺路线', permission: 'eng:routing:query' },
    { path: 'work-center', title: '工作中心', permission: 'eng:work-center:query' },
    { path: 'ecn', title: 'ECN', permission: 'eng:ecn:query' },
    { path: 'sample', title: '样品', permission: 'eng:sample:query' },
    { path: 'tooling', title: '工装', permission: 'eng:tooling:query' },
    { path: 'cert', title: '认证', permission: 'eng:cert:query' }
  ]
})
