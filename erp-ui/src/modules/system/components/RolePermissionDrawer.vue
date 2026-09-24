<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { roleApi, type PermissionGroup, type PermissionModule, type RoleRow } from '../api/role'

/**
 * 功能权限抽屉（01-03 3.3）：每个分组一行，菜单权限排第一；勾选任一权限点自动勾选其依赖；
 * 取消“查看”时同时取消该分组其他权限；模块、分组支持半选；按名称搜索。
 */
const visible = ref(false)
const saving = ref(false)
const role = ref<RoleRow>()
const tree = ref<PermissionModule[]>([])
const checked = ref(new Set<string>())
const keyword = ref('')
const collapsed = ref(new Set<string>())

const deps = computed(() => {
  const m = new Map<string, string[]>()
  for (const mod of tree.value) for (const g of mod.groups) for (const p of g.permissions) m.set(p.code, p.dependsOn)
  return m
})

const filtered = computed(() => {
  const k = keyword.value.trim()
  if (!k) return tree.value
  return tree.value
    .map((m) => ({ ...m, groups: m.groups.filter((g) => g.name.includes(k) || g.permissions.some((p) => p.name.includes(k) || p.code.includes(k))) }))
    .filter((m) => m.groups.length || m.name.includes(k))
})

async function open(r: RoleRow) {
  role.value = r
  keyword.value = ''
  const [t, perms] = await Promise.all([roleApi.permissionTree(), roleApi.permissions(r.id)])
  tree.value = t
  checked.value = new Set(perms)
  visible.value = true
}

function addWithDeps(code: string, set: Set<string>) {
  if (set.has(code)) return
  set.add(code)
  for (const d of deps.value.get(code) ?? []) addWithDeps(d, set)
}

function toggle(group: PermissionGroup, code: string, on: boolean) {
  const set = new Set(checked.value)
  if (on) addWithDeps(code, set)
  else {
    set.delete(code)
    const menu = group.permissions.find((p) => p.type === 'MENU')
    if (menu && menu.code === code) {
      const others = group.permissions.filter((p) => p.code !== code && set.has(p.code))
      if (others.length) ElMessage.info('取消查看将同时取消该页面的其他权限')
      others.forEach((p) => set.delete(p.code))
    }
    // 依赖该权限点的其他权限点一并取消
    for (const [c, ds] of deps.value) if (ds.includes(code)) set.delete(c)
  }
  checked.value = set
}

function groupState(g: PermissionGroup) {
  const n = g.permissions.filter((p) => checked.value.has(p.code)).length
  return { all: n === g.permissions.length && n > 0, some: n > 0 && n < g.permissions.length }
}

function moduleState(m: PermissionModule) {
  const all = m.groups.flatMap((g) => g.permissions)
  const n = all.filter((p) => checked.value.has(p.code)).length
  return { all: n === all.length && n > 0, some: n > 0 && n < all.length }
}

function toggleGroup(g: PermissionGroup, on: boolean) {
  const set = new Set(checked.value)
  for (const p of g.permissions) {
    if (on) addWithDeps(p.code, set)
    else set.delete(p.code)
  }
  checked.value = set
}

function toggleModule(m: PermissionModule, on: boolean) {
  m.groups.forEach((g) => toggleGroup(g, on))
}

function setAllCollapsed(c: boolean) {
  collapsed.value = new Set(c ? tree.value.map((m) => m.code) : [])
}

function toggleCollapse(code: string) {
  const s = new Set(collapsed.value)
  if (s.has(code)) s.delete(code)
  else s.add(code)
  collapsed.value = s
}

async function save() {
  saving.value = true
  try {
    await roleApi.savePermissions(role.value!.id, [...checked.value])
    ElMessage.success('保存成功，权限已立即生效')
    visible.value = false
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-drawer v-model="visible" :title="`功能权限 - ${role?.name ?? ''}`" size="720px" append-to-body>
    <div class="bar">
      <el-input v-model="keyword" placeholder="搜索权限" prefix-icon="Search" clearable class="search" />
      <el-button link type="primary" @click="setAllCollapsed(false)">全部展开</el-button>
      <el-button link type="primary" @click="setAllCollapsed(true)">全部收起</el-button>
    </div>
    <div v-for="m in filtered" :key="m.code" class="module">
      <div class="module-head">
        <el-icon class="caret" @click="toggleCollapse(m.code)"><component :is="collapsed.has(m.code) ? 'ArrowRight' : 'ArrowDown'" /></el-icon>
        <el-checkbox :model-value="moduleState(m).all" :indeterminate="moduleState(m).some" @change="(v: any) => toggleModule(m, !!v)">
          <span class="module-name">{{ m.name }}</span>
        </el-checkbox>
      </div>
      <div v-show="!collapsed.has(m.code)">
        <div v-for="g in m.groups" :key="g.code" class="group">
          <el-checkbox :model-value="groupState(g).all" :indeterminate="groupState(g).some" class="group-name" @change="(v: any) => toggleGroup(g, !!v)">
            {{ g.name }}
          </el-checkbox>
          <div class="perms">
            <el-checkbox
              v-for="p in g.permissions"
              :key="p.code"
              :model-value="checked.has(p.code)"
              :title="p.code"
              @change="(v: any) => toggle(g, p.code, !!v)"
            >
              {{ p.name }}<ErpBadge v-if="p.type === 'FIELD'" type="warning" :dot="false" class="field">字段</ErpBadge>
            </el-checkbox>
          </div>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-drawer>
</template>

<style scoped>
.bar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.search { flex: 1; }
.module { border-bottom: 1px solid var(--erp-color-border-light); padding: 8px 0; }
.module-head { display: flex; align-items: center; gap: 4px; }
.caret { cursor: pointer; }
.group { display: flex; align-items: flex-start; padding: 4px 0 4px 24px; }
.group-name { width: 150px; flex-shrink: 0; }
.perms { display: flex; flex-wrap: wrap; column-gap: 4px; }
.field { margin-left: 6px; }
.module-name { font-weight: var(--erp-font-weight-semibold); }
.caret { color: var(--erp-color-text-tertiary); }
</style>
