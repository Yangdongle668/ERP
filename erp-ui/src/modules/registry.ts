import type { ModuleDefinition } from './types'

/**
 * 自动收集 src/modules/<code>/index.ts 的模块定义。
 * 新增或修改模块只改动该模块自己的目录，不需要修改任何公共文件，避免并行开发时的合并冲突。
 */
const loaded = import.meta.glob<{ default: ModuleDefinition }>('./*/index.ts', { eager: true })

export const modules: ModuleDefinition[] = Object.values(loaded)
  .map((m) => m.default)
  .sort((a, b) => a.order - b.order)

const codes = new Set<string>()
for (const m of modules) {
  if (codes.has(m.code)) {
    throw new Error(`模块编码重复: ${m.code}`)
  }
  codes.add(m.code)
}
