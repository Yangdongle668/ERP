import { request } from './http'

export interface LoginResp {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export interface CurrentUser {
  id: string
  username: string
  realName: string
  permissions: string[]
}

export const authApi = {
  login: (username: string, password: string) =>
    request<LoginResp>({ url: '/system/auth/login', method: 'post', data: { username, password } }),
  refresh: (refreshToken: string) =>
    request<LoginResp>({ url: '/system/auth/refresh', method: 'post', data: { refreshToken }, silent: true }),
  me: () => request<CurrentUser>({ url: '/system/auth/me', method: 'get' })
}
