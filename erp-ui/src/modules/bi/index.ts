import { defineModule } from '../types'

/** BI / AI 模块前端入口（需求 13-BI与AI） */
export default defineModule({
  code: 'bi',
  title: 'BI / AI',
  icon: 'DataAnalysis',
  order: 120,
  doc: '13-BI与AI',
  menus: [
    { path: 'dashboard', title: '经营驾驶舱', permission: 'bi:dashboard:view', doc: '02-经营驾驶舱.md', component: () => import('./views/DashboardPage.vue') },
    { path: 'sales', title: '销售分析', permission: 'bi:sales:view', doc: '03-专题分析.md', component: () => import('./views/SalesAnalysis.vue') },
    { path: 'purchase', title: '采购分析', permission: 'bi:purchase:view', doc: '03-专题分析.md', component: () => import('./views/PurchaseAnalysis.vue') },
    { path: 'inventory', title: '库存分析', permission: 'bi:inventory:view', doc: '03-专题分析.md', component: () => import('./views/InventoryAnalysis.vue') },
    { path: 'production', title: '生产分析', permission: 'bi:production:view', doc: '03-专题分析.md', component: () => import('./views/ProductionAnalysis.vue') },
    { path: 'quality', title: '品质分析', permission: 'bi:quality:view', doc: '03-专题分析.md', component: () => import('./views/QualityAnalysis.vue') },
    { path: 'finance', title: '财务分析', permission: 'bi:finance:view', doc: '03-专题分析.md', component: () => import('./views/FinanceAnalysis.vue') },
    { path: 'forecast', title: '销售预测建议', permission: 'bi:forecast:use', doc: '04-AI分析.md', component: () => import('./views/ForecastPage.vue') },
    { path: 'target', title: 'KPI 目标', permission: 'bi:target:manage', doc: '02-经营驾驶舱.md', component: () => import('./views/TargetPage.vue') },
    { path: 'ai', title: 'AI 分析', permission: 'ai:query:use', doc: '04-AI分析.md', component: () => import('./views/AiPage.vue') },
    { path: 'metric', title: '指标库', permission: 'bi:metric:manage', doc: '01-指标库与数据层.md', component: () => import('./views/MetricPage.vue') },
    { path: 'etl', title: '数据任务', permission: 'bi:metric:manage', doc: '01-指标库与数据层.md', component: () => import('./views/EtlPage.vue') }
  ]
})
