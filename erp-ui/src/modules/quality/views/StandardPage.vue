<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { RowAction, SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import {
  AQLS, ENABLE, INSPECT_TYPE, INSPECTION_LEVELS, ITEM_TYPE, LEVELS, PLAN_TYPE, STD_STATUS, basicApi, joinList, labelOf, optionsOf, standardApi,
  type DefectCodeRow, type ItemLibRow, type SamplingResult, type SamplingRow, type StandardRow
} from '../api/quality'

defineOptions({ name: 'QcStandardPage' })

/** 检验基础数据（需求 10-01 第 5 节，T1 + T2 弹窗）：检验标准、检验项目库、抽样方案（计算预览）、缺陷代码 */
const router = useRouter()
const tab = ref('standard')
const typeOptions = Object.entries(INSPECT_TYPE).filter(([k]) => k !== 'RECHECK').map(([value, label]) => ({ value, label }))

// ==================== 检验标准 ====================
type StdQuery = { code?: string; name?: string; inspectType?: string; materialId?: string; statuses?: string[] }
const std = useListPage<StdQuery, StandardRow>({
  api: (q) => standardApi.page({ ...q, statuses: joinList(q.statuses) } as never),
  defaultQuery: () => ({ statuses: ['EFFECTIVE', 'DRAFT'] }),
  refreshOnActivated: true
})
const stdFields: SearchField[] = [
  { prop: 'code', label: '编号', upper: true },
  { prop: 'name', label: '名称' },
  { prop: 'inspectType', label: '检验类型', type: 'select', options: typeOptions },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(STD_STATUS), multiple: true }
]
const stdColumns: TableColumn<StandardRow>[] = [
  { prop: 'code', label: '编号', width: 120, type: 'link', onClick: (r) => router.push(`/quality/standard/${r.id}`) },
  { prop: 'name', label: '名称', minWidth: 160 },
  { prop: 'inspectType', label: '检验类型', width: 90, formatter: (r) => INSPECT_TYPE[r.inspectType] },
  { prop: 'scope', label: '适用', minWidth: 160, formatter: (r) => (r.scopeType === 'MATERIAL' ? `${r.materialCode} ${r.materialName ?? ''}` : `类别：${r.categoryName}`) },
  { prop: 'operation', label: '工序', width: 90, dictType: 'eng_operation', type: 'dict' },
  { prop: 'samplingPlanName', label: '抽样方案', minWidth: 150 },
  { prop: 'stdVersion', label: '版本', width: 60, formatter: (r) => `V${r.stdVersion}` },
  { prop: 'itemCount', label: '项目数', width: 70, align: 'right' },
  { prop: 'status', label: '状态', width: 70, type: 'status', statusMap: STD_STATUS },
  { prop: 'updatedAt', label: '更新时间', width: 140, type: 'datetime' }
]
const stdActions = (r: StandardRow): RowAction[] => [
  { label: '编辑', permission: 'qc:standard:update', visible: r.status === 'DRAFT', handler: () => router.push(`/quality/standard/${r.id}`) },
  { label: '生效', permission: 'qc:standard:approve', visible: r.status === 'DRAFT', confirm: '生效后同一适用范围的旧版本将自动作废。', handler: async () => {
    await standardApi.activate(r.id)
    ElMessage.success('已生效')
    std.load()
  } },
  { label: '新版本', permission: 'qc:standard:update', visible: r.status === 'EFFECTIVE', handler: async () => {
    const id = await standardApi.newVersion(r.id)
    router.push(`/quality/standard/${id}`)
  } },
  { label: '作废', permission: 'qc:standard:delete', visible: r.status === 'EFFECTIVE', confirm: `确定作废「${r.code} V${r.stdVersion}」吗？`, handler: async () => {
    await standardApi.obsolete(r.id)
    std.load()
  } },
  { label: '删除', permission: 'qc:standard:delete', visible: r.status === 'DRAFT', danger: true, confirm: `确定删除「${r.code} V${r.stdVersion}」吗？`, handler: async () => {
    await standardApi.remove(r.id)
    std.load()
  } }
]
const asStd = (r: unknown) => r as StandardRow

// ==================== 项目库 ====================
const items = useListPage<{ keyword?: string; itemType?: string }, ItemLibRow>({ api: (q) => basicApi.items(q as never), immediate: true })
const itemColumns: TableColumn<ItemLibRow>[] = [
  { prop: 'code', label: '编码', width: 120 },
  { prop: 'name', label: '名称', width: 130 },
  { prop: 'itemType', label: '类型', width: 70, formatter: (r) => labelOf(ITEM_TYPE, r.itemType) },
  { prop: 'method', label: '方法', width: 90, type: 'dict', dictType: 'qc_inspection_method' },
  { prop: 'unit', label: '单位', width: 60 },
  { prop: 'defectLevel', label: '缺陷等级', width: 80 },
  { prop: 'tool', label: '工具', width: 100 },
  { prop: 'description', label: '检验方法说明', minWidth: 200 },
  { prop: 'status', label: '状态', width: 70, type: 'status', statusMap: ENABLE }
]
const itemVisible = ref(false)
const itemForm = ref<Partial<ItemLibRow> & { version?: number }>({})
function editItem(r?: ItemLibRow) {
  itemForm.value = r ? { ...r } : { itemType: 'QUALITATIVE', method: 'VISUAL', defectLevel: 'MI', status: 'ENABLED' }
  itemVisible.value = true
}
async function saveItem() {
  await basicApi.saveItem(itemForm.value.id, itemForm.value)
  itemVisible.value = false
  ElMessage.success('已保存')
  items.load()
}
const itemActions = (r: ItemLibRow): RowAction[] => [
  { label: '编辑', permission: 'qc:defect-code:manage', handler: () => editItem(r) },
  { label: '删除', permission: 'qc:defect-code:manage', danger: true, confirm: `确定删除「${r.name}」吗？`, handler: async () => {
    await basicApi.deleteItem(r.id)
    items.load()
  } }
]

// ==================== 抽样方案 ====================
const plans = useListPage<{ keyword?: string; planType?: string }, SamplingRow>({ api: (q) => basicApi.plans(q as never) })
const planColumns: TableColumn<SamplingRow>[] = [
  { prop: 'code', label: '编码', width: 130 },
  { prop: 'name', label: '名称', minWidth: 180 },
  { prop: 'planType', label: '类型', width: 130, formatter: (r) => labelOf(PLAN_TYPE, r.planType) },
  { prop: 'inspectionLevel', label: '检验水平', width: 80 },
  { prop: 'aql', label: 'AQL（CR / MA / MI）', width: 160, formatter: (r) => (r.planType === 'GB2828' ? `${r.aqlCr ?? '-'} / ${r.aqlMa ?? '-'} / ${r.aqlMi ?? '-'}` : '-') },
  { prop: 'fixedQty', label: '固定数量', width: 80, align: 'right' },
  { prop: 'status', label: '状态', width: 70, type: 'status', statusMap: ENABLE },
  { prop: 'remark', label: '说明', minWidth: 140 }
]
const planVisible = ref(false)
const planForm = ref<Partial<SamplingRow> & { version?: number }>({})
const previewLot = ref('1000')
const preview = ref<SamplingResult>()
function editPlan(r?: SamplingRow) {
  planForm.value = r ? { ...r } : { planType: 'GB2828', inspectionLevel: 'II', aqlCr: '0', aqlMa: '0.65', aqlMi: '1.5', status: 'ENABLED' }
  preview.value = undefined
  planVisible.value = true
}
async function doPreview() {
  preview.value = await basicApi.preview({ ...planForm.value, planId: undefined, lotQty: previewLot.value })
}
async function savePlan() {
  await basicApi.savePlan(planForm.value.id, planForm.value)
  planVisible.value = false
  ElMessage.success('已保存')
  plans.load()
}
const planActions = (r: SamplingRow): RowAction[] => [
  { label: '编辑', permission: 'qc:sampling:manage', handler: () => editPlan(r) },
  { label: '删除', permission: 'qc:sampling:manage', danger: true, confirm: `确定删除「${r.name}」吗？`, handler: async () => {
    await basicApi.deletePlan(r.id)
    plans.load()
  } }
]

// ==================== 缺陷代码 ====================
const defects = useListPage<{ keyword?: string; category?: string }, DefectCodeRow>({ api: (q) => basicApi.defects(q as never) })
const defectColumns: TableColumn<DefectCodeRow>[] = [
  { prop: 'code', label: '编码', width: 130 },
  { prop: 'name', label: '名称', minWidth: 160 },
  { prop: 'category', label: '分类', width: 100, type: 'dict', dictType: 'qc_defect_category' },
  { prop: 'defaultLevel', label: '默认等级', width: 90 },
  { prop: 'status', label: '状态', width: 70, type: 'status', statusMap: ENABLE }
]
const defectVisible = ref(false)
const defectForm = ref<Partial<DefectCodeRow> & { version?: number }>({})
function editDefect(r?: DefectCodeRow) {
  defectForm.value = r ? { ...r } : { category: 'APPEARANCE', defaultLevel: 'MI', status: 'ENABLED' }
  defectVisible.value = true
}
async function saveDefect() {
  await basicApi.saveDefect(defectForm.value.id, defectForm.value)
  defectVisible.value = false
  ElMessage.success('已保存')
  defects.load()
}
const defectActions = (r: DefectCodeRow): RowAction[] => [
  { label: '编辑', permission: 'qc:defect-code:manage', handler: () => editDefect(r) },
  { label: '删除', permission: 'qc:defect-code:manage', danger: true, confirm: `确定删除「${r.code}」吗？`, handler: async () => {
    await basicApi.deleteDefect(r.id)
    defects.load()
  } }
]
const statusOptions = optionsOf(ENABLE)
</script>

<template>
  <ErpPage description="检验标准按“物料 → 物料类别（逐级向上）→ 通用标准”匹配；检验单保存标准与抽样结果快照，标准变更不影响已创建的检验单">
    <ErpPanel flush>
      <el-tabs v-model="tab" class="detail-tabs">
        <el-tab-pane label="检验标准" name="standard">
          <ErpSearchForm v-model="std.query" :fields="stdFields" :loading="std.loading.value" @search="std.search" @reset="std.reset">
            <template #field-materialId><MaterialSelect v-model="std.query.materialId" /></template>
          </ErpSearchForm>
          <ErpTable :columns="stdColumns" :data="std.list.value" :loading="std.loading.value" storage-key="qc.standard" :actions-width="150" @refresh="std.load">
            <template #toolbar><el-button v-perm="'qc:standard:create'" type="primary" @click="router.push('/quality/standard/new')">新建标准</el-button></template>
            <template #actions="{ row }"><RowActions :actions="stdActions(asStd(row))" /></template>
          </ErpTable>
          <ErpPagination v-model:page-no="std.query.pageNo" v-model:page-size="std.query.pageSize" :total="std.total.value" @change="std.load" />
        </el-tab-pane>

        <el-tab-pane label="检验项目库" name="items">
          <ErpTable :columns="itemColumns" :data="items.list.value" :loading="items.loading.value" storage-key="qc.item-lib" :actions-width="100" @refresh="items.load">
            <template #toolbar>
              <el-input v-model="items.query.keyword" placeholder="编码 / 名称" clearable class="kw" @change="items.search" />
              <el-button v-perm="'qc:defect-code:manage'" type="primary" @click="editItem()">新建项目</el-button>
            </template>
            <template #actions="{ row }"><RowActions :actions="itemActions(row as ItemLibRow)" /></template>
          </ErpTable>
          <ErpPagination v-model:page-no="items.query.pageNo" v-model:page-size="items.query.pageSize" :total="items.total.value" @change="items.load" />
        </el-tab-pane>

        <el-tab-pane label="抽样方案" name="plans">
          <ErpTable :columns="planColumns" :data="plans.list.value" :loading="plans.loading.value" storage-key="qc.sampling" :actions-width="100" @refresh="plans.load">
            <template #toolbar><el-button v-perm="'qc:sampling:manage'" type="primary" @click="editPlan()">新建方案</el-button></template>
            <template #actions="{ row }"><RowActions :actions="planActions(row as SamplingRow)" /></template>
          </ErpTable>
          <ErpPagination v-model:page-no="plans.query.pageNo" v-model:page-size="plans.query.pageSize" :total="plans.total.value" @change="plans.load" />
        </el-tab-pane>

        <el-tab-pane label="缺陷代码" name="defects">
          <ErpTable :columns="defectColumns" :data="defects.list.value" :loading="defects.loading.value" storage-key="qc.defect-code" :actions-width="100" @refresh="defects.load">
            <template #toolbar>
              <el-input v-model="defects.query.keyword" placeholder="编码 / 名称" clearable class="kw" @change="defects.search" />
              <el-button v-perm="'qc:defect-code:manage'" type="primary" @click="editDefect()">新建缺陷代码</el-button>
            </template>
            <template #actions="{ row }"><RowActions :actions="defectActions(row as DefectCodeRow)" /></template>
          </ErpTable>
          <ErpPagination v-model:page-no="defects.query.pageNo" v-model:page-size="defects.query.pageSize" :total="defects.total.value" @change="defects.load" />
        </el-tab-pane>
      </el-tabs>
    </ErpPanel>

    <el-dialog v-model="itemVisible" :title="itemForm.id ? '编辑检验项目' : '新建检验项目'" width="560px" append-to-body :close-on-click-modal="false">
      <el-form label-width="96px">
        <el-form-item label="编码" required><el-input v-model="itemForm.code" maxlength="32" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="itemForm.name" maxlength="64" /></el-form-item>
        <el-form-item label="项目类型" required>
          <el-radio-group v-model="itemForm.itemType"><el-radio-button v-for="o in ITEM_TYPE" :key="String(o.value)" :value="o.value">{{ o.label }}</el-radio-button></el-radio-group>
        </el-form-item>
        <el-form-item label="检验方法" required><DictSelect v-model="itemForm.method" type="qc_inspection_method" /></el-form-item>
        <el-form-item v-if="itemForm.itemType === 'QUANTITATIVE'" label="单位"><el-input v-model="itemForm.unit" maxlength="16" /></el-form-item>
        <el-form-item label="缺陷等级" required>
          <el-select v-model="itemForm.defectLevel"><el-option v-for="l in LEVELS" :key="String(l.value)" :value="l.value" :label="l.label" /></el-select>
        </el-form-item>
        <el-form-item label="检验工具"><el-input v-model="itemForm.tool" maxlength="64" /></el-form-item>
        <el-form-item label="方法说明"><el-input v-model="itemForm.description" type="textarea" :rows="2" maxlength="512" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="itemForm.status"><el-radio v-for="o in statusOptions" :key="String(o.value)" :value="o.value">{{ o.label }}</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="itemVisible = false">取消</el-button><el-button type="primary" @click="saveItem">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="planVisible" :title="planForm.id ? '编辑抽样方案' : '新建抽样方案'" width="620px" append-to-body :close-on-click-modal="false">
      <el-form label-width="96px">
        <el-form-item label="编码" required><el-input v-model="planForm.code" maxlength="32" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="planForm.name" maxlength="64" placeholder="如：一般 II 级 MA0.65 MI1.5" /></el-form-item>
        <el-form-item label="方案类型" required>
          <el-select v-model="planForm.planType"><el-option v-for="o in PLAN_TYPE" :key="String(o.value)" :value="o.value" :label="o.label" /></el-select>
        </el-form-item>
        <template v-if="planForm.planType === 'GB2828'">
          <el-form-item label="检验水平" required>
            <el-select v-model="planForm.inspectionLevel"><el-option v-for="o in INSPECTION_LEVELS" :key="String(o.value)" :value="o.value" :label="o.label" /></el-select>
          </el-form-item>
          <el-form-item label="AQL">
            <div class="aql">
              <span>CR</span><el-select v-model="planForm.aqlCr" clearable><el-option v-for="a in AQLS" :key="a" :value="a" :label="a" /></el-select>
              <span>MA</span><el-select v-model="planForm.aqlMa" clearable><el-option v-for="a in AQLS" :key="a" :value="a" :label="a" /></el-select>
              <span>MI</span><el-select v-model="planForm.aqlMi" clearable><el-option v-for="a in AQLS" :key="a" :value="a" :label="a" /></el-select>
            </div>
          </el-form-item>
        </template>
        <el-form-item v-if="planForm.planType === 'FIXED'" label="样本数量" required><el-input-number v-model="planForm.fixedQty" :min="1" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="planForm.status"><el-radio v-for="o in statusOptions" :key="String(o.value)" :value="o.value">{{ o.label }}</el-radio></el-radio-group></el-form-item>
        <el-form-item label="说明"><el-input v-model="planForm.remark" maxlength="256" /></el-form-item>
        <el-form-item label="计算预览">
          <div class="aql"><QtyInput v-model="previewLot" /><el-button @click="doPreview">计算</el-button></div>
        </el-form-item>
        <el-form-item v-if="preview" label="">
          <div>
            <div>样本量 <strong class="num">{{ preview.sampleQty }}</strong><template v-if="preview.letter">（字码 {{ preview.letter }}）</template><template v-if="preview.full">，全检</template></div>
            <div v-for="l in preview.levels" :key="l.level" class="muted">{{ l.level }} AQL {{ l.aql }}：n={{ l.n }}，Ac {{ l.ac }} / Re {{ l.re }}</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="planVisible = false">取消</el-button><el-button type="primary" @click="savePlan">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="defectVisible" :title="defectForm.id ? '编辑缺陷代码' : '新建缺陷代码'" width="480px" append-to-body :close-on-click-modal="false">
      <el-form label-width="96px">
        <el-form-item label="编码" required><el-input v-model="defectForm.code" maxlength="32" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="defectForm.name" maxlength="64" /></el-form-item>
        <el-form-item label="缺陷分类" required><DictSelect v-model="defectForm.category" type="qc_defect_category" /></el-form-item>
        <el-form-item label="默认等级" required>
          <el-select v-model="defectForm.defaultLevel"><el-option v-for="l in LEVELS" :key="String(l.value)" :value="l.value" :label="l.label" /></el-select>
        </el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="defectForm.status"><el-radio v-for="o in statusOptions" :key="String(o.value)" :value="o.value">{{ o.label }}</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="defectVisible = false">取消</el-button><el-button type="primary" @click="saveDefect">保存</el-button></template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.kw { width: 200px; margin-right: var(--erp-space-2); }
.aql { display: flex; align-items: center; gap: var(--erp-space-2); }
.aql .el-select { width: 100px; }
.muted { color: var(--erp-color-text-secondary); }
</style>
