#!/usr/bin/env node
/**
 * 设计系统一致性检查（UI 设计规范第 2 节、第 10 节）。`npm run build` 前自动执行。
 *
 * 页面（src/modules、src/views）：
 *   - <style> 中不允许颜色字面量（#hex、rgb()、hsl()，阴影同理）、px 字号、font-family、数字字重、大于 10px 的圆角
 *     → 使用 var(--erp-*) token 或 global.css 中的工具类
 *   - 不允许行内 style 设置颜色 / 字号
 *   - modules/<code>/views 下的页面必须以 <ErpPage> 作为根组件
 * 全部源码：
 *   - 不允许 Emoji 作为界面图标
 *   - 不允许直接引入 @element-plus/icons-vue（图标统一使用 components/icons.ts 中的 Lucide 映射）
 * 公共组件（src/components、src/layout）：不允许颜色字面量。
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

const root = new URL('../src/', import.meta.url).pathname
const errors = []

function walk(dir) {
  return readdirSync(dir).flatMap((n) => {
    const p = join(dir, n)
    return statSync(p).isDirectory() ? walk(p) : [p]
  })
}

const EMOJI = /[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{1F000}-\u{1F2FF}]/u
const COLOR = /#[0-9a-fA-F]{3,8}\b|\brgba?\(|\bhsla?\(/
const styleBlocks = (src) => [...src.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)].map((m) => ({ css: m[1], offset: m.index }))
const lineOf = (src, index) => src.slice(0, index).split('\n').length

for (const file of walk(root)) {
  if (!/\.(vue|ts)$/.test(file)) continue
  const rel = relative(root, file)
  const src = readFileSync(file, 'utf8')
  const report = (index, msg) => errors.push(`src/${rel}:${lineOf(src, index)}  ${msg}`)

  if (/@element-plus\/icons-vue/.test(src) && !rel.startsWith('components/icons')) report(src.search(/@element-plus\/icons-vue/), '不要直接引入 @element-plus/icons-vue，使用 components/icons.ts 注册的图标名')
  const emoji = src.search(EMOJI)
  if (emoji >= 0) report(emoji, '不允许使用 Emoji 作为界面元素')
  if (!file.endsWith('.vue')) continue

  const isPage = rel.startsWith('modules/') || rel.startsWith('views/')
  const isShared = rel.startsWith('components/') || rel.startsWith('layout/')

  for (const { css, offset } of styleBlocks(src)) {
    const base = offset + src.slice(offset).indexOf(css)
    css.split('\n').reduce((pos, line) => {
      const at = base + pos
      const code = line.replace(/\/\*.*?\*\//g, '')
      if (isPage || isShared) {
        if (COLOR.test(code)) report(at, `颜色请使用 token：${line.trim()}`)
      }
      if (isPage) {
        if (/font-size\s*:\s*\d/.test(code)) report(at, `字号请使用 --erp-font-size-*：${line.trim()}`)
        if (/font-family\s*:\s*(?!\s|var\(--erp-|inherit)/.test(code)) report(at, `不要在页面中设置字体：${line.trim()}`)
        if (/font-weight\s*:\s*\d/.test(code)) report(at, `字重请使用 --erp-font-weight-*：${line.trim()}`)
        const r = /border-radius\s*:\s*(\d+)px/.exec(code)
        if (r && Number(r[1]) > 10) report(at, `圆角不超过 10px，请使用 --erp-radius-*：${line.trim()}`)
      }
      return pos + line.length + 1
    }, 0)
  }

  if (isPage) {
    const tpl = /<template>([\s\S]*)<\/template>/.exec(src)
    if (tpl && /\sstyle="[^"]*(color|font-size)\s*:/.test(tpl[1])) report(src.search(/\sstyle="[^"]*(color|font-size)\s*:/), '不要用行内 style 设置颜色或字号')
    if (/^modules\/[^/]+\/views\//.test(rel) && tpl && !/<ErpPage[\s>]/.test(tpl[1])) report(0, '页面必须使用 <ErpPage> 作为根组件')
  }
}

if (errors.length) {
  console.error(`设计系统检查未通过（${errors.length} 处），详见 docs/ui/UI设计规范.md 第 2 节：\n` + errors.map((e) => '  ' + e).join('\n'))
  process.exit(1)
}
console.log('设计系统检查通过')
