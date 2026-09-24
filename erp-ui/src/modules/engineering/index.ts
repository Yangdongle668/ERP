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
  doc: '05-研发工程',
  menus: [
    { path: 'category', title: '物料类别', permission: 'eng:category:query', doc: '01-物料类别.md', component: () => import('./views/CategoryList.vue') },
    { path: 'material', title: '物料', permission: 'eng:material:query', doc: '02-物料.md', component: () => import('./views/MaterialList.vue') },
    { path: 'material/new', title: '新建物料', permission: 'eng:material:create', hidden: true, doc: '02-物料.md', component: () => import('./views/MaterialEdit.vue') },
    { path: 'material/:id/edit', title: '编辑物料', permission: 'eng:material:update', hidden: true, doc: '02-物料.md', component: () => import('./views/MaterialEdit.vue') },
    { path: 'material/:id', title: '物料详情', permission: 'eng:material:query', hidden: true, doc: '02-物料.md', component: () => import('./views/MaterialDetail.vue') },
    { path: 'bom', title: 'BOM', permission: 'eng:bom:query', doc: '03-BOM.md', component: () => import('./views/BomList.vue') },
    { path: 'bom/new', title: '新建 BOM', permission: 'eng:bom:create', hidden: true, doc: '03-BOM.md', component: () => import('./views/BomEdit.vue') },
    { path: 'bom/:id/edit', title: '编辑 BOM', permission: 'eng:bom:update', hidden: true, doc: '03-BOM.md', component: () => import('./views/BomEdit.vue') },
    { path: 'bom/:id', title: 'BOM 详情', permission: 'eng:bom:query', hidden: true, doc: '03-BOM.md', component: () => import('./views/BomDetail.vue') },
    { path: 'work-center', title: '工作中心', permission: 'eng:work-center:query', doc: '04-工作中心与工艺路线.md' },
    { path: 'routing', title: '工艺路线', permission: 'eng:routing:query', doc: '04-工作中心与工艺路线.md' },
    { path: 'ecn', title: 'ECN', permission: 'eng:ecn:query', doc: '05-ECN.md' },
    { path: 'project', title: '研发项目', permission: 'eng:project:query', doc: '06-研发项目.md' },
    { path: 'sample', title: '样品', permission: 'eng:sample:query', doc: '07-样品.md' },
    { path: 'tooling', title: '工装', permission: 'eng:tooling:query', doc: '08-工装.md' },
    { path: 'cert', title: '认证', permission: 'eng:cert:query', doc: '09-认证.md' }
  ]
})
