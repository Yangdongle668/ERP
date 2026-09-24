import { nextTick } from 'vue'
import { defineStore } from 'pinia'
import type { RouteLocationNormalized } from 'vue-router'

export const HOME_PATH = '/workbench/home'
const MAX_TABS = 20

export interface TabItem {
  /** 页签键：完整路径（含查询参数），同一单据只开一个页签 */
  key: string
  path: string
  fullPath: string
  title: string
  /** 页面有未保存修改 */
  dirty: boolean
  /** 固定页签（工作台）不可关闭 */
  fixed: boolean
  /** 缓存组件名，关闭页签时从 keep-alive 中移除 */
  cacheName: string
  openedAt: number
}

/** 页签键：路径 + 查询参数（忽略 hash） */
export function tabKeyOf(route: Pick<RouteLocationNormalized, 'path' | 'fullPath'>): string {
  return route.fullPath.split('#')[0]
}

export function cacheNameOf(key: string): string {
  return 'Tab_' + key.replace(/[^A-Za-z0-9]/g, '_')
}

/**
 * 多页签（UI 设计规范 3.3）：打开页面新增页签；已打开的页面切换过去；最多 20 个，超出时关闭最早打开的未修改页签。
 * 页签缓存页面状态（keep-alive），刷新 = 从缓存移除后重新渲染。
 */
export const useTabsStore = defineStore('tabs', {
  state: () => ({
    tabs: [] as TabItem[],
    /** 正在被刷新的页签键：从 keep-alive include 中暂时移除 */
    refreshing: '' as string,
    /** 页面主动设置的标题（如单号），优先于菜单标题 */
    customTitles: {} as Record<string, string>,
    /** 刷新次数：作为组件 key 的一部分，刷新时重新创建页面组件 */
    versions: {} as Record<string, number>
  }),
  getters: {
    cachedNames: (s) => s.tabs.filter((t) => t.key !== s.refreshing).map((t) => t.cacheName)
  },
  actions: {
    open(route: RouteLocationNormalized) {
      if (route.meta.public || !route.matched.length || route.name === 'not-found') return
      const key = tabKeyOf(route)
      const existing = this.tabs.find((t) => t.key === key)
      if (existing) return
      this.tabs.push({
        key,
        path: route.path,
        fullPath: route.fullPath,
        title: this.customTitles[key] ?? String(route.meta.title ?? route.path),
        dirty: false,
        fixed: route.path === HOME_PATH,
        cacheName: cacheNameOf(key),
        openedAt: Date.now()
      })
      this.enforceLimit()
    },
    enforceLimit() {
      while (this.tabs.length > MAX_TABS) {
        const victim = [...this.tabs].filter((t) => !t.fixed && !t.dirty).sort((a, b) => a.openedAt - b.openedAt)[0]
        if (!victim) break
        this.tabs = this.tabs.filter((t) => t !== victim)
      }
    },
    setTitle(key: string, title: string) {
      this.customTitles[key] = title
      const t = this.tabs.find((x) => x.key === key)
      if (t) t.title = title
    },
    setDirty(key: string, dirty: boolean) {
      const t = this.tabs.find((x) => x.key === key)
      if (t) t.dirty = dirty
    },
    remove(keys: string[]) {
      const set = new Set(keys)
      this.tabs = this.tabs.filter((t) => t.fixed || !set.has(t.key))
      for (const k of keys) {
        delete this.customTitles[k]
        delete this.versions[k]
      }
    },
    /** 刷新页签：先从 keep-alive 缓存中移除（销毁旧实例），再以新 key 重新创建 */
    async refresh(key: string) {
      this.refreshing = key
      await nextTick()
      this.versions[key] = (this.versions[key] ?? 0) + 1
      this.refreshing = ''
    },
    clear() {
      this.tabs = []
      this.customTitles = {}
      this.versions = {}
    }
  }
})
