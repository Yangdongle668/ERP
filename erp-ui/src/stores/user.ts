import { defineStore } from 'pinia'
import { authApi, type CurrentUser } from '@/api/auth'

const ACCESS = 'erp.accessToken'
const REFRESH = 'erp.refreshToken'

function readStorage(key: string): string {
  try {
    return localStorage.getItem(key) ?? ''
  } catch {
    return ''
  }
}

function writeStorage(key: string, value: string) {
  try {
    if (value) localStorage.setItem(key, value)
    else localStorage.removeItem(key)
  } catch {
    /* 隐私模式等场景下存储不可用，忽略 */
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    accessToken: readStorage(ACCESS),
    refreshToken: readStorage(REFRESH),
    user: null as CurrentUser | null
  }),
  getters: {
    isLoggedIn: (s) => !!s.accessToken,
    permissionSet: (s) => new Set(s.user?.permissions ?? [])
  },
  actions: {
    setTokens(access: string, refresh: string) {
      this.accessToken = access
      this.refreshToken = refresh
      writeStorage(ACCESS, access)
      writeStorage(REFRESH, refresh)
    },
    async login(username: string, password: string) {
      const resp = await authApi.login(username, password)
      this.setTokens(resp.accessToken, resp.refreshToken)
      await this.loadUser()
    },
    async refresh(): Promise<boolean> {
      try {
        const resp = await authApi.refresh(this.refreshToken)
        this.setTokens(resp.accessToken, resp.refreshToken)
        return true
      } catch {
        return false
      }
    },
    async loadUser() {
      this.user = await authApi.me()
    },
    hasPermission(permission?: string): boolean {
      if (!permission) return true
      const set = this.permissionSet
      return set.has('*') || set.has(permission)
    },
    clear() {
      this.setTokens('', '')
      this.user = null
    }
  }
})
