<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction } from '@/components'
import { download } from '@/api/http'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatPrice, formatQty } from '@/utils/format'
import {
  bomApi, BOM_STATUS, ISSUE_METHOD_OPTIONS,
  type BomDetail, type BomRow, type CompareRow, type ExplodeRow, type WhereUsedRow
} from '../api/bom'
import { labelOf, MATERIAL_TYPE_OPTIONS, SOURCE_TYPE_OPTIONS } from '../api/material'

defineOptions({ name: 'EngBomDetail' })

/** BOM 详情（需求 05-03 4.3，T5）：明细、多级展开、反查、版本比较、成本、审批记录、操作日志、附件 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => String(route.params.id))
const d = ref<BomDetail>()
const activeTab = ref('lines')

async function load() {
  d.value = await bomApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}

const s = computed(() => d.value?.status)
const isDefault = computed(() => !!d.value?.isDefault)

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'eng:bom:delete', visible: () => s.value === 'DRAFT', confirm: `确定删除 ${d.value?.docNo} 吗？删除后不可恢复。`,
    handler: async () => {
      await bomApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/engineering/bom')
    } },
  { key: 'disable', label: '停用', permission: 'eng:bom:disable', visible: () => s.value === 'APPROVED' && !isDefault.value,
    confirm: '停用后不能恢复，需要时可通过“新建版本”复制。确定停用吗？', handler: () => run(bomApi.disable(id.value), '已停用') },
  { key: 'unapprove', label: '反审核', permission: 'eng:bom:unapprove', visible: () => s.value === 'APPROVED' && !isDefault.value, reasonRequired: true,
    confirm: '反审核后 BOM 回到草稿，可修改后重新提交。', handler: (reason) => run(bomApi.unapprove(id.value, reason!), '已反审核') },
  { key: 'newVersion', label: '新建版本', permission: 'eng:bom:create', visible: () => s.value === 'APPROVED' || s.value === 'CLOSED',
    handler: async () => {
      const nid = await bomApi.newVersion(id.value)
      ElMessage.success('已复制为新版本，请填写版本说明')
      router.push(`/engineering/bom/${nid}/edit`)
    } },
  { key: 'setDefault', label: '设为默认', permission: 'eng:bom:set-default', visible: () => s.value === 'APPROVED' && !isDefault.value,
    confirm: '设为默认后，MRP、新建生产订单和成本计算将使用此版本，原默认版本取消默认。确定吗？', handler: () => run(bomApi.setDefault(id.value), '已设为默认版本') },
  { key: 'edit', label: '编辑', permission: 'eng:bom:update', visible: () => s.value === 'DRAFT', handler: () => router.push(`/engineering/bom/${id.value}/edit`) },
  { key: 'submit', label: '提交', type: 'primary', permission: 'eng:bom:submit', visible: () => s.value === 'DRAFT',
    handler: async () => {
      const st = await bomApi.submit(id.value)
      ElMessage.success(st === 'APPROVED' ? '提交成功，已审核' : '已提交审批')
      load()
    } }
])

async function run(p: Promise<unknown>, msg: string) {
  await p
  ElMessage.success(msg)
  load()
}

const steps = [
  { status: 'DRAFT', label: '草稿' },
  { status: 'PENDING_APPROVAL', label: '待审批' },
  { status: 'APPROVED', label: '已审核' }
]

const pct = (v?: string) => (v === undefined || v === null ? '' : `${Number((Number(v) * 100).toFixed(4))}%`)
const issue = (v?: string) => labelOf(ISSUE_METHOD_OPTIONS, v)

// ---------- 多级展开 ----------
const explodeQty = ref('1')
const explodeLevels = ref(0)
const explodeRows = ref<ExplodeRow[]>([])
const exploding = ref(false)
async function explode() {
  exploding.value = true
  try {
    explodeRows.value = await bomApi.explode(id.value, explodeQty.value || '1', explodeLevels.value)
  } finally {
    exploding.value = false
  }
}
async function exportExplode() {
  await download('/engineering/boms/export', { mode: 'MULTI', ids: id.value }, `${d.value?.docNo}_多级展开.xlsx`)
}

// ---------- 反查 ----------
const whereUsed = ref<WhereUsedRow[]>([])
const loadingUsed = ref(false)
async function loadWhereUsed() {
  if (!d.value) return
  loadingUsed.value = true
  try {
    whereUsed.value = await bomApi.whereUsed(d.value.materialId)
  } finally {
    loadingUsed.value = false
  }
}

// ---------- 版本比较 ----------
const versions = ref<BomRow[]>([])
const compareWith = ref<string>()
const compareRows = ref<CompareRow[]>([])
const onlyChanges = ref(true)
async function loadVersions() {
  if (!d.value) return
  versions.value = (await bomApi.page({ materialId: d.value.materialId, pageNo: 1, pageSize: 100 })).list.filter((b) => b.id !== id.value)
  if (!compareWith.value && versions.value.length) {
    const prev = versions.value.filter((b) => b.version < d.value!.version).sort((a, b) => b.version - a.version)[0]
    compareWith.value = (prev ?? versions.value[0]).id
    compare()
  }
}
async function compare() {
  if (!compareWith.value) return
  compareRows.value = (await bomApi.compare(compareWith.value, id.value)).rows
}
const shownCompare = computed(() => compareRows.value.filter((r) => !onlyChanges.value || r.change !== 'SAME'))
const compareLeftNo = computed(() => versions.value.find((v) => v.id === compareWith.value)?.docNo ?? '')
const CHANGE_LABEL: Record<string, string> = { ADDED: '新增', REMOVED: '删除', CHANGED: '变化', SAME: '相同' }
const compareClass = ({ row }: { row: unknown }) => `chg-${(row as CompareRow).change.toLowerCase()}`
function diff(r: CompareRow, field: 'qtyPer' | 'scrapRate' | 'positionNo' | 'issueMethod') {
  const fmt = (v?: string) => (field === 'scrapRate' ? pct(v) : field === 'issueMethod' ? issue(v) : field === 'qtyPer' ? (v ? formatQty(v) : '') : v ?? '')
  const l = fmt(r.left?.[field] as string | undefined)
  const rt = fmt(r.right?.[field] as string | undefined)
  if (r.change === 'ADDED') return rt
  if (r.change === 'REMOVED') return l
  return r.changedFields.includes(field) ? `${l || '空'} → ${rt || '空'}` : rt
}

// ---------- 成本 ----------
const canCost = computed(() => me.hasPermission('eng:bom:cost'))
const cost = ref<{ total: string; missingCount: number; rows: ExplodeRow[] }>()
async function loadCost() {
  cost.value = await bomApi.cost(id.value)
}

watch(activeTab, (t) => {
  if (t === 'explode' && !explodeRows.value.length) explode()
  if (t === 'used' && !whereUsed.value.length) loadWhereUsed()
  if (t === 'compare' && !versions.value.length) loadVersions()
  if (t === 'cost' && !cost.value) loadCost()
})

const asExplode = (r: unknown) => r as ExplodeRow
const asUsed = (r: unknown) => r as WhereUsedRow
const asCmp = (r: unknown) => r as CompareRow

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d ? `${d.materialCode} ${d.materialName} V${d.version}` : 'BOM'" :status="d?.status" :status-map="BOM_STATUS" :actions="actions"
                     @back="router.push('/engineering/bom')">
        <template #extra><ErpBadge v-if="isDefault" type="success">默认</ErpBadge></template>
        <template #actions-prefix>
          <ApprovalActions v-if="d" biz-type="ENG_BOM" :biz-id="id" @changed="load" />
          <PrintButton v-if="d && d.status !== 'VOIDED'" biz-type="ENG_BOM" :ids="[id]" permission="eng:bom:print" />
        </template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <el-alert v-if="d.status === 'CLOSED'" type="info" :closable="false" show-icon title="此版本已停用，不能恢复；需要时可通过“新建版本”复制" class="closed" />
        <DocSteps v-else :steps="steps" :current="d.status" />
        <el-descriptions :column="3" class="head">
          <el-descriptions-item label="父件">
            <el-link type="primary" underline="never" @click="router.push(`/engineering/material/${d.materialId}`)">{{ d.materialCode }}</el-link> {{ d.materialName }}
          </el-descriptions-item>
          <el-descriptions-item label="规格">{{ d.materialSpec || '-' }}</el-descriptions-item>
          <el-descriptions-item label="单位 / 类型">{{ d.uom }} / {{ labelOf(MATERIAL_TYPE_OPTIONS, d.materialType) }}</el-descriptions-item>
          <el-descriptions-item label="基数">{{ formatQty(d.baseQty) }} {{ d.uom }}</el-descriptions-item>
          <el-descriptions-item label="默认版本">{{ isDefault ? `是（${d.effectiveDate} 起）` : '否' }}</el-descriptions-item>
          <el-descriptions-item label="复制自">
            <el-link v-if="d.copiedFromId" type="primary" underline="never" @click="router.push(`/engineering/bom/${d.copiedFromId}`)">{{ d.copiedFromNo }}</el-link>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="版本说明" :span="3">{{ d.description || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建">{{ d.createdByName ?? '-' }} {{ d.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="更新">{{ d.updatedByName ?? '-' }} {{ d.updatedAt }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane :label="`明细(${d.lines.length})`" name="lines">
            <el-table :data="d.lines" row-key="id">
              <el-table-column type="expand" width="36">
                <template #default="{ row }">
                  <div class="subs">
                    <div class="group-title">替代料</div>
                    <el-table v-if="row.substitutes.length" :data="row.substitutes" size="small">
                      <el-table-column prop="priority" label="优先级" width="80" align="right" />
                      <el-table-column prop="code" label="编码" width="140" />
                      <el-table-column prop="name" label="名称" min-width="160" />
                      <el-table-column prop="spec" label="规格" min-width="160" />
                      <el-table-column label="比例" width="160"><template #default="{ row: s }">1 : {{ Number(s.ratio) }} {{ s.uom }}</template></el-table-column>
                      <el-table-column prop="remark" label="备注" min-width="120" />
                    </el-table>
                    <span v-else class="text-muted">没有替代料</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="lineNo" label="行" width="50" align="right" />
              <el-table-column label="子件编码" width="140">
                <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/engineering/material/${row.componentId}`)">{{ row.componentCode }}</el-link></template>
              </el-table-column>
              <el-table-column prop="componentName" label="名称" min-width="160" show-overflow-tooltip />
              <el-table-column prop="componentSpec" label="规格" min-width="160" show-overflow-tooltip />
              <el-table-column label="类型" width="70"><template #default="{ row }">{{ labelOf(MATERIAL_TYPE_OPTIONS, row.componentType) }}</template></el-table-column>
              <el-table-column prop="uom" label="单位" width="60" />
              <el-table-column label="用量" width="100" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.qtyPer) }}</span></template></el-table-column>
              <el-table-column label="损耗率" width="80" align="right"><template #default="{ row }"><span class="num">{{ pct(row.scrapRate) }}</span></template></el-table-column>
              <el-table-column prop="positionNo" label="位号" min-width="140" show-overflow-tooltip />
              <el-table-column label="发料" width="70"><template #default="{ row }">{{ issue(row.issueMethod) }}</template></el-table-column>
              <el-table-column label="关键件" width="70" align="center"><template #default="{ row }">{{ row.isKey ? '是' : '' }}</template></el-table-column>
              <el-table-column label="替代" width="60" align="right"><template #default="{ row }">{{ row.substitutes.length || '' }}</template></el-table-column>
              <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="多级展开" name="explode">
            <div class="tab-toolbar">
              <span>父件数量</span><NumberInput v-model="explodeQty" :precision="4" trim-zeros class="w120" />
              <span>展开层数</span>
              <el-select v-model="explodeLevels" class="w120">
                <el-option :value="0" label="全部" /><el-option v-for="n in 5" :key="n" :value="n" :label="`${n} 层`" />
              </el-select>
              <el-button type="primary" :loading="exploding" @click="explode">计算</el-button>
              <span class="spacer" />
              <el-button v-perm="'eng:bom:export'" icon="Download" @click="exportExplode">导出</el-button>
            </div>
            <el-table v-loading="exploding" :data="explodeRows" row-key="key" default-expand-all :tree-props="{ children: 'children' }">
              <el-table-column label="层级" min-width="140"><template #default="{ row }"><span class="mono">{{ asExplode(row).path }}</span></template></el-table-column>
              <el-table-column label="子件编码" width="130">
                <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/engineering/material/${asExplode(row).componentId}`)">{{ asExplode(row).code }}</el-link></template>
              </el-table-column>
              <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
              <el-table-column prop="spec" label="规格" min-width="140" show-overflow-tooltip />
              <el-table-column prop="uom" label="单位" width="60" />
              <el-table-column label="单层用量" width="100" align="right"><template #default="{ row }"><span class="num">{{ formatQty(asExplode(row).qtyPer) }}</span></template></el-table-column>
              <el-table-column label="损耗率" width="80" align="right"><template #default="{ row }"><span class="num">{{ pct(asExplode(row).scrapRate) }}</span></template></el-table-column>
              <el-table-column label="累计用量" width="110" align="right"><template #default="{ row }"><span class="num">{{ formatQty(asExplode(row).totalQtyPer, 6) }}</span></template></el-table-column>
              <el-table-column label="需求量" width="110" align="right"><template #default="{ row }"><span class="num strong">{{ formatQty(asExplode(row).requiredQty) }}</span></template></el-table-column>
              <el-table-column label="取得方式" width="80"><template #default="{ row }">{{ labelOf(SOURCE_TYPE_OPTIONS, asExplode(row).sourceType) }}</template></el-table-column>
              <el-table-column label="虚拟件" width="70" align="center"><template #default="{ row }"><ErpBadge v-if="asExplode(row).phantom" type="info" plain>虚拟</ErpBadge></template></el-table-column>
              <el-table-column label="子 BOM" width="80">
                <template #default="{ row }">
                  <el-link v-if="asExplode(row).bomId" type="primary" underline="never" @click="router.push(`/engineering/bom/${asExplode(row).bomId}`)">V{{ asExplode(row).bomVersion }}</el-link>
                </template>
              </el-table-column>
              <template #empty><ErpEmpty compact description="没有明细" /></template>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="反查" name="used">
            <p class="tab-tip">父件 {{ d.materialCode }} 被以下 BOM 使用（多级向上，“顶层”为最终成品）</p>
            <el-table v-loading="loadingUsed" :data="whereUsed" row-key="key" default-expand-all :tree-props="{ children: 'children' }">
              <el-table-column label="BOM" min-width="200">
                <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/engineering/bom/${asUsed(row).bomId}`)">{{ asUsed(row).docNo }}</el-link></template>
              </el-table-column>
              <el-table-column prop="materialName" label="父件名称" min-width="160" show-overflow-tooltip />
              <el-table-column label="类型" width="80"><template #default="{ row }">{{ labelOf(MATERIAL_TYPE_OPTIONS, asUsed(row).materialType) }}</template></el-table-column>
              <el-table-column label="用量" width="110" align="right"><template #default="{ row }"><span class="num">{{ formatQty(asUsed(row).qtyPer) }} {{ asUsed(row).uom }}</span></template></el-table-column>
              <el-table-column label="默认" width="70" align="center"><template #default="{ row }"><ErpBadge v-if="asUsed(row).isDefault" type="success">默认</ErpBadge></template></el-table-column>
              <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="asUsed(row).status" :map="BOM_STATUS" /></template></el-table-column>
              <el-table-column label="" width="70" align="center"><template #default="{ row }"><ErpBadge v-if="asUsed(row).top" type="primary" plain>顶层</ErpBadge></template></el-table-column>
              <template #empty><ErpEmpty compact description="没有 BOM 使用该父件" /></template>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="版本比较" name="compare">
            <div class="tab-toolbar">
              <span>与</span>
              <el-select v-model="compareWith" class="w240" placeholder="选择另一个版本" @change="compare">
                <el-option v-for="v in versions" :key="v.id" :value="v.id" :label="`${v.docNo}${v.isDefault ? '（默认）' : ''} ${BOM_STATUS[v.status]?.label ?? ''}`" />
              </el-select>
              <span>比较（左：{{ compareLeftNo || '-' }}，右：{{ d.docNo }}）</span>
              <el-checkbox v-model="onlyChanges">只看差异</el-checkbox>
            </div>
            <el-table :data="shownCompare" :row-class-name="compareClass">
              <el-table-column label="变化" width="70"><template #default="{ row }">{{ CHANGE_LABEL[row.change] }}</template></el-table-column>
              <el-table-column prop="code" label="子件编码" width="140" />
              <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
              <el-table-column prop="uom" label="单位" width="60" />
              <el-table-column label="用量（每 1 个父件）" min-width="150" align="right"><template #default="{ row }"><span class="num">{{ diff(asCmp(row), 'qtyPer') }}</span></template></el-table-column>
              <el-table-column label="损耗率" min-width="120" align="right"><template #default="{ row }"><span class="num">{{ diff(asCmp(row), 'scrapRate') }}</span></template></el-table-column>
              <el-table-column label="位号" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ diff(asCmp(row), 'positionNo') }}</template></el-table-column>
              <el-table-column label="发料" width="110"><template #default="{ row }">{{ diff(asCmp(row), 'issueMethod') }}</template></el-table-column>
              <template #empty><ErpEmpty compact :description="versions.length ? '两个版本没有差异' : '该父件只有一个版本'" /></template>
            </el-table>
          </el-tab-pane>

          <el-tab-pane v-if="canCost" label="成本" name="cost">
            <template v-if="cost">
              <div class="cost-summary">
                <span>材料标准成本合计（每 1 {{ d.uom }}）：<strong class="num">{{ formatPrice(cost.total) }}</strong></span>
                <span v-if="cost.missingCount" class="text-danger">{{ cost.missingCount }} 个子件缺少标准成本（标红），合计偏低</span>
              </div>
              <el-table :data="cost.rows" row-key="key" default-expand-all :tree-props="{ children: 'children' }">
                <el-table-column label="层级" min-width="120"><template #default="{ row }"><span class="mono">{{ asExplode(row).path }}</span></template></el-table-column>
                <el-table-column prop="code" label="子件编码" width="130" />
                <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
                <el-table-column prop="uom" label="单位" width="60" />
                <el-table-column label="累计用量" width="110" align="right"><template #default="{ row }"><span class="num">{{ formatQty(asExplode(row).totalQtyPer, 6) }}</span></template></el-table-column>
                <el-table-column label="标准成本" width="120" align="right">
                  <template #default="{ row }">
                    <span v-if="asExplode(row).costMissing" class="text-danger">缺少</span>
                    <span v-else class="num">{{ asExplode(row).unitCost ? formatPrice(asExplode(row).unitCost!) : '' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="材料成本" width="130" align="right"><template #default="{ row }"><span class="num">{{ asExplode(row).costAmount ? formatPrice(asExplode(row).costAmount!) : '' }}</span></template></el-table-column>
              </el-table>
            </template>
            <el-skeleton v-else :rows="4" animated />
          </el-tab-pane>

          <el-tab-pane label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="ENG_BOM" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="ENG_BOM" :biz-id="id" :status-map="BOM_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="ENG_BOM" :biz-id="id" :editable="d.status === 'DRAFT'" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
.closed { margin-bottom: var(--erp-space-4); }
.head { margin-top: var(--erp-space-4); }
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
.subs { padding: var(--erp-space-2) var(--erp-space-4) var(--erp-space-2) var(--erp-space-8); }
.subs .group-title { margin-top: 0; }
.tab-toolbar { display: flex; align-items: center; gap: var(--erp-space-2); margin-bottom: var(--erp-space-3); flex-wrap: wrap; }
.tab-toolbar .spacer { flex: 1; }
.tab-tip { margin: 0 0 var(--erp-space-3); color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-secondary); }
.strong { font-weight: var(--erp-font-weight-medium); }
.cost-summary { display: flex; gap: var(--erp-space-5); align-items: center; margin-bottom: var(--erp-space-3); }
:deep(.chg-added) { background: var(--erp-color-success-bg); }
:deep(.chg-removed) { background: var(--erp-color-error-bg); color: var(--erp-color-text-secondary); text-decoration: line-through; }
:deep(.chg-changed) { background: var(--erp-color-warning-bg); }
</style>
