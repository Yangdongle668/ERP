/**
 * 专题分析页配置（需求 13-03）：KPI、图表、明细透视的指标与维度。页面只显示当前用户有权限的指标
 * （GET /bi/pages/{code}/config），数据均通过 POST /bi/query 获取。
 */
export type ChartDef =
  /** 趋势：近 months 个月，按月；seriesDim 时每个维度值一条线（取前 5） */
  | { type: 'line'; title: string; metrics: string[]; months: number; seriesDim?: string }
  /** 排名：按 dim 分组排序；pareto 显示累计占比 */
  | { type: 'bar'; title: string; metric: string; dim: string; limit: number; order?: 'asc' | 'desc'; pareto?: boolean; color?: number }
  /** 占比：前 7 项 + 其他 */
  | { type: 'donut'; title: string; metric: string; dim: string }

export type FilterKind = 'customer' | 'supplier' | 'material' | 'owner'

export interface TopicDef {
  code: string
  kpis: string[]
  charts: ChartDef[]
  filters: FilterKind[]
  pivot: { dims: string[]; drill: Record<string, string>; metrics: string[] }
}

export const TOPICS: Record<string, TopicDef> = {
  sales: {
    code: 'sales',
    kpis: ['sales_order_amount', 'sales_ship_amount', 'sales_receipt_amount', 'gross_margin', 'new_customer_count', 'on_time_delivery_rate'],
    charts: [
      { type: 'line', title: '月度接单 / 出货趋势', metrics: ['sales_order_amount', 'sales_ship_amount'], months: 12 },
      { type: 'bar', title: '客户 ABC 分析（出货额累计占比）', metric: 'sales_ship_amount', dim: 'customer', limit: 20, pareto: true },
      { type: 'bar', title: '国家 / 地区分布', metric: 'sales_ship_amount', dim: 'country', limit: 10, color: 2 },
      { type: 'bar', title: '业务员排名', metric: 'sales_ship_amount', dim: 'owner', limit: 10, color: 4 }
    ],
    filters: ['customer', 'owner', 'material'],
    pivot: {
      dims: ['customer', 'country', 'owner', 'dept', 'category', 'material'],
      drill: { category: 'material', material: 'customer', customer: 'material', owner: 'customer', dept: 'owner', country: 'customer' },
      metrics: ['sales_order_amount', 'sales_ship_amount', 'sales_return_amount', 'gross_profit', 'gross_margin']
    }
  },
  purchase: {
    code: 'purchase',
    kpis: ['purchase_amount', 'receipt_amount', 'supplier_count', 'supplier_on_time_rate', 'iqc_lot_pass_rate', 'purchase_avg_price'],
    charts: [
      { type: 'line', title: '月度采购额趋势', metrics: ['purchase_amount', 'receipt_amount'], months: 12 },
      { type: 'donut', title: '供应商集中度', metric: 'purchase_amount', dim: 'supplier' },
      { type: 'donut', title: '物料类别采购结构', metric: 'purchase_amount', dim: 'category' },
      { type: 'bar', title: '采购员排名', metric: 'purchase_amount', dim: 'owner', limit: 10, color: 4 }
    ],
    filters: ['supplier', 'material', 'owner'],
    pivot: {
      dims: ['supplier', 'category', 'material', 'owner'],
      drill: { category: 'material', material: 'supplier', supplier: 'material', owner: 'supplier' },
      metrics: ['purchase_amount', 'receipt_amount', 'supplier_on_time_rate', 'purchase_avg_price']
    }
  },
  inventory: {
    code: 'inventory',
    kpis: ['inventory_amount', 'inventory_turnover_days', 'slow_moving_amount', 'slow_moving_ratio', 'aged_amount'],
    charts: [
      { type: 'line', title: '近 12 个月库存金额（按仓库类型）', metrics: ['inventory_amount'], months: 12, seriesDim: 'warehouse_type' },
      { type: 'bar', title: '呆滞 Top20 物料', metric: 'slow_moving_amount', dim: 'material', limit: 20, color: 3 },
      { type: 'donut', title: '库存结构（仓库类型）', metric: 'inventory_amount', dim: 'warehouse_type' },
      { type: 'bar', title: '库龄 >180 天（类别）', metric: 'aged_amount', dim: 'category', limit: 10, color: 6 }
    ],
    filters: ['material'],
    pivot: {
      dims: ['warehouse_type', 'warehouse', 'category', 'material'],
      drill: { warehouse_type: 'warehouse', warehouse: 'material', category: 'material' },
      metrics: ['inventory_amount', 'slow_moving_amount', 'aged_amount']
    }
  },
  production: {
    code: 'production',
    kpis: ['production_output', 'plan_achievement_rate', 'fpy', 'yield_rate', 'work_hours', 'delayed_order_count'],
    charts: [
      { type: 'line', title: '月度产量趋势', metrics: ['production_output', 'plan_qty'], months: 12 },
      { type: 'bar', title: '车间良率对比', metric: 'yield_rate', dim: 'dept', limit: 10, color: 2 },
      { type: 'bar', title: '产品产量排名', metric: 'production_output', dim: 'material', limit: 10 },
      { type: 'bar', title: '车间延期订单', metric: 'delayed_order_count', dim: 'dept', limit: 10, color: 6 }
    ],
    filters: ['material'],
    pivot: {
      dims: ['dept', 'category', 'material'],
      drill: { dept: 'material', category: 'material' },
      metrics: ['production_output', 'yield_rate', 'fpy', 'work_hours']
    }
  },
  quality: {
    code: 'quality',
    kpis: ['iqc_lot_pass_rate', 'fqc_pass_rate', 'oqc_pass_rate', 'ncr_count', 'complaint_count'],
    charts: [
      { type: 'line', title: '合格率趋势', metrics: ['iqc_lot_pass_rate', 'fqc_pass_rate', 'oqc_pass_rate'], months: 12 },
      { type: 'bar', title: '供应商来料合格率（最低 10）', metric: 'iqc_lot_pass_rate', dim: 'supplier', limit: 10, order: 'asc', color: 6 },
      { type: 'bar', title: '客诉按客户分布', metric: 'complaint_count', dim: 'customer', limit: 10, color: 3 },
      { type: 'bar', title: 'NCR 按物料类别', metric: 'ncr_count', dim: 'category', limit: 10, color: 4 }
    ],
    filters: ['supplier', 'customer', 'material'],
    pivot: {
      dims: ['supplier', 'category', 'material', 'customer'],
      drill: { supplier: 'material', category: 'material', customer: 'material' },
      metrics: ['iqc_lot_pass_rate', 'iqc_lot_count', 'ncr_count', 'complaint_count']
    }
  },
  finance: {
    code: 'finance',
    kpis: ['sales_ship_amount', 'ship_cost', 'gross_margin', 'ar_balance', 'ar_overdue_ratio', 'dso', 'ap_balance', 'dpo'],
    charts: [
      { type: 'line', title: '收入 / 成本 / 毛利趋势', metrics: ['sales_ship_amount', 'ship_cost', 'gross_profit'], months: 12 },
      { type: 'bar', title: '应收余额 Top10 客户', metric: 'ar_balance', dim: 'customer', limit: 10, color: 3 },
      { type: 'bar', title: '产品毛利率', metric: 'gross_margin', dim: 'material', limit: 10, color: 2 },
      { type: 'bar', title: '应付余额 Top10 供应商', metric: 'ap_balance', dim: 'supplier', limit: 10, color: 4 }
    ],
    filters: ['customer'],
    pivot: {
      dims: ['customer', 'owner', 'dept'],
      drill: { dept: 'owner', owner: 'customer' },
      metrics: ['ar_balance', 'ar_overdue', 'ar_add_amount']
    }
  }
}
