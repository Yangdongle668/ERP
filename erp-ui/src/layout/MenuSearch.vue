<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import type { ModuleDefinition } from '@/modules/types'

/** 菜单搜索：输入中文、拼音首字母或全拼，下拉匹配菜单，回车跳转；Ctrl + K 聚焦 */
const props = defineProps<{ modules: ModuleDefinition[] }>()
const router = useRouter()
const keyword = ref('')
const inputRef = ref<{ focus: () => void }>()

interface Entry { path: string; title: string; module: string; initials: string; full: string }

/** 拼音库较大，首次搜索时再加载 */
type PinyinFn = typeof import('pinyin-pro').pinyin
const pinyinFn = shallowRef<PinyinFn>()
async function ensurePinyin() {
  if (!pinyinFn.value) pinyinFn.value = (await import('pinyin-pro')).pinyin
}

const entries = computed<Entry[]>(() => {
  const pinyin = pinyinFn.value
  if (!pinyin) return []
  return props.modules.flatMap((m) =>
    m.menus.map((menu) => ({
      path: `/${m.code}/${menu.path}`,
      title: menu.title,
      module: m.title,
      initials: pinyin(menu.title, { pattern: 'first', toneType: 'none', type: 'array' }).join('').toLowerCase(),
      full: pinyin(menu.title, { toneType: 'none', type: 'array' }).join('').toLowerCase()
    }))
  )
})

async function query(q: string, cb: (items: { value: string; entry: Entry }[]) => void) {
  await ensurePinyin()
  const k = q.trim().toLowerCase()
  if (!k) return cb([])
  const hits = entries.value.filter((e) => e.title.toLowerCase().includes(k) || e.module.includes(k) || e.initials.includes(k) || e.full.includes(k))
  cb(hits.slice(0, 20).map((e) => ({ value: e.title, entry: e })))
}

function select(item: Record<string, any>) {
  keyword.value = ''
  router.push((item as { entry: Entry }).entry.path)
}

function onKey(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    ensurePinyin()
    inputRef.value?.focus()
  }
}
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <el-autocomplete
    ref="inputRef"
    v-model="keyword"
    :fetch-suggestions="query"
    placeholder="搜索菜单（Ctrl+K）"
    prefix-icon="Search"
    :trigger-on-focus="false"
    highlight-first-item
    clearable
    class="menu-search"
    @select="select"
  >
    <template #default="{ item }">
      <span>{{ item.entry.title }}</span><span class="module">{{ item.entry.module }}</span>
    </template>
  </el-autocomplete>
</template>

<style scoped>
.menu-search { width: 220px; }
.module { float: right; margin-left: 12px; color: var(--el-text-color-secondary); font-size: 12px; }
</style>
