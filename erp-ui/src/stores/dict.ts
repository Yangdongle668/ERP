import { defineStore } from 'pinia'
import { computed } from 'vue'
import { systemCommonApi, type DictItem, type TagType } from '@/api/system'

const POLL_MS = 10 * 60 * 1000

/**
 * 数据字典全局缓存（01-04 数据字典 第 6 节）：登录后加载一次，每 10 分钟比较版本号，变化时重新加载。
 * 停用项也在缓存中（带 enabled=false），用于显示历史数据的标签；选择器只列启用项。
 */
export const useDictStore = defineStore('dict', {
  state: () => ({
    version: -1,
    types: {} as Record<string, DictItem[]>,
    loading: null as Promise<void> | null,
    timer: 0 as number
  }),
  actions: {
    async load(force = false) {
      if (this.loading) return this.loading
      if (!force && this.version >= 0) return
      this.loading = (async () => {
        try {
          const bundle = await systemCommonApi.dictAll()
          const map: Record<string, DictItem[]> = {}
          for (const t of bundle.types) map[t.code] = [...t.items].sort((a, b) => a.sort - b.sort)
          this.types = map
          this.version = bundle.version
        } catch {
          /* 字典加载失败不阻塞页面，标签退化为显示原值 */
        } finally {
          this.loading = null
        }
      })()
      return this.loading
    },
    startPolling() {
      this.stopPolling()
      this.timer = window.setInterval(async () => {
        try {
          const v = await systemCommonApi.dictVersion()
          if (v !== this.version) await this.load(true)
        } catch {
          /* 忽略 */
        }
      }, POLL_MS)
    },
    stopPolling() {
      if (this.timer) window.clearInterval(this.timer)
      this.timer = 0
    },
    reset() {
      this.stopPolling()
      this.version = -1
      this.types = {}
    },
    items(type: string, includeDisabled = false): DictItem[] {
      const list = this.types[type] ?? []
      return includeDisabled ? list : list.filter((i) => i.enabled)
    },
    item(type: string, value?: string | null): DictItem | undefined {
      if (value === null || value === undefined || value === '') return undefined
      return (this.types[type] ?? []).find((i) => i.value === value)
    },
    label(type: string, value?: string | null): string {
      if (value === null || value === undefined || value === '') return '-'
      return this.item(type, value)?.label ?? value
    },
    defaultValue(type: string): string | undefined {
      return this.items(type).find((i) => i.isDefault)?.value
    }
  }
})

/** DictTag 颜色 → Element Plus tag type */
export function tagTypeOf(t?: TagType): '' | 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  switch (t) {
    case 'PRIMARY': return 'primary'
    case 'SUCCESS': return 'success'
    case 'WARNING': return 'warning'
    case 'DANGER': return 'danger'
    case 'INFO': return 'info'
    default: return ''
  }
}

/** 组合式用法：const { items, label } = useDict('crm_source') */
export function useDict(type: string) {
  const store = useDictStore()
  store.load()
  return {
    items: computed(() => store.items(type)),
    allItems: computed(() => store.items(type, true)),
    label: (v?: string | null) => store.label(type, v),
    defaultValue: () => store.defaultValue(type)
  }
}
