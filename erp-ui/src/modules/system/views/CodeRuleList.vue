<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { modules } from '@/modules/registry'
import { codeRuleApi, DATE_PATTERN_OPTIONS, RESET_OPTIONS, type CodeRuleRow, type CodeRuleSave, type SeqRow } from '../api/codeRule'
import { formatDateTime } from '@/utils/format'

defineOptions({ name: 'SystemCodeRuleList' })

/** 编码规则（01-05）：列表不分页，编辑弹窗实时预览，流水号抽屉可调整（只能调大） */
const query = reactive<{ moduleCode?: string; keyword?: string }>({})
const list = ref<CodeRuleRow[]>([])
const loading = ref(false)

const fields: SearchField[] = [
  { prop: 'moduleCode', label: '模块', type: 'select', options: modules.map((m) => ({ value: m.code, label: m.title })) },
  { prop: 'keyword', label: '关键字', placeholder: '业务编码/名称' }
]

const columns: TableColumn<CodeRuleRow>[] = [
  { prop: 'moduleName', label: '模块', width: 100 },
  { prop: 'bizCode', label: '业务编码', width: 200 },
  { prop: 'name', label: '名称', width: 160 },
  { key: 'rule', label: '规则', width: 280, slot: true },
  { prop: 'example', label: '示例', width: 180 },
  { prop: 'resetCycle', label: '重置周期', width: 90, type: 'enum', options: RESET_OPTIONS },
  { prop: 'allowManual', label: '手工输入', width: 90, type: 'bool' }
]

async function load() {
  loading.value = true
  try {
    list.value = await codeRuleApi.list({ ...query })
  } finally {
    loading.value = false
  }
}

function reset() {
  query.moduleCode = undefined
  query.keyword = undefined
  load()
}

/** 规则分段：前缀 / 日期 / 分隔符 / 流水号，用中性色块区分，流水号用主色 */
function segments(r: Pick<CodeRuleRow, 'prefix' | 'datePattern' | 'separator' | 'seqLength'>) {
  const s: { text: string; kind: 'prefix' | 'date' | 'sep' | 'seq'; title: string }[] = []
  if (r.prefix) s.push({ text: r.prefix, kind: 'prefix', title: '前缀' })
  if (r.datePattern) s.push({ text: r.datePattern, kind: 'date', title: '日期' })
  if (r.datePattern && r.separator) s.push({ text: r.separator, kind: 'sep', title: '分隔符' })
  s.push({ text: '0'.repeat(r.seqLength), kind: 'seq', title: `流水号 ${r.seqLength} 位` })
  return s
}

// ---------- 编辑 ----------
const visible = ref(false)
const saving = ref(false)
const editing = ref<CodeRuleRow>()
const formRef = ref<FormInstance>()
const form = ref<CodeRuleSave>({ name: '', prefix: '', datePattern: '', separator: '', seqLength: 4, resetCycle: 'NEVER', allowManual: false })
const preview = ref('')
const previewError = ref('')
const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  seqLength: [{ required: true, message: '请输入流水号位数', trigger: 'blur' }],
  resetCycle: [{ required: true, message: '请选择重置周期', trigger: 'change' }]
}

function openEdit(r: CodeRuleRow) {
  editing.value = r
  form.value = { name: r.name, prefix: r.prefix, datePattern: r.datePattern, separator: r.separator, seqLength: r.seqLength, resetCycle: r.resetCycle, allowManual: r.allowManual, version: r.version }
  visible.value = true
}

let timer = 0
watch(() => [form.value.prefix, form.value.datePattern, form.value.separator, form.value.seqLength, form.value.resetCycle], () => {
  if (!visible.value || !editing.value) return
  window.clearTimeout(timer)
  timer = window.setTimeout(async () => {
    try {
      preview.value = await codeRuleApi.preview({ bizCode: editing.value!.bizCode, prefix: form.value.prefix, datePattern: form.value.datePattern,
        separator: form.value.separator, seqLength: form.value.seqLength, resetCycle: form.value.resetCycle })
      previewError.value = ''
    } catch (e) {
      preview.value = ''
      previewError.value = (e as Error).message
    }
  }, 300)
}, { immediate: false })
watch(visible, (v) => {
  if (v) form.value = { ...form.value }
})

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  await ElMessageBox.confirm('修改规则只影响之后生成的编码，已生成的编码不变。确定保存吗？', '提示', { type: 'warning' })
  saving.value = true
  try {
    await codeRuleApi.update(editing.value!.id, form.value)
    ElMessage.success('保存成功')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------- 流水号 ----------
const seqVisible = ref(false)
const seqRule = ref<CodeRuleRow>()
const seqs = ref<SeqRow[]>([])

async function openSeqs(r: CodeRuleRow) {
  seqRule.value = r
  seqs.value = await codeRuleApi.seqs(r.id)
  seqVisible.value = true
}

async function adjust(s: SeqRow) {
  const { value } = await ElMessageBox.prompt(`当前值 ${s.currentValue}，请输入新值（只能调大）`, `调整流水号 ${s.resetKey}`, {
    inputPattern: /^\d+$/, inputErrorMessage: '请输入正整数', inputValue: String(s.currentValue + 1)
  })
  await codeRuleApi.adjust(seqRule.value!.id, s.resetKey, Number(value))
  ElMessage.success('已调整')
  seqs.value = await codeRuleApi.seqs(seqRule.value!.id)
  load()
}

const vars = computed(() => editing.value?.allowedVars ?? [])
const asRule = (r: unknown) => r as CodeRuleRow

onMounted(load)
</script>

<template>
  <ErpPage description="各业务单据与主数据的编号规则：前缀 + 日期 + 流水号；修改只影响之后生成的编号">
    <ErpPanel>
      <template #filter><ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="load" @reset="reset" /></template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="system.codeRule" :actions-width="140" @refresh="load">
        <template #col-rule="{ row }">
          <span class="segs">
            <span v-for="(s, i) in segments(asRule(row))" :key="i" :class="['seg', `seg--${s.kind}`]" :title="s.title">{{ s.text }}</span>
          </span>
        </template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '编辑', permission: 'system:code-rule:update', handler: () => openEdit(asRule(row)) },
            { label: '流水号', permission: 'system:code-rule:query', handler: () => openSeqs(asRule(row)) }
          ]" />
        </template>
      </ErpTable>
    </ErpPanel>

  <el-dialog v-model="visible" :title="`编辑编码规则 - ${editing?.bizCode ?? ''}`" width="640px" :close-on-click-modal="false" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="业务编码">{{ editing?.bizCode }}</el-form-item>
      <el-form-item label="名称" prop="name"><el-input v-model="form.name" maxlength="64" /></el-form-item>
      <el-form-item label="前缀">
        <el-input v-model="form.prefix" maxlength="32" />
        <div class="form-tip">字母、数字、- _ /<template v-if="vars.length">；可用变量：<code v-for="v in vars" :key="v" class="var">{{ '{' + v + '}' }}</code></template></div>
      </el-form-item>
      <el-form-item label="日期格式">
        <el-select v-model="form.datePattern"><el-option v-for="o in DATE_PATTERN_OPTIONS" :key="o.value" :value="o.value" :label="o.label" /></el-select>
      </el-form-item>
      <el-form-item label="分隔符">
        <el-radio-group v-model="form.separator"><el-radio value="">无</el-radio><el-radio value="-">-</el-radio></el-radio-group>
      </el-form-item>
      <el-form-item label="流水号位数" prop="seqLength"><el-input-number v-model="form.seqLength" :min="1" :max="12" controls-position="right" /></el-form-item>
      <el-form-item label="重置周期" prop="resetCycle">
        <el-select v-model="form.resetCycle"><el-option v-for="o in RESET_OPTIONS" :key="o.value" :value="o.value" :label="o.label" /></el-select>
      </el-form-item>
      <el-form-item label="允许手工输入"><el-switch v-model="form.allowManual" /></el-form-item>
      <el-form-item label="示例预览">
        <span v-if="preview" class="preview">{{ preview }}</span>
        <span v-else-if="previewError" class="err">{{ previewError }}</span>
        <span v-else class="form-tip">{{ editing?.example }}</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>

  <el-drawer v-model="seqVisible" :title="`流水号 - ${seqRule?.name ?? ''}`" size="720px" append-to-body>
    <el-table :data="seqs">
      <el-table-column prop="resetKey" label="重置键" min-width="160" />
      <el-table-column prop="currentValue" label="当前值" width="120" align="right" />
      <el-table-column label="最后更新" width="170"><template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template></el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }"><el-button v-perm="'system:code-rule:update'" link type="primary" @click="adjust(row as SeqRow)">调整</el-button></template>
      </el-table-column>
      <template #empty><ErpEmpty description="尚未生成过编码" compact /></template>
    </el-table>
  </el-drawer>
  </ErpPage>
</template>

<style scoped>
.segs { display: inline-flex; gap: 2px; }
.seg {
  display: inline-flex; align-items: center; height: 22px; padding: 0 6px; border-radius: var(--erp-radius-xs);
  font-family: var(--erp-font-family-mono); font-size: var(--erp-font-size-caption); background: var(--erp-color-hover); color: var(--erp-color-text);
}
.seg--date { color: var(--erp-color-text-secondary); }
.seg--sep { background: transparent; color: var(--erp-color-text-tertiary); padding: 0 2px; }
.seg--seq { background: var(--erp-color-primary-bg); color: var(--el-color-primary); }
.preview { font-family: var(--erp-font-family-mono); font-size: var(--erp-font-size-section-title); color: var(--erp-color-text); letter-spacing: 0.5px; }
.err { color: var(--el-color-danger); font-size: var(--erp-font-size-caption); }
.var { margin-left: 4px; font-family: var(--erp-font-family-mono); font-size: var(--erp-font-size-caption); padding: 1px 4px; border-radius: var(--erp-radius-xs); background: var(--erp-color-hover); }
</style>
