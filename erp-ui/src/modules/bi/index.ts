import { defineModule } from '../types'

/**
 * BI / AI模块前端入口。菜单项未指定 component 时显示“开发中”占位页；
 * 开发某个页面时在 ./views 下新建组件并在此处填写 component 即可。
 */
export default defineModule({
  code: 'bi',
  title: 'BI / AI',
  icon: 'DataAnalysis',
  order: 120,
  doc: '13-BI与AI',
  menus: [
    { path: 'dashboard', title: '经营驾驶舱', permission: 'bi:dashboard:view', doc: '02-经营驾驶舱.md' },
    { path: 'sales', title: '销售分析', permission: 'bi:sales:view', doc: '03-专题分析.md' },
    { path: 'purchase', title: '采购分析', permission: 'bi:purchase:view', doc: '03-专题分析.md' },
    { path: 'inventory', title: '库存分析', permission: 'bi:inventory:view', doc: '03-专题分析.md' },
    { path: 'production', title: '生产分析', permission: 'bi:production:view', doc: '03-专题分析.md' },
    { path: 'quality', title: '品质分析', permission: 'bi:quality:view', doc: '03-专题分析.md' },
    { path: 'finance', title: '财务分析', permission: 'bi:finance:view', doc: '03-专题分析.md' },
    { path: 'ai', title: 'AI 分析', permission: 'ai:query:use', doc: '04-AI分析.md' },
    { path: 'metric', title: '指标库', permission: 'bi:metric:manage', doc: '01-指标库与数据层.md' },
    { path: 'etl', title: '数据任务', permission: 'bi:metric:manage', doc: '01-指标库与数据层.md' }
  ]
})
