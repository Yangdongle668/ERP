import { defineModule } from '../types'

/**
 * 品质模块前端入口（需求 10-品质）：检验基础数据、检验单（IQC / IPQC / FQC / OQC / 退货检验）、NCR、CAPA、客诉、SCAR、质量追溯与报表。
 * 各类检验单共用 InspectionList（按类型过滤）与检验录入页；编辑、详情页 hidden。
 */
export default defineModule({
  code: 'quality',
  title: '品质',
  icon: 'CircleCheck',
  order: 90,
  doc: '10-品质',
  menus: [
    { path: 'standard', title: '检验基础数据', permission: 'qc:standard:query', doc: '01-检验基础数据.md', component: () => import('./views/StandardPage.vue') },
    { path: 'standard/:id', title: '检验标准', permission: 'qc:standard:query', hidden: true, doc: '01-检验基础数据.md', component: () => import('./views/StandardEdit.vue') },
    { path: 'iqc', title: '来料检验 IQC', permission: 'qc:iqc:query', doc: '02-检验单.md', component: () => import('./views/IqcList.vue') },
    { path: 'ipqc', title: '制程检验 IPQC', permission: 'qc:ipqc:query', doc: '02-检验单.md', component: () => import('./views/IpqcList.vue') },
    { path: 'fqc', title: '成品检验 FQC', permission: 'qc:fqc:query', doc: '02-检验单.md', component: () => import('./views/FqcList.vue') },
    { path: 'oqc', title: '出货检验 OQC', permission: 'qc:oqc:query', doc: '02-检验单.md', component: () => import('./views/OqcList.vue') },
    { path: 'return', title: '退货检验', permission: 'qc:return:query', doc: '02-检验单.md', component: () => import('./views/ReturnList.vue') },
    { path: 'inspection/:id', title: '检验录入', hidden: true, doc: '02-检验单.md', component: () => import('./views/InspectionEntry.vue') },
    { path: 'ncr', title: 'NCR', permission: 'qc:ncr:query', doc: '03-NCR与MRB.md', component: () => import('./views/NcrList.vue') },
    { path: 'ncr/:id/edit', title: '编辑 NCR', permission: 'qc:ncr:query', hidden: true, doc: '03-NCR与MRB.md', component: () => import('./views/NcrEdit.vue') },
    { path: 'ncr/:id', title: 'NCR 详情', permission: 'qc:ncr:query', hidden: true, doc: '03-NCR与MRB.md', component: () => import('./views/NcrDetail.vue') },
    { path: 'capa', title: 'CAPA', permission: 'qc:capa:query', doc: '04-CAPA.md', component: () => import('./views/CapaList.vue') },
    { path: 'capa/:id', title: 'CAPA / 8D', permission: 'qc:capa:query', hidden: true, doc: '04-CAPA.md', component: () => import('./views/CapaDetail.vue') },
    { path: 'complaint', title: '客诉', permission: 'qc:complaint:query', doc: '05-客诉.md', component: () => import('./views/ComplaintList.vue') },
    { path: 'complaint/:id/edit', title: '登记客诉', permission: 'qc:complaint:query', hidden: true, doc: '05-客诉.md', component: () => import('./views/ComplaintEdit.vue') },
    { path: 'complaint/:id', title: '客诉详情', permission: 'qc:complaint:query', hidden: true, doc: '05-客诉.md', component: () => import('./views/ComplaintDetail.vue') },
    { path: 'scar', title: 'SCAR', permission: 'qc:scar:query', doc: '06-SCAR.md', component: () => import('./views/ScarList.vue') },
    { path: 'scar/:id', title: 'SCAR 详情', permission: 'qc:scar:query', hidden: true, doc: '06-SCAR.md', component: () => import('./views/ScarDetail.vue') },
    { path: 'trace', title: '质量追溯', permission: 'qc:trace:query', doc: '07-质量追溯与报表.md', component: () => import('./views/TracePage.vue') },
    { path: 'report', title: '质量报表', permission: 'qc:report:query', doc: '07-质量追溯与报表.md', component: () => import('./views/ReportPage.vue') }
  ]
})
