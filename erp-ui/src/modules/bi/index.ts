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
  doc: '13-BI与AI.md',
  menus: [
    { path: 'dashboard', title: '经营分析', permission: 'bi:dashboard:view' },
    { path: 'sales', title: '销售分析', permission: 'bi:sales:view' },
    { path: 'purchase', title: '采购分析', permission: 'bi:purchase:view' },
    { path: 'inventory', title: '库存分析', permission: 'bi:inventory:view' },
    { path: 'production', title: '生产分析', permission: 'bi:production:view' },
    { path: 'quality', title: '品质分析', permission: 'bi:quality:view' },
    { path: 'ai', title: 'AI分析', permission: 'ai:query:use' }
  ]
})
