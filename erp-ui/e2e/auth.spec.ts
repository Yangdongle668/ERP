import { expect, test } from '@playwright/test'
import { ADMIN } from './api'
import { uiLogin } from './support'

test.describe('登录与退出', () => {
  test('密码错误：提示错误并停留在登录页', async ({ page }) => {
    await page.goto('/login')
    await page.getByPlaceholder('用户名').fill(ADMIN)
    await page.getByPlaceholder('密码').fill('wrong-password')
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page).toHaveURL(/login/)
    await expect(page.locator('.el-alert, .el-message').first()).toBeVisible()
  })

  test('未登录访问业务页面：跳转登录页', async ({ page }) => {
    await page.goto('/sales/order')
    await expect(page).toHaveURL(/login/)
  })

  test('登录 → 工作台 → 退出登录', async ({ page }) => {
    await uiLogin(page)
    await page.locator('.el-dropdown').filter({ hasText: /admin|管理员/i }).first().click().catch(() => undefined)
    await page.getByText('退出登录').first().click({ force: true })
    await expect(page).toHaveURL(/login/)
    // 退出后受保护页面不可直接访问
    await page.goto('/sales/order')
    await expect(page).toHaveURL(/login/)
  })
})
