<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { EditorView, basicSetup } from 'codemirror'
import { Compartment, EditorState } from '@codemirror/state'
import { html } from '@codemirror/lang-html'
import { http } from '@/api/http'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import { buildPrintHtml, checkTemplate, renderTemplate } from '@/utils/print/render'
import { LANGUAGE_OPTIONS, printApi, type PrintBiz, type TemplateSave } from '../api/print'

defineOptions({ name: 'SystemPrintTemplateEdit' })

/**
 * 打印模板编辑器（需求 01-09 第 3.2 节）：变量面板 | 代码（CodeMirror，HTML 模式）| 预览（示例数据或真实单据）。
 * 内置模板只读，可复制后修改；保存时后端再次校验（R02：≤ 200KB、无脚本、语法正确）。
 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const dict = useDictStore()
const me = useUserStore()

const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const builtin = ref(false)
const readonly = computed(() => builtin.value || !me.hasPermission(id.value ? 'system:print:update' : 'system:print:create'))
const saving = ref(false)
const biz = ref<PrintBiz>()
const bizList = ref<PrintBiz[]>([])

const form = ref<TemplateSave>({ bizType: '', name: '', language: 'zh-CN', paper: 'A4_P', margin: '10mm 10mm 10mm 10mm', content: '' })
const guard = useLeaveGuard(() => form.value)

// ---------- 边距：上 右 下 左（mm） ----------
const margins = computed<number[]>({
  get: () => {
    const p = form.value.margin.split(/\s+/).map((s) => parseFloat(s))
    const [t = 10, r = t, b = t, l = r] = p.map((n) => (Number.isFinite(n) ? n : 10))
    return [t, r, b, l]
  },
  set: (v) => (form.value.margin = v.map((n) => `${n ?? 0}mm`).join(' '))
})
function setMargin(i: number, v?: number) {
  const m = [...margins.value]
  m[i] = v ?? 0
  margins.value = m
}

// ---------- 变量面板 ----------
interface VarNode { key: string; label: string; path: string; type: string; insert: string; children?: VarNode[] }

const HELPERS: { name: string; insert: string }[] = [
  { name: '日期 formatDate', insert: '{{formatDate docDate}}' },
  { name: '日期时间 formatDateTime', insert: '{{formatDateTime createdAt}}' },
  { name: '数量 formatQty', insert: '{{formatQty qty 4}}' },
  { name: '金额 formatAmount', insert: '{{formatAmount amount 2}}' },
  { name: '单价 formatPrice', insert: '{{formatPrice price}}' },
  { name: '中文大写金额 amountInWordsCn', insert: '{{amountInWordsCn totalAmount}}' },
  { name: '英文大写金额 amountInWordsEn', insert: '{{amountInWordsEn totalAmount "USD"}}' },
  { name: '条码 barcode', insert: '{{barcode docNo 40}}' },
  { name: '二维码 qrcode', insert: '{{qrcode docNo 80}}' },
  { name: '字典标签 dict', insert: '{{dict "sys_uom" uom}}' },
  { name: '序号 add', insert: '{{add @index 1}}' },
  { name: '条件 if', insert: '{{#if remark}}{{remark}}{{/if}}' }
]

/** 按 path 组装树：数组类型插入 each 片段；数组下的字段插入相对路径（在 each 内使用） */
const varTree = computed<VarNode[]>(() => {
  const vars = biz.value?.variables ?? []
  const roots: VarNode[] = []
  const byPath = new Map<string, VarNode>()
  for (const v of vars) {
    const dot = v.path.lastIndexOf('.')
    const parentPath = dot > 0 ? v.path.slice(0, dot) : ''
    const parent = byPath.get(parentPath)
    const rel = parent && parent.type === 'array' ? v.path.slice(dot + 1) : v.path
    const node: VarNode = {
      key: v.path, label: v.name, path: v.path, type: v.type,
      insert: v.type === 'array' ? `{{#each ${v.path}}}\n  {{add @index 1}}\n{{/each}}` : `{{${rel}}}`
    }
    byPath.set(v.path, node)
    if (parent) (parent.children ??= []).push(node)
    else roots.push(node)
  }
  return roots
})

const TYPE_LABEL: Record<string, string> = { array: '数组', object: '对象', date: '日期', datetime: '日期时间', qty: '数量', amount: '金额', price: '单价', number: '数字', image: '图片' }

// ---------- 代码编辑器 ----------
const editorEl = ref<HTMLElement>()
const view = shallowRef<EditorView>()
const readonlyCompartment = new Compartment()

function createEditor() {
  if (!editorEl.value) return
  view.value?.destroy()
  view.value = new EditorView({
    parent: editorEl.value,
    state: EditorState.create({
      doc: form.value.content,
      extensions: [
        basicSetup,
        html(),
        EditorView.lineWrapping,
        readonlyCompartment.of(EditorState.readOnly.of(readonly.value)),
        EditorView.updateListener.of((u) => {
          if (u.docChanged) form.value.content = u.state.doc.toString()
        })
      ]
    })
  })
}

watch(readonly, (r) => view.value?.dispatch({ effects: readonlyCompartment.reconfigure(EditorState.readOnly.of(r)) }))

function onNodeClick(n: VarNode) {
  insert(n.insert)
}

function insert(text: string) {
  const v = view.value
  if (!v || readonly.value) return
  v.dispatch(v.state.replaceSelection(text))
  v.focus()
}

// ---------- 预览 ----------
const previewHtml = ref('')
const previewError = ref('')
const realId = ref('')
const realData = ref<Record<string, unknown>>()
const previewSource = computed(() => (realData.value ? '真实单据' : '示例数据'))

function refreshPreview() {
  const err = checkTemplate(form.value.content)
  if (err) {
    previewError.value = err
    return
  }
  try {
    const data = realData.value ?? biz.value?.sampleData ?? {}
    const body = renderTemplate(form.value.content, data, { label: (t, v) => dict.item(t, v)?.label })
    previewHtml.value = buildPrintHtml({ title: form.value.name || '预览', setting: form.value, docs: [{ html: body }], printedBy: me.user?.realName, toolbar: false })
    previewError.value = ''
  } catch (e) {
    previewError.value = e instanceof Error ? e.message.split('\n')[0] : String(e)
  }
}

let timer = 0
watch(() => [form.value.content, form.value.paper, form.value.paperWidth, form.value.paperHeight, form.value.margin], () => {
  window.clearTimeout(timer)
  timer = window.setTimeout(refreshPreview, 1000)
})

const loadingReal = ref(false)
async function previewReal() {
  const docId = realId.value.trim()
  if (!docId) {
    realData.value = undefined
    refreshPreview()
    return
  }
  if (!biz.value) return
  loadingReal.value = true
  try {
    realData.value = await http.get<Record<string, unknown>>(biz.value.dataApi.replace('{id}', encodeURIComponent(docId)).replace(/^\/api/, ''))
    refreshPreview()
  } finally {
    loadingReal.value = false
  }
}
function useSample() {
  realId.value = ''
  realData.value = undefined
  refreshPreview()
}

// ---------- 加载与保存 ----------
async function loadBiz(bizType: string) {
  biz.value = bizType ? await printApi.biz(bizType).catch(() => undefined) : undefined
}

onMounted(async () => {
  dict.load()
  bizList.value = await printApi.bizList().catch(() => [])
  if (id.value) {
    const d = await printApi.get(id.value)
    builtin.value = d.isBuiltin
    form.value = {
      bizType: d.bizType, name: d.name, language: d.language, paper: d.paper, paperWidth: d.paperWidth, paperHeight: d.paperHeight,
      margin: d.margin, content: d.content, remark: d.remark, version: d.version
    }
    tabs.setTitle(tabKeyOf(route), `${d.isBuiltin ? '查看' : '编辑'}模板 ${d.name}`)
  } else {
    const q = route.query
    form.value.bizType = typeof q.bizType === 'string' ? q.bizType : bizList.value[0]?.bizType ?? ''
    form.value.language = typeof q.language === 'string' ? q.language : 'zh-CN'
    if (typeof q.from === 'string' && q.from) {
      const src = await printApi.get(q.from)
      Object.assign(form.value, { paper: src.paper, paperWidth: src.paperWidth, paperHeight: src.paperHeight, margin: src.margin, content: src.content })
    }
    const bizName = bizList.value.find((b) => b.bizType === form.value.bizType)?.name
    if (bizName) form.value.name = `${bizName}（自定义）`
  }
  await loadBiz(form.value.bizType)
  guard.markClean()
  await nextTick()
  createEditor()
  refreshPreview()
})

watch(() => form.value.bizType, (t, old) => {
  if (old !== undefined && old !== '' && t !== old) {
    realData.value = undefined
    loadBiz(t).then(refreshPreview)
  }
})

onBeforeUnmount(() => {
  window.clearTimeout(timer)
  view.value?.destroy()
})

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push('/system/print-template')
}

async function save() {
  const f = form.value
  if (!f.bizType) return ElMessage.warning('请选择单据类型')
  if (!f.name.trim()) return ElMessage.warning('请输入模板名称')
  if (f.paper === 'CUSTOM' && !(f.paperWidth && f.paperHeight)) return ElMessage.warning('自定义纸张请填写宽度和高度')
  if (!f.content.trim()) return ElMessage.warning('请输入模板内容')
  const err = checkTemplate(f.content)
  if (err) return ElMessage.error(`模板语法错误，${err.split('\n')[0]}`)
  saving.value = true
  try {
    const data: TemplateSave = { ...f, name: f.name.trim(), paperWidth: f.paper === 'CUSTOM' ? f.paperWidth : undefined, paperHeight: f.paper === 'CUSTOM' ? f.paperHeight : undefined }
    if (id.value) {
      await printApi.update(id.value, data)
    } else {
      await printApi.create(data)
    }
    ElMessage.success('保存成功')
    guard.markClean()
    await back()
  } finally {
    saving.value = false
  }
}

const sizeKb = computed(() => (new Blob([form.value.content]).size / 1024).toFixed(1))
</script>

<template>
  <ErpPage fill back :on-back="back">
    <template #meta>
      <ErpBadge v-if="builtin" type="info" plain>内置 · 只读</ErpBadge>
    </template>
    <template #actions>
      <el-button @click="back">{{ readonly ? '返回' : '取消' }}</el-button>
      <el-button v-if="!readonly" type="primary" :loading="saving" @click="save">保存</el-button>
    </template>

    <ErpPanel class="settings">
      <el-form :model="form" inline label-width="auto" :disabled="readonly" class="settings-form">
        <el-form-item label="单据类型" required>
          <el-select v-model="form.bizType" filterable :disabled="!!id" class="w160">
            <el-option v-for="b in bizList" :key="b.bizType" :value="b.bizType" :label="b.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="64" class="w200" /></el-form-item>
        <el-form-item label="语言">
          <el-select v-model="form.language" class="w120"><el-option v-for="o in LANGUAGE_OPTIONS" :key="o.value" v-bind="o" /></el-select>
        </el-form-item>
        <el-form-item label="纸张"><DictSelect v-model="form.paper" type="sys_print_paper" :clearable="false" class="w120" /></el-form-item>
        <el-form-item v-if="form.paper === 'CUSTOM'" label="宽×高(mm)">
          <el-input-number v-model="form.paperWidth" :min="20" :max="1000" controls-position="right" class="num-mm" />
          <span class="x">×</span>
          <el-input-number v-model="form.paperHeight" :min="20" :max="2000" controls-position="right" class="num-mm" />
        </el-form-item>
        <el-form-item label="边距(mm)">
          <span class="margins">
            <el-tooltip v-for="(n, i) in ['上', '右', '下', '左']" :key="n" :content="n" placement="top">
              <el-input-number :model-value="margins[i]" :min="0" :max="50" :controls="false" class="num-margin" @update:model-value="setMargin(i, $event)" />
            </el-tooltip>
          </span>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" maxlength="200" class="w200" /></el-form-item>
      </el-form>
    </ErpPanel>

    <div class="workspace">
      <ErpPanel title="变量" flush class="vars">
        <el-scrollbar>
          <div class="vars-body">
            <el-tree v-if="varTree.length" :data="varTree" node-key="key" default-expand-all :expand-on-click-node="false" :indent="12" @node-click="onNodeClick">
              <template #default="{ data }">
                <span class="var-node" :title="`插入 ${data.insert}`">
                  <span class="mono">{{ data.path.split('.').pop() }}</span>
                  <span class="var-name">{{ data.label }}</span>
                  <span v-if="TYPE_LABEL[data.type]" class="var-type">{{ TYPE_LABEL[data.type] }}</span>
                </span>
              </template>
            </el-tree>
            <ErpEmpty v-else compact description="该单据类型未声明变量" />
            <div class="group-title helpers-title">函数</div>
            <button v-for="h in HELPERS" :key="h.name" type="button" class="helper" :disabled="readonly" :title="h.insert" @click="insert(h.insert)">{{ h.name }}</button>
          </div>
        </el-scrollbar>
      </ErpPanel>

      <ErpPanel title="模板代码（HTML + Handlebars）" flush class="code">
        <template #extra><span class="text-muted">{{ sizeKb }} KB / 200 KB</span></template>
        <div ref="editorEl" class="editor" />
      </ErpPanel>

      <ErpPanel flush class="preview">
        <template #title>预览 <ErpBadge type="info" plain>{{ previewSource }}</ErpBadge></template>
        <template #extra>
          <div class="preview-tools">
            <el-input v-model="realId" placeholder="单据 ID" clearable class="w120" @keyup.enter="previewReal" @clear="useSample" />
            <el-button :loading="loadingReal" @click="previewReal">真实单据预览</el-button>
            <ErpIconButton icon="Refresh" tooltip="刷新预览" @click="refreshPreview" />
          </div>
        </template>
        <div v-if="previewError" class="preview-error">
          <div class="preview-error__title">模板语法错误：{{ previewError.split('\n')[0] }}</div>
          <pre class="mono">{{ previewError.split('\n').slice(1).join('\n') }}</pre>
        </div>
        <iframe v-else class="preview-frame" sandbox="" :srcdoc="previewHtml" title="打印预览" />
      </ErpPanel>
    </div>
  </ErpPage>
</template>

<style scoped>
.settings :deep(.el-form-item) { margin-bottom: 0; margin-right: var(--erp-space-5); }
.settings-form { display: flex; flex-wrap: wrap; row-gap: var(--erp-space-3); }
.x { margin: 0 var(--erp-space-1); color: var(--erp-color-text-tertiary); }
.num-mm { width: 100px; }
.margins { display: inline-flex; gap: var(--erp-space-1); }
.num-margin { width: 52px; }
.workspace { flex: 1; min-height: 480px; display: grid; grid-template-columns: 240px minmax(0, 1fr) minmax(0, 1fr); gap: var(--erp-space-4); }
.workspace > * { min-height: 0; display: flex; flex-direction: column; }
.workspace :deep(.erp-panel__body) { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.vars-body { padding: var(--erp-space-2); }
.var-node { display: inline-flex; align-items: center; gap: var(--erp-space-2); min-width: 0; font-size: var(--erp-font-size-secondary); }
.var-name { color: var(--erp-color-text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.var-type { font-size: var(--erp-font-size-caption); color: var(--erp-color-text-tertiary); }
.helpers-title { margin: var(--erp-space-4) var(--erp-space-2) var(--erp-space-2); }
.helper {
  display: block; width: 100%; padding: var(--erp-space-1) var(--erp-space-2); border: 0; border-radius: var(--erp-radius-xs);
  background: transparent; text-align: left; font: inherit; font-size: var(--erp-font-size-secondary); color: var(--erp-color-text); cursor: pointer;
  transition: background-color var(--erp-duration-fast) var(--erp-ease);
}
.helper:hover:not(:disabled) { background: var(--erp-color-hover); }
.helper:disabled { color: var(--erp-color-text-disabled); cursor: not-allowed; }
.editor { flex: 1; min-height: 0; overflow: hidden; }
.editor :deep(.cm-editor) { height: 100%; font-size: var(--erp-font-size-secondary); }
.editor :deep(.cm-editor.cm-focused) { outline: none; }
.editor :deep(.cm-scroller) { font-family: var(--erp-font-family-mono); }
.editor :deep(.cm-gutters) { background: var(--erp-color-surface-subtle); border-right: 1px solid var(--erp-color-border-light); color: var(--erp-color-text-tertiary); }
.preview :deep(.erp-panel__title) { display: flex; align-items: center; gap: var(--erp-space-2); white-space: nowrap; }
.preview-tools { display: flex; align-items: center; gap: var(--erp-space-2); }
.preview-frame { flex: 1; width: 100%; min-height: 0; border: 0; background: var(--erp-color-bg); border-radius: 0 0 var(--erp-radius-card) var(--erp-radius-card); }
.preview-error { padding: var(--erp-space-4); }
.preview-error__title { color: var(--el-color-danger); font-weight: var(--erp-font-weight-medium); margin-bottom: var(--erp-space-2); }
.preview-error pre { margin: 0; white-space: pre-wrap; font-size: var(--erp-font-size-secondary); color: var(--erp-color-text); }
@media (max-width: 1280px) {
  .workspace { grid-template-columns: 200px minmax(0, 1fr) minmax(0, 1fr); }
}
</style>
