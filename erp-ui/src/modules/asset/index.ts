import { defineModule } from '../types'

/**
 * 固定资产模块前端入口（需求 15-固定资产）：资产台账，资产编码按《编码规则管理制度》5.4 自动生成，
 * 如 LD1-PD-CPJ-264-001（广东蓝电 · 生产专用设备 · 冲片机 · 26 年 4 月 · 001 号）。
 */
export default defineModule({
  code: 'asset',
  title: '固定资产',
  icon: 'Asset',
  order: 130,
  doc: '15-固定资产',
  menus: [
    { path: 'assets', title: '资产台账', permission: 'ast:asset:query', doc: 'README.md', component: () => import('./views/AssetList.vue') }
  ]
})
