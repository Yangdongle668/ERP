import type { APIRequestContext } from '@playwright/test'

/** 测试账号：初始管理员，首次登录强制改密后使用 E2E_PASSWORD */
export const ADMIN = 'admin'
export const INITIAL_PASSWORD = 'admin123'
export const E2E_PASSWORD = process.env.E2E_PASSWORD ?? 'E2e-Passw0rd!2026'
export const API = process.env.E2E_API ?? 'http://localhost:8080'

interface Result<T> { code: number; msg: string; data: T }

async function unwrap<T>(res: { ok(): boolean; status(): number; json(): Promise<unknown>; url(): string }): Promise<T> {
  const body = (await res.json()) as Result<T>
  if (!res.ok() || body.code !== 0) throw new Error(`${res.url()} → ${res.status()} ${body.code} ${body.msg}`)
  return body.data
}

export async function login(request: APIRequestContext, username: string, password: string): Promise<string> {
  const r = await request.post(`${API}/api/system/auth/login`, { data: { username, password } })
  return (await unwrap<{ accessToken: string }>(r)).accessToken
}

/** 管理员 token：H2 新库先用初始密码登录并改密；已改过则直接用新密码 */
export async function adminToken(request: APIRequestContext): Promise<string> {
  const first = await request.post(`${API}/api/system/auth/login`, { data: { username: ADMIN, password: E2E_PASSWORD } })
  const body = (await first.json()) as Result<{ accessToken: string }>
  if (body.code === 0) return body.data.accessToken
  const t = await login(request, ADMIN, INITIAL_PASSWORD)
  await unwrap(await request.post(`${API}/api/system/profile/change-password`, { headers: { Authorization: `Bearer ${t}` }, data: { oldPassword: INITIAL_PASSWORD, newPassword: E2E_PASSWORD } }))
  return login(request, ADMIN, E2E_PASSWORD)
}

/** 带登录令牌的接口客户端 */
export function client(request: APIRequestContext, token: string) {
  const headers = { Authorization: `Bearer ${token}` }
  return {
    get: async <T>(url: string) => unwrap<T>(await request.get(`${API}/api${url}`, { headers })),
    post: async <T>(url: string, data?: unknown) => unwrap<T>(await request.post(`${API}/api${url}`, { headers, data: data ?? {} })),
    put: async <T>(url: string, data?: unknown) => unwrap<T>(await request.put(`${API}/api${url}`, { headers, data: data ?? {} }))
  }
}
