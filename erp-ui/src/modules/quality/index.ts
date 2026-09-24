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
  doc: '10-品质',
  menus: [
    { path: 'standard', title: '检验基础数据', permission: 'qc:standard:query', doc: '01-检验基础数据.md' },
    { path: 'iqc', title: '来料检验 IQC', permission: 'qc:iqc:query', doc: '02-检验单.md' },
    { path: 'ipqc', title: '制程检验 IPQC', permission: 'qc:ipqc:query', doc: '02-检验单.md' },
    { path: 'fqc', title: '成品检验 FQC', permission: 'qc:fqc:query', doc: '02-检验单.md' },
    { path: 'oqc', title: '出货检验 OQC', permission: 'qc:oqc:query', doc: '02-检验单.md' },
    { path: 'return', title: '退货检验', permission: 'qc:return:query', doc: '02-检验单.md' },
    { path: 'ncr', title: 'NCR', permission: 'qc:ncr:query', doc: '03-NCR与MRB.md' },
    { path: 'capa', title: 'CAPA', permission: 'qc:capa:query', doc: '04-CAPA.md' },
    { path: 'complaint', title: '客诉', permission: 'qc:complaint:query', doc: '05-客诉.md' },
    { path: 'scar', title: 'SCAR', permission: 'qc:scar:query', doc: '06-SCAR.md' },
    { path: 'trace', title: '质量追溯', permission: 'qc:trace:query', doc: '07-质量追溯与报表.md' },
    { path: 'report', title: '质量报表', permission: 'qc:report:query', doc: '07-质量追溯与报表.md' }
  ]
})
