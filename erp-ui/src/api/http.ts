import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
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

/** 与后端 GlobalErrorCodes 对应的错误码 */
export const ErrorCodes = {
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  CONCURRENT_MODIFICATION: 1_000_000_001,
  PASSWORD_CHANGE_REQUIRED: 1_000_000_004
} as const

/** 业务错误：code 非 0。调用方可 catch 后按 code 做特殊处理 */
export class BizError extends Error {
  constructor(public code: number, message: string, public httpStatus?: number, public traceId?: string, public data?: unknown) {
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

/** 并发修改提示只弹一次，避免多个请求同时失败时叠加弹窗 */
let concurrentDialogOpen = false

/** 数据已被他人修改时，页面可订阅此事件重新加载；未订阅时刷新当前页签 */
export const CONCURRENT_EVENT = 'erp:concurrent-modification'

export type RequestOptions = AxiosRequestConfig & {
  /** true 时不做任何错误提示，由调用方处理 */
  silent?: boolean
}

/**
 * 发送请求并解包 CommonResult（UI 设计规范 6.3 消息反馈）：
 * - code = 0 返回 data
 * - 401 自动用 refreshToken 刷新一次后重试，失败则跳转登录，登录后回到原页面
 * - 403 提示“没有该操作权限”；需要修改密码时跳转修改密码页
 * - 500 弹窗显示追踪号
 * - 并发修改询问是否刷新
 * - 其他业务错误显示后端 msg
 */
export async function request<T>(config: RequestOptions, retried = false): Promise<T> {
  try {
    const resp = await instance.request<CommonResult<T>>(config)
    const body = resp.data
    if (body.code !== 0) {
      throw new BizError(body.code, body.msg, resp.status, resp.headers['x-trace-id'], body.data)
    }
    return body.data
  } catch (e) {
    if (e instanceof AxiosError && e.response?.status === 401 && !retried && !config.url?.startsWith('/system/auth/login')) {
      if (await tryRefresh()) {
        return request<T>(config, true)
      }
      useUserStore().clear()
      await router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    }
    const err = toBizError(e)
    if (!config.silent) {
      notifyError(err)
    }
    throw err
  }
}

function notifyError(err: BizError) {
  if (err.code === ErrorCodes.PASSWORD_CHANGE_REQUIRED) {
    router.push('/change-password')
    return
  }
  if (err.httpStatus === 401) return // 已跳转登录页
  if (err.httpStatus === 403 || err.code === ErrorCodes.FORBIDDEN) {
    ElMessage.warning({ message: err.message || '没有该操作权限', duration: 3000 })
    return
  }
  if (err.code === ErrorCodes.CONCURRENT_MODIFICATION) {
    if (concurrentDialogOpen) return
    concurrentDialogOpen = true
    ElMessageBox.confirm('数据已被他人修改，是否刷新？', '提示', { type: 'warning', confirmButtonText: '刷新' })
      .then(() => window.dispatchEvent(new CustomEvent(CONCURRENT_EVENT)))
      .catch(() => undefined)
      .finally(() => (concurrentDialogOpen = false))
    return
  }
  if (err.httpStatus !== undefined && err.httpStatus >= 500) {
    const trace = err.traceId ? `追踪号：${err.traceId}` : ''
    ElMessageBox.alert(`系统异常，请联系管理员。${trace}`, '系统异常', {
      type: 'error',
      confirmButtonText: err.traceId ? '复制追踪号' : '确定',
      callback: () => {
        if (err.traceId) navigator.clipboard?.writeText(err.traceId).catch(() => undefined)
      }
    })
    return
  }
  ElMessage.error({ message: err.message, duration: 4000 })
}

function toBizError(e: unknown): BizError {
  if (e instanceof BizError) return e
  if (e instanceof AxiosError) {
    const status = e.response?.status
    const traceId = e.response?.headers?.['x-trace-id'] as string | undefined
    const body = e.response?.data as Partial<CommonResult<unknown>> | undefined
    if (body && typeof body.code === 'number') return new BizError(body.code, body.msg ?? '请求失败', status, traceId, body.data)
    if (status && status >= 500) return new BizError(500, '系统异常，请联系管理员', status, traceId)
    return new BizError(status ?? -1, e.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '网络异常，请稍后重试', status, traceId)
  }
  return new BizError(-1, String(e))
}

export const http = {
  get: <T>(url: string, params?: object, opts?: RequestOptions) => request<T>({ url, method: 'get', params, ...opts }),
  post: <T>(url: string, data?: object, opts?: RequestOptions) => request<T>({ url, method: 'post', data, ...opts }),
  put: <T>(url: string, data?: object, opts?: RequestOptions) => request<T>({ url, method: 'put', data, ...opts }),
  delete: <T>(url: string, opts?: RequestOptions) => request<T>({ url, method: 'delete', ...opts })
}

/** 上传文件（multipart），返回解包后的 data */
export function upload<T>(url: string, file: File | Blob, fields: Record<string, string> = {}, onProgress?: (percent: number) => void,
                          opts?: RequestOptions): Promise<T> {
  const form = new FormData()
  form.append('file', file)
  for (const [k, v] of Object.entries(fields)) form.append(k, v)
  return request<T>({
    url, method: 'post', data: form, timeout: 300000,
    onUploadProgress: (ev) => onProgress?.(ev.total ? Math.round((ev.loaded / ev.total) * 100) : 0),
    ...opts
  })
}

/**
 * 下载文件。响应为 JSON 时按 CommonResult 处理（如后端返回“已转为后台导出”），返回其 data；
 * 响应为文件时触发浏览器下载，返回 undefined。
 */
export async function download<T = unknown>(url: string, params?: object, fallbackName = 'download'): Promise<T | undefined> {
  const resp = await requestRaw({ url, method: 'get', params, responseType: 'blob', timeout: 300000 })
  const type = String(resp.headers['content-type'] ?? '')
  if (type.includes('application/json')) {
    const body = JSON.parse(await (resp.data as Blob).text()) as CommonResult<T>
    if (body.code !== 0) {
      const err = new BizError(body.code, body.msg)
      notifyError(err)
      throw err
    }
    return body.data
  }
  saveBlob(resp.data as Blob, filenameOf(String(resp.headers['content-disposition'] ?? '')) ?? fallbackName)
  return undefined
}

/** POST 并下载返回的文件（如导入错误报告：上传原文件，返回带“错误原因”列的文件） */
export async function downloadPost(url: string, data: FormData | object, fallbackName = 'download'): Promise<void> {
  const resp = await requestRaw({ url, method: 'post', data, responseType: 'blob', timeout: 300000 })
  saveBlob(resp.data as Blob, filenameOf(String(resp.headers['content-disposition'] ?? '')) ?? fallbackName)
}

/** 取文件 Blob（用于预览、打印），失败时统一提示 */
export async function fetchBlob(url: string, params?: object): Promise<Blob> {
  const resp = await requestRaw({ url, method: 'get', params, responseType: 'blob', timeout: 300000 })
  return resp.data as Blob
}

async function requestRaw(config: AxiosRequestConfig, retried = false) {
  try {
    return await instance.request(config)
  } catch (e) {
    if (e instanceof AxiosError && e.response?.status === 401 && !retried && (await tryRefresh())) {
      return requestRaw(config, true)
    }
    let err = toBizError(e)
    if (e instanceof AxiosError && e.response?.data instanceof Blob) {
      try {
        const body = JSON.parse(await e.response.data.text()) as CommonResult<unknown>
        err = new BizError(body.code, body.msg, e.response.status)
      } catch {
        /* 非 JSON 错误体 */
      }
    }
    notifyError(err)
    throw err
  }
}

export function saveBlob(blob: Blob, filename: string) {
  const href = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = href
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(href), 1000)
}

function filenameOf(disposition: string): string | undefined {
  const star = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (star) return decodeURIComponent(star[1])
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  return plain ? decodeURIComponent(plain[1]) : undefined
}
