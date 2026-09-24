<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { systemCommonApi, type OrgNode } from '@/api/system'
import { useBaseDataStore } from '@/stores/baseData'
import { useUserStore } from '@/stores/user'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { formatDateTime } from '@/utils/format'
import { roleApi, type RoleSimple } from '../api/role'
import {
  APPROVER_TYPES, OPS, workflowApi,
  type ApproverType, type BizField, type BizType, type Condition, type Definition, type HistoryRow, type NodeDef
} from '../api/workflow'

defineOptions({ name: 'SystemWorkflowConfig' })

/**
 * 审批流配置（需求 08 第 4.1 节）：左侧单据类型，右侧查看当前版本 / 编辑草稿。
 * 模型为“条件分支 + 顺序节点”：分支自上而下匹配，命中第一个即停止；“其他情况”兜底，固定在最后。
 */
const baseData = useBaseDataStore()
const userStore = useUserStore()
const canUpdate = computed(() => userStore.hasPermission('system:workflow:update'))

const types = ref<BizType[]>([])
const keyword = ref('')
const current = ref<BizType>()
const active = ref<Definition>()
const draft = ref<Definition>()
const editing = ref(false)
const loading = ref(false)
const saving = ref(false)
const roles = ref<RoleSimple[]>([])
/** 用户 ID → 姓名（节点摘要、条件显示） */
const userNames = ref<Record<string, string>>({})

const guard = useLeaveGuard(() => (editing.value ? draft.value : null))

const groups = computed(() => {
  const k = keyword.value.trim()
  const map = new Map<string, BizType[]>()
  for (const t of types.value) {
    if (k && !t.name.includes(k) && !t.bizType.toLowerCase().includes(k.toLowerCase())) continue
    if (!map.has(t.moduleName)) map.set(t.moduleName, [])
    map.get(t.moduleName)!.push(t)
  }
  return [...map.entries()].map(([name, items]) => ({ name, items }))
})

const STATUS_LABEL: Record<BizType['configStatus'], { label: string; type: 'success' | 'info'; plain?: boolean }> = {
  NONE: { label: '未配置', type: 'info' },
  ENABLED: { label: '已启用', type: 'success' },
  DISABLED: { label: '已停用', type: 'info', plain: true }
}

async function loadTypes() {
  types.value = await workflowApi.bizTypes()
  if (current.value) current.value = types.value.find((t) => t.bizType === current.value!.bizType)
}

async function select(t: BizType) {
  if (editing.value && current.value?.bizType !== t.bizType && !(await guard.confirmLeave())) return
  current.value = t
  editing.value = false
  await loadDefinitions()
}

async function loadDefinitions() {
  if (!current.value) return
  loading.value = true
  try {
    const r = await workflowApi.definitions(current.value.bizType)
    active.value = r.active
    draft.value = r.draft
  } finally {
    loading.value = false
  }
}

const shown = computed(() => (editing.value ? draft.value : active.value))
const fields = computed(() => current.value?.fields ?? [])
const fieldOf = (code: string) => fields.value.find((f) => f.code === code)

// ---------- 条件与节点显示 ----------
function orgName(id: string, nodes: OrgNode[] = baseData.orgTree.data ?? []): string | undefined {
  for (const n of nodes) {
    if (n.id === id) return n.name
    const c = orgName(id, n.children ?? [])
    if (c) return c
  }
  return undefined
}

function valueText(f: BizField | undefined, v: unknown): string {
  const arr = Array.isArray(v) ? v.map(String) : v === undefined || v === null ? [] : [String(v)]
  if (!f) return arr.join('、')
  switch (f.type) {
    case 'ENUM': return arr.map((x) => f.options.find((o) => o.value === x)?.label ?? x).join('、')
    case 'DEPT': return arr.map((x) => orgName(x) ?? x).join('、')
    case 'USER': return arr.map((x) => userNames.value[x] ?? x).join('、')
    case 'BOOL': return arr[0] === 'true' ? '是' : '否'
    default: return arr.join('、')
  }
}

function conditionText(c: Condition): string {
  const f = fieldOf(c.field)
  const op = f ? OPS[f.type].find((o) => o.value === c.op)?.label ?? c.op : c.op
  if (c.op === 'BETWEEN' && Array.isArray(c.value)) return `${f?.name ?? c.field} 介于 ${c.value[0]} ～ ${c.value[1]}`
  return `${f?.name ?? c.field} ${op} ${valueText(f, c.value)}`
}

function nodeSummary(n: NodeDef): string {
  if (n.approverSummary) return n.approverSummary
  switch (n.approverType) {
    case 'USER': return ((n.approverValue as string[]) ?? []).map((id) => userNames.value[id] ?? id).join('、') || '未指定'
    case 'ROLE': {
      const v = (n.approverValue ?? {}) as { roleId?: string; sameCompany?: boolean }
      return (roles.value.find((r) => r.id === v.roleId)?.name ?? '未指定角色') + (v.sameCompany !== false ? '（发起人所在公司）' : '')
    }
    case 'BIZ_USER': return '单据字段：' + (current.value?.userFields.find((f) => f.code === n.approverValue)?.name ?? String(n.approverValue ?? ''))
    default: return APPROVER_TYPES.find((t) => t.value === n.approverType)?.label ?? n.approverType
  }
}

const multiLabel = (n: NodeDef) => (['USER', 'ROLE'].includes(n.approverType) ? (n.multiMode === 'ALL' ? '会签' : '或签') : '')

/** 为条件、节点中出现的用户 ID 取姓名 */
async function resolveUserNames(def?: Definition) {
  const ids = new Set<string>()
  def?.branches.forEach((b) => {
    b.conditions.forEach((c) => { if (fieldOf(c.field)?.type === 'USER' && Array.isArray(c.value)) c.value.forEach((x) => ids.add(String(x))) })
  })
  const missing = [...ids].filter((id) => !userNames.value[id])
  if (!missing.length) return
  const users = await systemCommonApi.userSearch('', missing).catch(() => [])
  users.forEach((u) => (userNames.value[u.id] = u.realName))
}

// ---------- 启用、编辑、发布 ----------
async function toggleEnabled(v: boolean) {
  if (!current.value) return
  if (!v) await ElMessageBox.confirm('关闭后该单据提交即审核通过，确定吗？', '关闭审批', { type: 'warning' })
  await workflowApi.setEnabled(current.value.bizType, v)
  ElMessage.success(v ? '已启用审批' : '已关闭审批')
  await Promise.all([loadDefinitions(), loadTypes()])
}

async function startEdit() {
  if (!current.value) return
  draft.value = await workflowApi.draft(current.value.bizType)
  await resolveUserNames(draft.value)
  editing.value = true
  guard.markClean()
  loadTypes()
}

function payload(d: Definition) {
  return {
    skipInitiator: d.skipInitiator,
    skipDuplicate: d.skipDuplicate,
    emptyPolicy: d.emptyPolicy,
    branches: d.branches.map((b) => ({
      name: b.name, isDefault: b.isDefault, conditions: b.isDefault ? [] : b.conditions,
      nodes: b.nodes.map((n) => ({ name: n.name, approverType: n.approverType, approverValue: n.approverValue, multiMode: n.multiMode }))
    }))
  }
}

async function saveDraft(silent = false) {
  if (!draft.value) return
  saving.value = true
  try {
    draft.value = await workflowApi.saveDraft(draft.value.id, payload(draft.value))
    guard.markClean()
    if (!silent) ElMessage.success('草稿已保存')
  } finally {
    saving.value = false
  }
}

async function discard() {
  if (!draft.value) return
  await ElMessageBox.confirm('放弃后草稿中的修改将全部丢失，确定放弃吗？', '放弃草稿', { type: 'warning' })
  await workflowApi.discard(draft.value.id)
  guard.markClean()
  editing.value = false
  ElMessage.success('已放弃草稿')
  await Promise.all([loadDefinitions(), loadTypes()])
}

const publishVisible = ref(false)
const publishRemark = ref('')
async function publish() {
  if (!draft.value) return
  await saveDraft(true)
  saving.value = true
  try {
    await workflowApi.publish(draft.value.id, publishRemark.value)
    publishVisible.value = false
    publishRemark.value = ''
    editing.value = false
    guard.markClean()
    ElMessage.success('已发布，新提交的单据按新版本审批')
    await Promise.all([loadDefinitions(), loadTypes()])
  } finally {
    saving.value = false
  }
}

// ---------- 分支 ----------
const editableBranches = computed(() => draft.value?.branches ?? [])

function moveBranch(i: number, delta: number) {
  const list = editableBranches.value
  const j = i + delta
  if (j < 0 || j >= list.length || list[j].isDefault || list[i].isDefault) return
  ;[list[i], list[j]] = [list[j], list[i]]
}

async function removeBranch(i: number) {
  await ElMessageBox.confirm(`确定删除分支「${editableBranches.value[i].name}」吗？`, '删除分支', { type: 'warning' })
  editableBranches.value.splice(i, 1)
}

const branchVisible = ref(false)
const branchIndex = ref(-1)
const branchForm = ref<{ name: string; conditions: Condition[] }>({ name: '', conditions: [] })

function openBranch(i: number) {
  branchIndex.value = i
  const b = i >= 0 ? editableBranches.value[i] : undefined
  branchForm.value = b
    ? { name: b.name, conditions: JSON.parse(JSON.stringify(b.conditions)) }
    : { name: '', conditions: [{ field: fields.value[0]?.code ?? '', op: defaultOp(fields.value[0]), value: undefined }] }
  branchVisible.value = true
}

function defaultOp(f?: BizField) {
  return f ? OPS[f.type][0].value : ''
}

function onFieldChange(c: Condition) {
  c.op = defaultOp(fieldOf(c.field))
  c.value = undefined
}

function onOpChange(c: Condition) {
  c.value = undefined
}

const isMulti = (c: Condition) => ['IN', 'NOT_IN', 'IN_TREE'].includes(c.op) || fieldOf(c.field)?.type === 'USER'

function confirmBranch() {
  const f = branchForm.value
  if (!f.name.trim()) return ElMessage.warning('请输入分支名称')
  if (!f.conditions.length) return ElMessage.warning('至少需要一个条件')
  const bad = f.conditions.findIndex((c) => {
    if (!c.field || !c.op) return true
    if (c.op === 'BETWEEN') return !Array.isArray(c.value) || c.value.length !== 2 || c.value.some((x) => x === undefined || x === '')
    if (Array.isArray(c.value)) return !c.value.length
    return c.value === undefined || c.value === null || c.value === ''
  })
  if (bad >= 0) return ElMessage.warning(`第 ${bad + 1} 个条件不完整`)
  const list = editableBranches.value
  if (branchIndex.value >= 0) Object.assign(list[branchIndex.value], { name: f.name.trim(), conditions: f.conditions })
  else list.splice(list.length - 1, 0, { name: f.name.trim(), isDefault: false, conditions: f.conditions, nodes: [] })
  branchVisible.value = false
}

// ---------- 节点 ----------
const nodeVisible = ref(false)
const nodeTarget = ref<{ branch: number; index: number; insert: boolean }>({ branch: 0, index: 0, insert: true })
const nodeForm = ref<NodeDef>({ name: '', approverType: 'DEPT_LEADER', approverValue: null, multiMode: 'ANY' })
const roleForm = ref<{ roleId?: string; sameCompany: boolean }>({ sameCompany: true })

function openNode(branch: number, index: number, insert: boolean) {
  nodeTarget.value = { branch, index, insert }
  const n = insert ? undefined : editableBranches.value[branch].nodes[index]
  nodeForm.value = n ? JSON.parse(JSON.stringify(n)) : { name: '部门负责人审批', approverType: 'DEPT_LEADER', approverValue: null, multiMode: 'ANY' }
  const rv = (n?.approverType === 'ROLE' ? n.approverValue : {}) as { roleId?: string; sameCompany?: boolean }
  roleForm.value = { roleId: rv?.roleId, sameCompany: rv?.sameCompany !== false }
  nodeVisible.value = true
}

function onApproverTypeChange(t: ApproverType) {
  const def = APPROVER_TYPES.find((x) => x.value === t)
  const names = APPROVER_TYPES.map((x) => x.defaultName)
  if (!nodeForm.value.name || names.includes(nodeForm.value.name)) nodeForm.value.name = def?.defaultName ?? '审批'
  nodeForm.value.approverValue = t === 'USER' ? [] : null
  nodeForm.value.multiMode = 'ANY'
}

function onUsersSelected(users: unknown) {
  const list = Array.isArray(users) ? users : users ? [users] : []
  for (const u of list as { id: string; realName: string }[]) userNames.value[u.id] = u.realName
}

function confirmNode() {
  const n = nodeForm.value
  if (!n.name.trim()) return ElMessage.warning('请输入节点名称')
  if (n.approverType === 'USER' && !(n.approverValue as string[])?.length) return ElMessage.warning('请选择审批人')
  if (n.approverType === 'USER' && (n.approverValue as string[]).length > 20) return ElMessage.warning('指定人员最多 20 人')
  if (n.approverType === 'ROLE') {
    if (!roleForm.value.roleId) return ElMessage.warning('请选择角色')
    n.approverValue = { roleId: roleForm.value.roleId, sameCompany: roleForm.value.sameCompany }
  }
  if (n.approverType === 'BIZ_USER' && !n.approverValue) return ElMessage.warning('请选择单据字段')
  if (!['USER', 'ROLE'].includes(n.approverType)) n.multiMode = 'ANY'
  const node: NodeDef = { name: n.name.trim(), approverType: n.approverType, approverValue: n.approverValue, multiMode: n.multiMode }
  const nodes = editableBranches.value[nodeTarget.value.branch].nodes
  if (nodeTarget.value.insert) nodes.splice(nodeTarget.value.index, 0, node)
  else nodes.splice(nodeTarget.value.index, 1, node)
  nodeVisible.value = false
}

function removeNode(branch: number, index: number) {
  editableBranches.value[branch].nodes.splice(index, 1)
}

// ---------- 历史版本 ----------
const historyVisible = ref(false)
const history = ref<HistoryRow[]>([])
const historyView = ref<Definition>()

async function openHistory() {
  if (!current.value) return
  history.value = await workflowApi.history(current.value.bizType)
  historyView.value = undefined
  historyVisible.value = true
}

async function viewVersion(h: HistoryRow) {
  historyView.value = await workflowApi.definition(h.id)
  await resolveUserNames(historyView.value)
}

onMounted(async () => {
  baseData.loadOrgTree()
  roles.value = await roleApi.simple().catch(() => [])
  await loadTypes()
  if (types.value.length) select(types.value[0])
})
</script>

<template>
  <ErpPage description="按单据字段选择审批路径：分支自上而下匹配，命中第一个即停止；每条路径的节点依次审批">
    <div class="erp-split">
      <ErpPanel title="单据类型" class="types" flush>
        <div class="types__search"><el-input v-model="keyword" placeholder="搜索单据类型" clearable prefix-icon="Search" /></div>
        <el-scrollbar class="types__scroll">
          <div v-for="g in groups" :key="g.name" class="types__group">
            <div class="types__module">{{ g.name }}</div>
            <button v-for="t in g.items" :key="t.bizType" type="button" :class="['types__item', { active: current?.bizType === t.bizType }]" @click="select(t)">
              <span class="types__name">{{ t.name }}<i v-if="t.hasDraft" class="draft-dot" title="有未发布的草稿" /></span>
              <ErpBadge :type="STATUS_LABEL[t.configStatus].type" :plain="STATUS_LABEL[t.configStatus].plain" :dot="t.configStatus !== 'NONE'">{{ STATUS_LABEL[t.configStatus].label }}</ErpBadge>
            </button>
          </div>
          <ErpEmpty v-if="!groups.length" description="暂无可审批的单据类型" compact />
        </el-scrollbar>
      </ErpPanel>

      <ErpPanel v-if="current" v-loading="loading" class="erp-split-main">
        <template #title>{{ current.name }} <span class="mono code">{{ current.bizType }}</span></template>
        <template #extra>
          <template v-if="!editing">
            <el-button v-if="active" @click="openHistory">历史版本</el-button>
            <el-button v-if="canUpdate" type="primary" @click="startEdit">{{ current.hasDraft ? '继续编辑草稿' : '编辑流程' }}</el-button>
          </template>
          <template v-else>
            <el-button @click="discard">放弃草稿</el-button>
            <el-button :loading="saving" @click="saveDraft()">保存草稿</el-button>
            <el-button type="primary" :loading="saving" @click="publishVisible = true">发布</el-button>
          </template>
        </template>

        <!-- 版本与规则 -->
        <div class="meta">
          <template v-if="!editing">
            <div v-if="active" class="meta__row">
              <span class="meta__label">启用审批</span>
              <el-switch :model-value="active.enabled" :disabled="!canUpdate" @change="(v: string | number | boolean) => toggleEnabled(!!v)" />
              <span class="erp-text-secondary">当前版本 V{{ active.defVersion }}（{{ formatDateTime(active.publishedAt, true) }} {{ active.publishedByName }} 发布）</span>
            </div>
          </template>
          <div v-else class="meta__row">
            <ErpBadge type="warning">编辑草稿 V{{ draft?.defVersion }}{{ draft?.basedOn ? `（基于 V${draft.basedOn}）` : '' }}</ErpBadge>
            <span class="erp-text-caption">发布后对新提交的单据生效，进行中的审批仍按原版本执行</span>
          </div>
          <div v-if="shown" class="meta__row rules">
            <span class="meta__label">规则</span>
            <template v-if="editing && draft">
              <el-checkbox v-model="draft.skipInitiator">审批人是发起人时自动通过</el-checkbox>
              <el-checkbox v-model="draft.skipDuplicate">连续相同审批人自动通过</el-checkbox>
              <span class="erp-text-secondary">找不到审批人时</span>
              <el-select v-model="draft.emptyPolicy" class="w160">
                <el-option value="TO_ADMIN" label="转给流程管理员" /><el-option value="AUTO_PASS" label="自动通过" />
              </el-select>
            </template>
            <span v-else class="erp-text-secondary">
              {{ shown.skipInitiator ? '审批人是发起人时自动通过' : '发起人也需审批' }} ·
              {{ shown.skipDuplicate ? '连续相同审批人自动通过' : '相同审批人重复审批' }} ·
              找不到审批人时{{ shown.emptyPolicy === 'AUTO_PASS' ? '自动通过' : '转给流程管理员' }}
            </span>
          </div>
        </div>

        <!-- 分支 -->
        <ErpEmpty v-if="!shown" description="尚未配置审批流程，提交即审核通过">
          <el-button v-if="canUpdate" type="primary" @click="startEdit">配置流程</el-button>
        </ErpEmpty>
        <div v-else class="branches">
          <section v-for="(b, bi) in shown.branches" :key="b.id ?? `new-${bi}`" class="branch">
            <header class="branch__head">
              <span class="branch__no num">{{ bi + 1 }}</span>
              <span class="branch__name">{{ b.name }}</span>
              <span v-if="b.isDefault" class="erp-text-caption">以上分支都不满足时</span>
              <span v-else class="branch__conds">{{ b.conditions.map(conditionText).join('，并且 ') }}</span>
              <span class="erp-spacer" />
              <template v-if="editing && !b.isDefault">
                <el-button link type="primary" @click="openBranch(bi)">编辑条件</el-button>
                <ErpIconButton icon="Up" tooltip="上移" :disabled="bi === 0" @click="moveBranch(bi, -1)" />
                <ErpIconButton icon="Down" tooltip="下移" :disabled="shown.branches[bi + 1]?.isDefault" @click="moveBranch(bi, 1)" />
                <ErpIconButton icon="Delete" tooltip="删除分支" @click="removeBranch(bi)" />
              </template>
            </header>
            <div class="chain">
              <span class="chain__endpoint">发起人</span>
              <template v-for="(n, ni) in b.nodes" :key="ni">
                <span class="chain__arrow">
                  <button v-if="editing" type="button" class="chain__add" title="在此插入节点" @click="openNode(bi, ni, true)"><el-icon><Plus /></el-icon></button>
                  <el-icon v-else><Right /></el-icon>
                </span>
                <div :class="['node', { editable: editing }]" @click="editing && openNode(bi, ni, false)">
                  <div class="node__name">{{ n.name }}<span v-if="multiLabel(n)" class="node__mode">{{ multiLabel(n) }}</span></div>
                  <div class="node__who">{{ nodeSummary(n) }}</div>
                  <button v-if="editing" type="button" class="node__remove" title="删除节点" @click.stop="removeNode(bi, ni)"><el-icon><Close /></el-icon></button>
                </div>
              </template>
              <span class="chain__arrow">
                <button v-if="editing" type="button" class="chain__add" title="添加节点" @click="openNode(bi, b.nodes.length, true)"><el-icon><Plus /></el-icon></button>
                <el-icon v-else><Right /></el-icon>
              </span>
              <span class="chain__endpoint">结束</span>
              <span v-if="editing && !b.nodes.length" class="text-danger erp-text-caption">至少需要一个审批节点</span>
            </div>
          </section>
          <el-button v-if="editing" icon="Plus" class="add-branch" :disabled="!fields.length" @click="openBranch(-1)">添加分支</el-button>
          <span v-if="editing && !fields.length" class="erp-text-caption">该单据类型没有声明条件字段，只能配置“其他情况”</span>
        </div>
      </ErpPanel>
      <ErpPanel v-else class="erp-split-main"><ErpEmpty description="请选择左侧单据类型" icon="Back" /></ErpPanel>
    </div>

    <!-- 分支条件 -->
    <el-dialog v-model="branchVisible" :title="branchIndex >= 0 ? '编辑分支条件' : '添加分支'" width="760px" :close-on-click-modal="false" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="分支名称" required><el-input v-model="branchForm.name" maxlength="64" placeholder="如 大额订单" /></el-form-item>
        <el-form-item label="条件" required>
          <div class="conds">
            <div v-for="(c, ci) in branchForm.conditions" :key="ci" class="cond">
              <span v-if="ci > 0" class="cond__and">并且</span>
              <el-select v-model="c.field" class="w160" @change="onFieldChange(c)">
                <el-option v-for="f in fields" :key="f.code" :value="f.code" :label="f.name" />
              </el-select>
              <el-select v-model="c.op" class="w120" @change="onOpChange(c)">
                <el-option v-for="o in OPS[fieldOf(c.field)?.type ?? 'STRING']" :key="o.value" :value="o.value" :label="o.label" />
              </el-select>
              <template v-if="fieldOf(c.field)?.type === 'NUMBER'">
                <template v-if="c.op === 'BETWEEN'">
                  <NumberInput :model-value="(c.value as string[] | undefined)?.[0]" :precision="4" trim-zeros allow-negative class="w120"
                               @update:model-value="c.value = [$event, (c.value as string[] | undefined)?.[1]]" />
                  <span class="erp-text-secondary">～</span>
                  <NumberInput :model-value="(c.value as string[] | undefined)?.[1]" :precision="4" trim-zeros allow-negative class="w120"
                               @update:model-value="c.value = [(c.value as string[] | undefined)?.[0], $event]" />
                </template>
                <NumberInput v-else v-model="c.value as string" :precision="4" trim-zeros allow-negative class="w200" />
              </template>
              <el-select v-else-if="fieldOf(c.field)?.type === 'ENUM'" v-model="c.value" :multiple="isMulti(c)" class="value">
                <el-option v-for="o in fieldOf(c.field)?.options" :key="o.value" :value="o.value" :label="o.label" />
              </el-select>
              <DictSelect v-else-if="fieldOf(c.field)?.type === 'DICT'" v-model="c.value as string" :type="fieldOf(c.field)!.dictType!" :multiple="isMulti(c)" class="value" />
              <OrgTreeSelect v-else-if="fieldOf(c.field)?.type === 'DEPT'" v-model="c.value as string[]" multiple class="value" />
              <UserSelect v-else-if="fieldOf(c.field)?.type === 'USER'" v-model="c.value as string[]" multiple class="value" @select="onUsersSelected" />
              <el-radio-group v-else-if="fieldOf(c.field)?.type === 'BOOL'" v-model="c.value">
                <el-radio :value="true">是</el-radio><el-radio :value="false">否</el-radio>
              </el-radio-group>
              <el-select v-else-if="isMulti(c)" v-model="c.value" multiple filterable allow-create default-first-option placeholder="输入后回车" class="value" />
              <el-input v-else v-model="c.value as string" class="value" />
              <ErpIconButton icon="Delete" tooltip="删除条件" :disabled="branchForm.conditions.length === 1" @click="branchForm.conditions.splice(ci, 1)" />
            </div>
            <el-button link type="primary" icon="Plus" @click="branchForm.conditions.push({ field: fields[0]?.code ?? '', op: defaultOp(fields[0]), value: undefined })">添加条件</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="branchVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmBranch">确定</el-button>
      </template>
    </el-dialog>

    <!-- 节点 -->
    <el-dialog v-model="nodeVisible" :title="nodeTarget.insert ? '添加审批节点' : '编辑审批节点'" width="640px" :close-on-click-modal="false" append-to-body>
      <el-form label-width="140px">
        <el-form-item label="审批人类型" required>
          <el-radio-group v-model="nodeForm.approverType" class="types-radio" @change="(v: string | number | boolean | undefined) => onApproverTypeChange(v as ApproverType)">
            <el-radio v-for="t in APPROVER_TYPES" :key="t.value" :value="t.value">{{ t.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="节点名称" required><el-input v-model="nodeForm.name" maxlength="32" /></el-form-item>
        <el-form-item v-if="nodeForm.approverType === 'USER'" label="指定人员" required>
          <UserSelect v-model="nodeForm.approverValue as string[]" multiple placeholder="最多 20 人" @select="onUsersSelected" />
        </el-form-item>
        <template v-if="nodeForm.approverType === 'ROLE'">
          <el-form-item label="指定角色" required>
            <el-select v-model="roleForm.roleId" filterable><el-option v-for="r in roles" :key="r.id" :value="r.id" :label="r.name" /></el-select>
          </el-form-item>
          <el-form-item label="仅限发起人所在公司"><el-switch v-model="roleForm.sameCompany" /></el-form-item>
        </template>
        <el-form-item v-if="nodeForm.approverType === 'BIZ_USER'" label="单据字段" required>
          <el-select v-model="nodeForm.approverValue as string">
            <el-option v-for="f in current?.userFields" :key="f.code" :value="f.code" :label="f.name" />
          </el-select>
          <div v-if="!current?.userFields.length" class="form-tip">该单据类型没有声明用户字段</div>
        </el-form-item>
        <el-form-item v-if="['USER', 'ROLE'].includes(nodeForm.approverType)" label="多人审批方式" required>
          <el-radio-group v-model="nodeForm.multiMode">
            <el-radio value="ANY">或签（任一人通过即可）</el-radio>
            <el-radio value="ALL">会签（所有人都要通过）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="['DEPT_LEADER', 'UPPER_DEPT_LEADER'].includes(nodeForm.approverType)">
          <span class="form-tip">按发起人主部门查找；该部门没有负责人时向上找最近一级有负责人的部门</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmNode">确定</el-button>
      </template>
    </el-dialog>

    <!-- 发布 -->
    <el-dialog v-model="publishVisible" title="发布流程" width="480px" :close-on-click-modal="false" append-to-body>
      <p class="erp-text-secondary publish-tip">发布后对新提交的单据生效；进行中的审批仍按原版本执行。</p>
      <el-input v-model="publishRemark" type="textarea" :rows="3" maxlength="256" show-word-limit placeholder="版本说明（选填），如：大额订单增加总经理审批" />
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="publish">发布</el-button>
      </template>
    </el-dialog>

    <!-- 历史版本 -->
    <el-drawer v-model="historyVisible" :title="`历史版本 - ${current?.name ?? ''}`" size="720px" append-to-body>
      <el-table :data="history">
        <el-table-column label="版本" width="80"><template #default="{ row }">V{{ row.defVersion }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }"><ErpBadge :type="row.status === 'ACTIVE' ? 'success' : 'info'" :plain="row.status !== 'ACTIVE'">{{ row.status === 'ACTIVE' ? '当前' : '已归档' }}</ErpBadge></template></el-table-column>
        <el-table-column prop="publishedByName" label="发布人" width="100" />
        <el-table-column label="发布时间" width="150"><template #default="{ row }">{{ formatDateTime(row.publishedAt, true) }}</template></el-table-column>
        <el-table-column prop="remark" label="说明" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="70"><template #default="{ row }"><el-button link type="primary" @click="viewVersion(row as HistoryRow)">查看</el-button></template></el-table-column>
      </el-table>
      <div v-if="historyView" class="history-view">
        <div class="group-title">V{{ historyView.defVersion }}</div>
        <div v-for="(b, bi) in historyView.branches" :key="bi" class="history-branch">
          <div><b>{{ bi + 1 }}. {{ b.name }}</b> <span class="erp-text-secondary">{{ b.isDefault ? '其他情况' : b.conditions.map(conditionText).join('，并且 ') }}</span></div>
          <div class="erp-text-secondary">发起人 → {{ b.nodes.map((n) => `${n.name}（${nodeSummary(n)}）`).join(' → ') }} → 结束</div>
        </div>
      </div>
    </el-drawer>
  </ErpPage>
</template>

<style scoped>
.types { width: 260px; flex-shrink: 0; }
.types__search { padding: 12px 12px 4px; }
.types__scroll { height: calc(100vh - 280px); min-height: 280px; }
.types__group { padding: 4px 8px; }
.types__module { padding: 8px 8px 4px; font-size: var(--erp-font-size-caption); color: var(--erp-color-text-tertiary); }
.types__item {
  width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 8px; height: 36px; padding: 0 8px;
  border: none; background: transparent; border-radius: var(--erp-radius-control); cursor: pointer; font-family: inherit;
  font-size: var(--erp-font-size-body); color: var(--erp-color-text); text-align: left;
  transition: background-color var(--erp-duration-fast) var(--erp-ease);
}
.types__item:hover { background: var(--erp-color-hover); }
.types__item.active { background: var(--erp-color-primary-bg); color: var(--el-color-primary); font-weight: var(--erp-font-weight-medium); }
.types__name { display: inline-flex; align-items: center; gap: 6px; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.draft-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--el-color-warning); flex-shrink: 0; }
.code { font-size: var(--erp-font-size-caption); color: var(--erp-color-text-tertiary); font-weight: var(--erp-font-weight-regular); margin-left: 6px; }
.meta { display: flex; flex-direction: column; gap: 10px; padding-bottom: 16px; margin-bottom: 16px; border-bottom: 1px solid var(--erp-color-border); }
.meta__row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.meta__label { color: var(--erp-color-text-secondary); width: 64px; }
.branches { display: flex; flex-direction: column; gap: 12px; align-items: flex-start; }
.branch { width: 100%; border: 1px solid var(--erp-color-border); border-radius: var(--erp-radius-card); padding: 12px 16px 16px; }
.branch__head { display: flex; align-items: center; gap: 8px; min-height: 32px; }
.branch__no {
  width: 20px; height: 20px; border-radius: 50%; background: var(--erp-color-hover); color: var(--erp-color-text-secondary);
  display: inline-flex; align-items: center; justify-content: center; font-size: var(--erp-font-size-caption);
}
.branch__name { font-weight: var(--erp-font-weight-semibold); }
.branch__conds { color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-secondary); }
.chain { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.chain__endpoint {
  padding: 4px 10px; border-radius: var(--erp-radius-control); background: var(--erp-color-surface-subtle);
  font-size: var(--erp-font-size-secondary); color: var(--erp-color-text-secondary);
}
.chain__arrow { display: inline-flex; align-items: center; color: var(--erp-color-text-disabled); }
.chain__add {
  width: 22px; height: 22px; border-radius: 50%; border: 1px dashed var(--erp-color-border-strong); background: var(--erp-color-surface);
  color: var(--erp-color-text-secondary); display: inline-flex; align-items: center; justify-content: center; cursor: pointer; padding: 0;
  transition: border-color var(--erp-duration-fast) var(--erp-ease), color var(--erp-duration-fast) var(--erp-ease);
}
.chain__add:hover { border-color: var(--el-color-primary); color: var(--el-color-primary); }
.node {
  position: relative; min-width: 140px; max-width: 240px; padding: 8px 12px; border: 1px solid var(--erp-color-border);
  border-radius: var(--erp-radius-control); background: var(--erp-color-surface);
}
.node.editable { cursor: pointer; transition: border-color var(--erp-duration-fast) var(--erp-ease); }
.node.editable:hover { border-color: var(--el-color-primary); }
.node__name { font-weight: var(--erp-font-weight-medium); display: flex; align-items: center; gap: 6px; }
.node__mode { font-size: var(--erp-font-size-caption); color: var(--erp-color-text-tertiary); font-weight: var(--erp-font-weight-regular); }
.node__who { font-size: var(--erp-font-size-caption); color: var(--erp-color-text-secondary); margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.node__remove {
  position: absolute; top: -8px; right: -8px; width: 18px; height: 18px; border-radius: 50%; border: 1px solid var(--erp-color-border);
  background: var(--erp-color-surface); color: var(--erp-color-text-tertiary); display: none; align-items: center; justify-content: center;
  cursor: pointer; padding: 0; font-size: var(--erp-font-size-caption);
}
.node:hover .node__remove { display: inline-flex; }
.node__remove:hover { color: var(--el-color-danger); border-color: var(--el-color-danger); }
.conds { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.cond { display: flex; align-items: center; gap: 8px; }
.cond__and { font-size: var(--erp-font-size-caption); color: var(--erp-color-text-tertiary); width: 28px; }
.cond:first-child { padding-left: 36px; }
.cond .value { flex: 1; min-width: 160px; }
.types-radio { display: grid; grid-template-columns: repeat(3, auto); row-gap: 4px; }
.publish-tip { margin: 0 0 12px; }
.history-view { margin-top: 20px; }
.history-branch { padding: 8px 0; border-bottom: 1px solid var(--erp-color-border-light); line-height: 1.8; }
</style>
