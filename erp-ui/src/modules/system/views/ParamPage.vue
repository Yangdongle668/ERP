<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { paramApi, type ParamModule, type ParamRow } from '../api/param'

defineOptions({ name: 'SystemParamPage' })

/** 系统参数（01-10，T8）：左侧模块导航，右侧按分组卡片显示；只提交修改过的参数 */
const modules = ref<ParamModule[]>([])
const current = ref('')
const keyword = ref('')
const params = ref<ParamRow[]>([])
const loading = ref(false)
const saving = ref(false)
/** 编辑中的值：key → 值（字符串形式，与后端一致） */
const draft = reactive<Record<string, string | undefined>>({})

const changed = computed(() => params.value.filter((p) => (draft[p.key] ?? '') !== (p.value ?? '')))
useLeaveGuard(() => changed.value.length > 0)

const groups = computed(() => {
  const map = new Map<string, ParamRow[]>()
  for (const p of params.value) {
    const g = keyword.value ? `${moduleName(p.moduleCode)} / ${p.groupName}` : p.groupName
    if (!map.has(g)) map.set(g, [])
    map.get(g)!.push(p)
  }
  return [...map.entries()].map(([name, items]) => ({ name, items }))
})

const moduleName = (code: string) => modules.value.find((m) => m.moduleCode === code)?.moduleName ?? code

async function loadModules() {
  modules.value = await paramApi.modules()
  if (!current.value && modules.value.length) current.value = modules.value[0].moduleCode
}

async function load() {
  loading.value = true
  try {
    params.value = await paramApi.list(keyword.value ? { keyword: keyword.value } : { module: current.value })
    for (const k of Object.keys(draft)) delete draft[k]
    for (const p of params.value) draft[p.key] = p.value
  } finally {
    loading.value = false
  }
}

async function confirmDiscard() {
  if (!changed.value.length) return true
  return ElMessageBox.confirm('有未保存的修改，确定放弃吗？', '提示', { type: 'warning' }).then(() => true, () => false)
}

async function selectModule(code: string) {
  if (code === current.value && !keyword.value) return
  if (!(await confirmDiscard())) return
  current.value = code
  keyword.value = ''
  load()
}

async function onSearch() {
  if (!(await confirmDiscard())) return
  load()
}

// ---------- 控件值转换 ----------
const optionLabel = (p: ParamRow, v?: string) => p.options.find((o) => o.value === v)?.label ?? v
function display(p: ParamRow, v?: string): string {
  if (v === undefined || v === null || v === '') return '（空）'
  switch (p.valueType) {
    case 'BOOL': return v === 'true' ? '是' : '否'
    case 'ENUM': return optionLabel(p, v) ?? v
    case 'USER_LIST': return `${v.split(',').filter(Boolean).length} 人`
    default: return v
  }
}

const boolOf = (p: ParamRow) => draft[p.key] === 'true'
const setBool = (p: ParamRow, v: string | number | boolean) => (draft[p.key] = v ? 'true' : 'false')
const usersOf = (p: ParamRow) => (draft[p.key] ?? '').split(',').map((s) => s.trim()).filter(Boolean)
const setUsers = (p: ParamRow, v: string | string[] | undefined) => (draft[p.key] = (Array.isArray(v) ? v : v ? [v] : []).join(','))
const numMin = (p: ParamRow) => (p.minValue !== undefined && p.minValue !== null ? Number(p.minValue) : undefined)
const numMax = (p: ParamRow) => (p.maxValue !== undefined && p.maxValue !== null ? Number(p.maxValue) : undefined)
const rangeTip = (p: ParamRow) => {
  const min = numMin(p)
  const max = numMax(p)
  if (min === undefined && max === undefined) return ''
  return `范围 ${min ?? '-∞'}～${max ?? '∞'}`
}
const modified = (p: ParamRow) => (p.value ?? '') !== (p.defaultValue ?? '')

async function save() {
  const list = changed.value
  if (!list.length) {
    ElMessage.info('没有修改')
    return
  }
  const html = list
    .map((p) => `<div>${escape(p.groupName)} / ${escape(p.name)}：${escape(display(p, p.value))} → <b>${escape(display(p, draft[p.key]))}</b></div>`)
    .join('')
  await ElMessageBox.confirm(html, `确认修改 ${list.length} 个参数`, { dangerouslyUseHTMLString: true, type: 'warning' })
  saving.value = true
  try {
    await paramApi.save(list.map((p) => ({ key: p.key, value: draft[p.key] ?? '' })))
    ElMessage.success('保存成功，立即生效')
    await load()
  } finally {
    saving.value = false
  }
}

async function resetDefault(p: ParamRow) {
  await ElMessageBox.confirm(`确定将「${p.name}」恢复为默认值「${display(p, p.defaultValue)}」吗？`, '恢复默认', { type: 'warning' })
  await paramApi.reset(p.key)
  ElMessage.success('已恢复默认')
  const fresh = await paramApi.list(keyword.value ? { keyword: keyword.value } : { module: current.value })
  const row = fresh.find((x) => x.key === p.key)
  if (row) {
    Object.assign(p, row)
    draft[p.key] = row.value
  }
}

function escape(s: string) {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!)
}

onMounted(async () => {
  await loadModules()
  await load()
})
</script>

<template>
  <ErpPage description="系统与各模块的运行参数，保存后立即生效，无需重启">
    <template #actions>
      <el-input v-model="keyword" placeholder="按名称或编码搜索全部参数" clearable prefix-icon="Search" class="search"
                @keyup.enter="onSearch" @clear="onSearch" />
      <span v-if="changed.length" class="pending"><i class="pending__dot" />{{ changed.length }} 项未保存</span>
      <el-button v-perm="'system:param:update'" type="primary" :loading="saving" :disabled="!changed.length" @click="save">保存</el-button>
    </template>

    <div class="erp-split">
      <ErpPanel class="nav" flush>
        <nav class="nav__list">
          <button
            v-for="m in modules"
            :key="m.moduleCode"
            type="button"
            :class="['nav__item', { active: !keyword && m.moduleCode === current }]"
            @click="selectModule(m.moduleCode)"
          >
            <span>{{ m.moduleName }}</span>
            <span class="nav__count num">{{ m.count }}</span>
          </button>
        </nav>
        <ErpEmpty v-if="!modules.length" description="暂无参数" compact />
      </ErpPanel>

      <ErpPanel v-loading="loading" class="erp-split-main">
        <section v-for="g in groups" :key="g.name" class="group">
          <h3 class="group__title">{{ g.name }}</h3>
          <div v-for="p in g.items" :key="p.key" :class="['param-row', { 'is-dirty': (draft[p.key] ?? '') !== (p.value ?? '') }]">
            <div class="label">
              <div class="name">
                {{ p.name }}
                <el-tooltip v-if="modified(p)" :content="`默认值：${display(p, p.defaultValue)}`" placement="top">
                  <ErpBadge type="warning" :dot="false">已修改</ErpBadge>
                </el-tooltip>
              </div>
              <div class="key mono">{{ p.key }}</div>
            </div>
            <div class="control">
              <el-input v-if="p.valueType === 'STRING'" v-model="draft[p.key]" maxlength="500" />
              <NumberInput v-else-if="p.valueType === 'INT' || p.valueType === 'DECIMAL'" v-model="draft[p.key]"
                           :precision="p.valueType === 'INT' ? 0 : 4" :min="numMin(p)" :max="numMax(p)" trim-zeros allow-negative class="w200" />
              <el-switch v-else-if="p.valueType === 'BOOL'" :model-value="boolOf(p)" @update:model-value="setBool(p, $event)" />
              <el-select v-else-if="p.valueType === 'ENUM'" v-model="draft[p.key]" class="w200">
                <el-option v-for="o in p.options" :key="o.value" :value="o.value" :label="o.label" />
              </el-select>
              <UserSelect v-else-if="p.valueType === 'USER_LIST'" :model-value="usersOf(p)" multiple @update:model-value="setUsers(p, $event)" />
              <el-time-picker v-else-if="p.valueType === 'TIME'" v-model="draft[p.key]" format="HH:mm" value-format="HH:mm" :clearable="false" class="w200" />
              <div class="desc">{{ p.description }}<span v-if="rangeTip(p)">（{{ rangeTip(p) }}）</span></div>
            </div>
            <div class="ops">
              <el-button v-if="modified(p)" v-perm="'system:param:update'" link type="primary" @click="resetDefault(p)">恢复默认</el-button>
            </div>
          </div>
        </section>
        <ErpEmpty v-if="!loading && !groups.length" description="没有匹配的参数" />
      </ErpPanel>
    </div>
  </ErpPage>
</template>

<style scoped>
.search { width: 280px; }
.pending { display: inline-flex; align-items: center; gap: 6px; font-size: var(--erp-font-size-secondary); color: var(--erp-color-text-secondary); }
.pending__dot { width: 6px; height: 6px; border-radius: 50%; background: var(--el-color-warning); }
.nav { width: 208px; flex-shrink: 0; position: sticky; top: 0; }
.nav__list { display: flex; flex-direction: column; padding: 8px; gap: 2px; }
.nav__item {
  display: flex; justify-content: space-between; align-items: center; height: 36px; padding: 0 12px; border: none; background: transparent;
  border-radius: var(--erp-radius-control); cursor: pointer; font-family: inherit; font-size: var(--erp-font-size-body); color: var(--erp-color-text-secondary);
  transition: background-color var(--erp-duration-fast) var(--erp-ease), color var(--erp-duration-fast) var(--erp-ease);
}
.nav__item:hover { background: var(--erp-color-hover); color: var(--erp-color-text); }
.nav__item.active { background: var(--erp-color-primary-bg); color: var(--el-color-primary); font-weight: var(--erp-font-weight-medium); }
.nav__count { font-size: var(--erp-font-size-caption); color: var(--erp-color-text-tertiary); }
.group + .group { margin-top: 28px; }
.group__title {
  margin: 0 0 4px; padding-bottom: 8px; border-bottom: 1px solid var(--erp-color-border);
  font-size: var(--erp-font-size-section-title); font-weight: var(--erp-font-weight-semibold); line-height: 24px;
}
.param-row { display: flex; gap: 24px; padding: 14px 0; border-bottom: 1px solid var(--erp-color-border-light); }
.param-row:last-child { border-bottom: none; }
.param-row.is-dirty { box-shadow: -3px 0 0 var(--el-color-warning); padding-left: 12px; margin-left: -12px; }
.label { width: 260px; flex-shrink: 0; }
.name { font-weight: var(--erp-font-weight-medium); display: flex; align-items: center; gap: 8px; }
.key { color: var(--erp-color-text-tertiary); margin-top: 2px; font-size: var(--erp-font-size-caption); }
.control { flex: 1; min-width: 0; max-width: 520px; }
.desc { font-size: var(--erp-font-size-caption); color: var(--erp-color-text-tertiary); margin-top: 6px; line-height: 18px; }
.ops { width: 80px; flex-shrink: 0; text-align: right; }
</style>
