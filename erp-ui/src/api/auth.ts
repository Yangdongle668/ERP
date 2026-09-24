import { http, request } from './http'

export interface LoginResp {
  accessToken: string
  refreshToken: string
  expiresIn: number
  mustChangePassword: boolean
  passwordExpired: boolean
}

export interface CurrentUser {
  id: string
  username: string
  realName: string
  employeeNo?: string
  orgId?: string
  orgName?: string
  deptId?: string
  deptName?: string
  position?: string
  mobile?: string
  email?: string
  gender?: string
  language: string
  avatarFileId?: string
  roleNames: string[]
  permissions: string[]
  admin: boolean
  mustChangePassword: boolean
  passwordExpired: boolean
}

export interface PasswordPolicy {
  minLength: number
  /** NONE / LETTER_DIGIT / STRONG */
  complexity: string
  historyCount: number
}

export interface Captcha {
  captchaId: string
  image: string
}

export interface LoginReq {
  username: string
  password: string
  captchaId?: string
  captchaCode?: string
}

/** 登录失败时 BizError.data 中的附加信息 */
export interface LoginFailData {
  captchaRequired?: boolean
}

export const authApi = {
  login: (data: LoginReq) => request<LoginResp>({ url: '/system/auth/login', method: 'post', data, silent: true }),
  refresh: (refreshToken: string) =>
    request<LoginResp>({ url: '/system/auth/refresh', method: 'post', data: { refreshToken }, silent: true }),
  logout: () => request<void>({ url: '/system/auth/logout', method: 'post', silent: true }),
  me: () => http.get<CurrentUser>('/system/auth/me'),
  captcha: (username: string) => http.get<Captcha>('/system/auth/captcha', { username }),
  captchaRequired: (username: string) => http.get<boolean>('/system/auth/captcha-required', { username }, { silent: true }),
  passwordPolicy: () => http.get<PasswordPolicy>('/system/auth/password-policy', undefined, { silent: true }),
  updateProfile: (data: { mobile?: string; email?: string; language?: string; avatarFileId?: string }) =>
    http.put<void>('/system/profile', data),
  changePassword: (oldPassword: string, newPassword: string) =>
    http.post<LoginResp>('/system/profile/change-password', { oldPassword, newPassword })
}

/** 按密码策略检查（前端实时提示用，最终以后端校验为准） */
export function passwordChecks(pwd: string, policy: PasswordPolicy, username?: string) {
  const checks: { label: string; ok: boolean }[] = [{ label: `长度不少于 ${policy.minLength} 位`, ok: pwd.length >= policy.minLength }]
  if (policy.complexity === 'LETTER_DIGIT') {
    checks.push({ label: '同时包含字母和数字', ok: /[A-Za-z]/.test(pwd) && /\d/.test(pwd) })
  } else if (policy.complexity === 'STRONG') {
    const kinds = [/[A-Z]/, /[a-z]/, /\d/, /[^A-Za-z0-9]/].filter((r) => r.test(pwd)).length
    checks.push({ label: '包含大写字母、小写字母、数字、特殊字符中的至少三种', ok: kinds >= 3 })
  }
  if (username) checks.push({ label: '不包含用户名', ok: !pwd.toLowerCase().includes(username.toLowerCase()) })
  return checks
}
