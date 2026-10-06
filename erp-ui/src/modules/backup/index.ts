import { defineModule } from '../types'

/**
 * 系统备份模块前端入口（需求 14-系统备份）：只有超级管理员（拥有全部权限 *）可见。
 * 全量备份（数据库 + 附件）、下载 / 上传备份文件、一键恢复、自动备份与恢复记录。
 */
export default defineModule({
  code: 'backup',
  title: '系统备份',
  icon: 'Backup',
  order: 950,
  doc: '14-系统备份',
  menus: [
    { path: 'records', title: '备份与恢复', permission: '*', doc: 'README.md', component: () => import('./views/BackupPage.vue') }
  ]
})
