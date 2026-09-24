import { defineModule } from '../types'

/**
 * 品质模块前端入口。菜单项未指定 component 时显示“开发中”占位页；
 * 开发某个页面时在 ./views 下新建组件并在此处填写 component 即可。
 */
export default defineModule({
  code: 'quality',
  title: '品质',
  icon: 'CircleCheck',
  order: 90,
  doc: '10-品质.md',
  menus: [
    { path: 'standard', title: '检验标准', permission: 'qc:standard:query' },
    { path: 'iqc', title: 'IQC', permission: 'qc:iqc:query' },
    { path: 'ipqc', title: 'IPQC', permission: 'qc:ipqc:query' },
    { path: 'fqc', title: 'FQC', permission: 'qc:fqc:query' },
    { path: 'oqc', title: 'OQC', permission: 'qc:oqc:query' },
    { path: 'ncr', title: 'NCR', permission: 'qc:ncr:query' },
    { path: 'capa', title: 'CAPA', permission: 'qc:capa:query' },
    { path: 'complaint', title: '客诉', permission: 'qc:complaint:query' },
    { path: 'scar', title: 'SCAR', permission: 'qc:scar:query' },
    { path: 'trace', title: '质量追溯', permission: 'qc:trace:query' }
  ]
})
