import { expect, type Page } from '@playwright/test'
import { ADMIN, E2E_PASSWORD } from './api'

/** 通过登录页登录，等待进入工作台 */
export async function uiLogin(page: Page, username = ADMIN, password = E2E_PASSWORD) {
  await page.goto('/login')
  await page.getByPlaceholder('用户名').fill(username)
  await page.getByPlaceholder('密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/workbench\/home/)
}

/** 收集页面运行时错误与失败的接口请求（忽略 401 刷新与静默探测） */
export function watchProblems(page: Page) {
  const problems: string[] = []
  page.on('pageerror', (e) => problems.push(`pageerror: ${e.message}`))
  page.on('response', (r) => {
    const u = r.url()
    if (u.includes('/api/') && r.status() >= 500) problems.push(`${r.status()} ${u}`)
  })
  return problems
}
