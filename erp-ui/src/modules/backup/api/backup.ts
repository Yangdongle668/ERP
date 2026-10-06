import { download, http, upload, type PageResult } from '@/api/http'
import type { StatusMap } from '@/components'

export interface BackupInfo {
  backupPath: string; fileStorage: string; filesSupported: boolean; schemaVersions: Record<string, string>; usableBytes: number
  busy?: string; maintenance: boolean; autoEnabled: boolean; autoKeep: number; confirmText: string
}
export interface BackupRecord {
  id: string; fileName: string; fileSize: number; backupType: string; status: string; includeFiles: boolean; tableCount: number; rowCount: number
  attachmentCount: number; schemaVersions: Record<string, string>; dbProduct?: string; errorMsg?: string; remark?: string; startedAt: string
  finishedAt?: string; operatorName?: string; restorable: boolean; mismatch?: string
}
export interface RestoreCheck {
  id: string; fileName: string; ok: boolean; problems: string[]; backupVersions: Record<string, string>; currentVersions: Record<string, string>
  includeFiles: boolean; filesSupported: boolean; rowCount: number; attachmentCount: number; confirmText: string
}
export interface RestoreLog {
  id: string; recordId: string; fileName: string; preBackupId?: string; status: string; phase?: string; tableCount: number; rowCount: number
  attachmentCount: number; errorMsg?: string; startedAt: string; finishedAt?: string; operatorName?: string
}

export const BACKUP_TYPE: Record<string, string> = { MANUAL: '手工', AUTO: '自动', PRE_RESTORE: '恢复前', UPLOAD: '上传' }
export const RUN_STATUS: StatusMap = {
  RUNNING: { label: '进行中', type: 'primary' },
  SUCCESS: { label: '成功', type: 'success' },
  FAILED: { label: '失败', type: 'danger' }
}

export function formatBytes(n?: number | null): string {
  if (n == null) return '-'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let v = n
  let i = 0
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024
    i++
  }
  return `${v.toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

export const backupApi = {
  info: () => http.get<BackupInfo>('/backup/info'),
  records: (q: { pageNo: number; pageSize: number }) => http.get<PageResult<BackupRecord>>('/backup/records', q),
  get: (id: string) => http.get<BackupRecord>(`/backup/records/${id}`),
  create: (d: { includeFiles: boolean; remark?: string }) => http.post<string>('/backup/records', d),
  remove: (id: string) => http.delete<void>(`/backup/records/${id}`),
  download: (id: string, name: string) => download(`/backup/records/${id}/download`, undefined, name),
  upload: (file: File, onProgress?: (p: number) => void) => upload<string>('/backup/upload', file, {}, onProgress),
  check: (id: string) => http.get<RestoreCheck>(`/backup/records/${id}/check`),
  restore: (id: string, confirm: string) => http.post<string>(`/backup/records/${id}/restore`, { confirm }),
  /** 恢复进度：恢复期间其他接口返回 503，这里静默轮询 */
  restoreStatus: () => http.get<RestoreLog | null>('/backup/restore/status', undefined, { silent: true }),
  restoreLogs: () => http.get<RestoreLog[]>('/backup/restore/logs', { limit: 20 })
}
