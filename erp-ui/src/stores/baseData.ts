import { defineStore } from 'pinia'
import {
  systemCommonApi,
  type Country,
  type CurrencySimple,
  type OrgNode,
  type UomSimple
} from '@/api/system'

const TTL_MS = 5 * 60 * 1000

interface Cached<T> {
  data: T | null
  at: number
  pending: Promise<T> | null
}

function empty<T>(): Cached<T> {
  return { data: null, at: 0, pending: null }
}

function cachedLoad<T>(c: Cached<T>, loader: () => Promise<T>, force: boolean): Promise<T> {
  if (!force && c.data && Date.now() - c.at < TTL_MS) return Promise.resolve(c.data)
  if (c.pending) return c.pending
  c.pending = loader()
    .then((d) => {
      c.data = d
      c.at = Date.now()
      return d
    })
    .finally(() => (c.pending = null))
  return c.pending
}

/**
 * 基础数据缓存：计量单位、币别、国家、组织树。多个选择器同时挂载时只请求一次，5 分钟后过期重新加载。
 */
export const useBaseDataStore = defineStore('baseData', {
  state: () => ({
    uoms: empty<UomSimple[]>(),
    currencies: empty<CurrencySimple[]>(),
    countries: empty<Country[]>(),
    orgTree: empty<OrgNode[]>()
  }),
  actions: {
    loadUoms(force = false) {
      return cachedLoad(this.uoms as Cached<UomSimple[]>, systemCommonApi.uomSimple, force)
    },
    loadCurrencies(force = false) {
      return cachedLoad(this.currencies as Cached<CurrencySimple[]>, systemCommonApi.currencySimple, force)
    },
    loadCountries() {
      return cachedLoad(this.countries as Cached<Country[]>, systemCommonApi.countries, false)
    },
    loadOrgTree(force = false) {
      return cachedLoad(this.orgTree as Cached<OrgNode[]>, systemCommonApi.orgSimpleTree, force)
    },
    /** 单位精度，未知单位按 4 位 */
    uomPrecision(code?: string): number {
      return this.uoms.data?.find((u) => u.code === code)?.precision ?? 4
    },
    /** 币别金额精度，未知币别按 2 位 */
    currencyPrecision(code?: string): number {
      return this.currencies.data?.find((c) => c.code === code)?.amountPrecision ?? 2
    },
    baseCurrency(): string | undefined {
      return this.currencies.data?.find((c) => c.base)?.code
    },
    invalidate() {
      this.uoms = empty()
      this.currencies = empty()
      this.orgTree = empty()
    }
  }
})
