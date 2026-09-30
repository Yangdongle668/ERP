import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { expect, test } from '@playwright/test'
import { uiLogin, watchProblems } from './support'

/** 从各模块 index.ts 中取出所有列表 / 首页菜单（不含带参数与“新建”页）的路由 */
function listRoutes(): string[] {
  const root = join(process.cwd(), 'src', 'modules')
  const routes: string[] = []
  for (const dir of readdirSync(root, { withFileTypes: true })) {
    if (!dir.isDirectory()) continue
    let src = ''
    try {
      src = readFileSync(join(root, dir.name, 'index.ts'), 'utf8')
    } catch {
      continue
    }
    const code = /code:\s*'([^']+)'/.exec(src)?.[1]
    if (!code) continue
    for (const m of src.matchAll(/\{\s*path:\s*'([^']+)'/g)) {
      const p = m[1]
      if (p.includes(':') || p.endsWith('/new') || p.includes('/new/') || /hidden:\s*true/.test(src.slice(m.index!, src.indexOf('}', m.index!)))) continue
      routes.push(`/${code}/${p}`)
    }
  }
  return routes
}

test('管理员打开所有菜单页面：无脚本错误、无 5xx 接口', async ({ page }) => {
  test.setTimeout(600_000)
  const routes = listRoutes()
  expect(routes.length).toBeGreaterThan(50)
  await uiLogin(page)
  const problems = watchProblems(page)
  const failed: string[] = []
  for (const r of routes) {
    problems.length = 0
    await page.goto(r)
    // 工作台有 SSE 长连接，不会出现 networkidle：固定留一点时间让页面接口返回
    await page.waitForTimeout(600)
    await expect(page.locator('section.el-container.layout')).toBeVisible()
    if (/\/login/.test(page.url())) failed.push(`${r} → 被重定向到登录页`)
    if (problems.length) failed.push(`${r} → ${problems.join('; ')}`)
  }
  expect(failed, failed.join('\n')).toEqual([])
})
