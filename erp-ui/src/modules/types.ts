import type { Component } from 'vue'

/** 模块内的一个菜单页面。未提供 component 时显示“开发中”占位页。 */
export interface ModuleMenu {
  /** 相对模块的路径，最终路由为 /<模块code>/<path> */
  path: string
  title: string
  /** 访问该页面需要的权限标识，如 eng:material:query；不填表示登录即可访问 */
  permission?: string
  component?: () => Promise<{ default: Component }>
  /** 不在侧边栏显示（如详情页） */
  hidden?: boolean
  /** 该页面对应的功能需求文件名（位于模块需求目录下），如 02-物料.md */
  doc?: string
}

/** 业务模块定义。每个模块在 src/modules/<code>/index.ts 中默认导出一个 ModuleDefinition，框架自动注册。 */
export interface ModuleDefinition {
  /** 与后端模块编码一致，如 sales */
  code: string
  title: string
  /** Element Plus 图标组件名 */
  icon: string
  /** 侧边栏排序，越小越靠前 */
  order: number
  /** 需求文档目录名（位于 docs/requirements/ 下），如 05-研发工程；占位页中给出路径 */
  doc?: string
  menus: ModuleMenu[]
}

export function defineModule(def: ModuleDefinition): ModuleDefinition {
  return def
}
