/**
 * 由可视化版式生成内置针式模板：erp-modules/<模块>/.../print-templates/<bizType>-zh-CN-dot.layout.json → 同名 .html
 * 用法：cd erp-ui && node scripts/gen-dot-templates.mjs（修改 .layout.json 或 src/utils/print/layoutTemplate.ts 后执行）
 */
import { build } from 'esbuild'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(here, '../..')
const out = path.join(here, '../node_modules/.cache/layoutTemplate.mjs')
fs.mkdirSync(path.dirname(out), { recursive: true })
await build({ entryPoints: [path.join(here, '../src/utils/print/layoutTemplate.ts')], bundle: true, format: 'esm', platform: 'node', outfile: out, logLevel: 'error' })
const { buildLayoutHtml } = await import(pathToFileURL(out).href + '?t=' + Date.now())

let n = 0
for (const mod of fs.readdirSync(path.join(root, 'erp-modules'))) {
  const dir = path.join(root, 'erp-modules', mod, `${mod}-biz`, 'src/main/resources/print-templates')
  if (!fs.existsSync(dir)) continue
  for (const f of fs.readdirSync(dir).filter((x) => x.endsWith('-dot.layout.json'))) {
    const layout = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'))
    fs.writeFileSync(path.join(dir, f.replace('.layout.json', '.html')), buildLayoutHtml(layout, true))
    n++
  }
}
console.log(`已生成 ${n} 个针式模板`)
