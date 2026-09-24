import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

/** 后端统一响应体 */
export interface CommonResult<T> {
  code: number
  msg: string
  data: T
}

export interface PageResult<T> {
  list: T[]
  total: number
}

export interface PageParam {
  pageNo: number
  pageSize: number
}

/** 业务错误：code 非 0。调用方可 catch 后按 code 做特殊处理 */
export class BizError extends Error {
  constructor(public code: number, message: string) {
    super(message)
  }
}

const instance = axios.create({ baseURL: '/api', timeout: 30000 })

instance.interceptors.request.use((config) => {
  const store = useUserStore()
  if (store.accessToken) {
    config.headers.Authorization = `Bearer ${store.accessToken}`
  }
  return config
})

let refreshing: Promise<boolean> | null = null

async function tryRefresh(): Promise<boolean> {
  const store = useUserStore()
  if (!store.refreshToken) return false
  refreshing ??= store.refresh().finally(() => (refreshing = null))
  return refreshing
}

/**
 * 发送请求并解包 CommonResult。
 * - code = 0 返回 data
 * - 401 自动用 refreshToken 刷新一次后重试，失败则跳转登录
 * - 其他错误统一提示（silent = true 时不提示）
 */
export async function request<T>(config: AxiosRequestConfig & { silent?: boolean }, retried = false): Promise<T> {
  try {
    const resp = await instance.request<CommonResult<T>>(config)
    const body = resp.data
    if (body.code !== 0) {
      throw new BizError(body.code, body.msg)
    }
    return body.data
  } catch (e) {
    if (e instanceof AxiosError && e.response?.status === 401 && !retried && !config.url?.startsWith('/system/auth/')) {
      if (await tryRefresh()) {
        return request<T>(config, true)
      }
      useUserStore().clear()
      await router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    }
    const err = toBizError(e)
    if (!config.silent) {
      ElMessage.error(err.message)
    }
    throw err
  }
}

function toBizError(e: unknown): BizError {
  if (e instanceof BizError) return e
  if (e instanceof AxiosError) {
    const body = e.response?.data as Partial<CommonResult<unknown>> | undefined
    if (body && typeof body.code === 'number') return new BizError(body.code, body.msg ?? '请求失败')
    return new BizError(e.response?.status ?? -1, e.code === 'ECONNABORTED' ? '请求超时' : '网络异常，请稍后重试')
  }
  return new BizError(-1, String(e))
}

export const http = {
  get: <T>(url: string, params?: object) => request<T>({ url, method: 'get', params }),
  post: <T>(url: string, data?: object) => request<T>({ url, method: 'post', data }),
  put: <T>(url: string, data?: object) => request<T>({ url, method: 'put', data }),
  delete: <T>(url: string) => request<T>({ url, method: 'delete' })
}
