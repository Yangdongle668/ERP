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
    { path: 'work-center', title: '工作中心', permission: 'eng:work-center:query', doc: '04-工作中心与工艺路线.md', component: () => import('./views/WorkCenterList.vue') },
    { path: 'routing', title: '工艺路线', permission: 'eng:routing:query', doc: '04-工作中心与工艺路线.md', component: () => import('./views/RoutingList.vue') },
    { path: 'routing/new', title: '新建工艺路线', permission: 'eng:routing:create', hidden: true, doc: '04-工作中心与工艺路线.md', component: () => import('./views/RoutingEdit.vue') },
    { path: 'routing/:id/edit', title: '编辑工艺路线', permission: 'eng:routing:update', hidden: true, doc: '04-工作中心与工艺路线.md', component: () => import('./views/RoutingEdit.vue') },
    { path: 'routing/:id', title: '工艺路线详情', permission: 'eng:routing:query', hidden: true, doc: '04-工作中心与工艺路线.md', component: () => import('./views/RoutingDetail.vue') },
    { path: 'ecn', title: 'ECN', permission: 'eng:ecn:query', doc: '05-ECN.md', component: () => import('./views/EcnList.vue') },
    { path: 'ecn/new', title: '新建 ECN', permission: 'eng:ecn:create', hidden: true, doc: '05-ECN.md', component: () => import('./views/EcnEdit.vue') },
    { path: 'ecn/:id/edit', title: '编辑 ECN', permission: 'eng:ecn:update', hidden: true, doc: '05-ECN.md', component: () => import('./views/EcnEdit.vue') },
    { path: 'ecn/:id', title: 'ECN 详情', permission: 'eng:ecn:query', hidden: true, doc: '05-ECN.md', component: () => import('./views/EcnDetail.vue') },
    { path: 'project', title: '研发项目', permission: 'eng:project:query', doc: '06-研发项目.md', component: () => import('./views/ProjectList.vue') },
    { path: 'project/new', title: '新建项目', permission: 'eng:project:create', hidden: true, doc: '06-研发项目.md', component: () => import('./views/ProjectEdit.vue') },
    { path: 'project/:id/edit', title: '编辑项目', permission: 'eng:project:query', hidden: true, doc: '06-研发项目.md', component: () => import('./views/ProjectEdit.vue') },
    { path: 'project/:id', title: '项目详情', permission: 'eng:project:query', hidden: true, doc: '06-研发项目.md', component: () => import('./views/ProjectDetail.vue') },
    { path: 'sample', title: '样品', permission: 'eng:sample:query', doc: '07-样品.md', component: () => import('./views/SampleList.vue') },
    { path: 'sample/new', title: '新建样品单', permission: 'eng:sample:create', hidden: true, doc: '07-样品.md', component: () => import('./views/SampleEdit.vue') },
    { path: 'sample/:id/edit', title: '编辑样品单', permission: 'eng:sample:update', hidden: true, doc: '07-样品.md', component: () => import('./views/SampleEdit.vue') },
    { path: 'sample/:id', title: '样品单详情', permission: 'eng:sample:query', hidden: true, doc: '07-样品.md', component: () => import('./views/SampleDetail.vue') },
    { path: 'tooling', title: '工装', permission: 'eng:tooling:query', doc: '08-工装.md', component: () => import('./views/ToolingList.vue') },
    { path: 'tooling/:id', title: '工装详情', permission: 'eng:tooling:query', hidden: true, doc: '08-工装.md', component: () => import('./views/ToolingDetail.vue') },
    { path: 'cert', title: '认证', permission: 'eng:cert:query', doc: '09-认证.md', component: () => import('./views/CertList.vue') }
  ]
})
