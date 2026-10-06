import { defineModule } from '../types'

/**
 * 实时汇率模块前端入口（需求 16-实时汇率）：后台每 15 分钟获取中国银行现汇买入价（USD_CNY / EUR_CNY / EUR_USD），
 * 计算日平均、月平均汇率并自动写入系统汇率表，无需人工维护。
 */
export default defineModule({
  code: 'fx',
  title: '实时汇率',
  icon: 'Exchange',
  order: 125,
  doc: '16-实时汇率',
  menus: [
    { path: 'rates', title: '汇率看板', permission: 'fx:rate:query', doc: 'README.md', component: () => import('./views/FxPage.vue') }
  ]
})
