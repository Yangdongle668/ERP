import { onActivated, onMounted, reactive, ref, shallowRef, type Ref, type UnwrapRef } from 'vue'
import type { PageResult } from '@/api/http'

export interface ListPageOptions<Q extends object, T> {
  /** 分页查询接口；不分页的列表返回数组即可 */
  api: (query: Q & { pageNo: number; pageSize: number }) => Promise<PageResult<T> | T[]>
  /** 默认查询条件（“重置”恢复到此值）。函数形式可以返回依赖当前日期的默认值 */
  defaultQuery?: Q | (() => Q)
  pageSize?: number
  /** 挂载后立即查询，默认 true */
  immediate?: boolean
  /** 页签切换回来时是否刷新数据（查询条件保留），默认 false */
  refreshOnActivated?: boolean
}

/**
 * 列表页组合式函数（UI 设计规范 T1）：查询参数、加载、分页、勾选、刷新。
 * 页签缓存（keep-alive）下，切回列表时保留查询条件、页码。
 *
 * const { query, list, total, loading, load, search, reset, selection } = useListPage({ api: userApi.page })
 */
export function useListPage<Q extends object, T>(options: ListPageOptions<Q, T>) {
  const defaults = (): Q => {
    const d = options.defaultQuery
    return (typeof d === 'function' ? (d as () => Q)() : { ...(d ?? {}) }) as Q
  }
  const pageSize = options.pageSize ?? 20
  const query = reactive({ ...defaults(), pageNo: 1, pageSize }) as UnwrapRef<Q & { pageNo: number; pageSize: number }>
  const list = shallowRef<T[]>([]) as Ref<T[]>
  const total = ref(0)
  const loading = ref(false)
  const selection = shallowRef<T[]>([]) as Ref<T[]>
  let seq = 0

  async function load() {
    const mySeq = ++seq
    loading.value = true
    try {
      const result = await options.api({ ...(query as object) } as Q & { pageNo: number; pageSize: number })
      if (mySeq !== seq) return // 丢弃过期响应（连续查询时只采用最后一次）
      if (Array.isArray(result)) {
        list.value = result
        total.value = result.length
      } else {
        list.value = result.list
        total.value = result.total
      }
      selection.value = []
    } finally {
      if (mySeq === seq) loading.value = false
    }
  }

  function search() {
    ;(query as { pageNo: number }).pageNo = 1
    return load()
  }

  function reset() {
    const q = query as Record<string, unknown>
    for (const k of Object.keys(q)) {
      if (k !== 'pageSize') delete q[k]
    }
    Object.assign(q, defaults(), { pageNo: 1 })
    return load()
  }

  function onSelectionChange(rows: T[]) {
    selection.value = rows
  }

  onMounted(() => {
    if (options.immediate !== false) load()
  })
  let first = true
  onActivated(() => {
    if (first) {
      first = false
      return
    }
    if (options.refreshOnActivated) load()
  })

  return { query, list, total, loading, selection, load, search, reset, onSelectionChange }
}
