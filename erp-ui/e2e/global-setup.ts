import { request } from '@playwright/test'
import { adminToken } from './api'

/** 全新的 H2 库：把初始管理员密码改为测试密码，之后各用例直接登录 */
export default async function globalSetup() {
  const ctx = await request.newContext()
  try {
    await adminToken(ctx)
  } finally {
    await ctx.dispose()
  }
}
