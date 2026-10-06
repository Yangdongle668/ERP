<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { categoryApi, type CodeScheme, type CodeSegment, type SegmentValue } from '../api/category'

/**
 * 物料编码段维护（需求 05-01 第 8 节，《物料编码原则》）：编码 = 类别前缀 + 各编码段特征值（从左到右）+ 流水号。
 * 类别已有物料后只能改名称 / 说明、新增或停用特征值；编码段的个数、顺序、位数和已有特征值的代码不能改。
 */
const props = defineProps<{ modelValue: boolean; categoryId?: string }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; saved: [] }>()

const store = useUserStore()
const canEdit = computed(() => store.hasPermission('eng:category:update'))
const loading = ref(false)
const saving = ref(false)
const scheme = ref<CodeScheme>()
const segments = ref<CodeSegment[]>([])
const snapshot = ref('')

const visible = computed({ get: () => props.modelValue, set: (v) => emit('update:modelValue', v) })
const locked = computed(() => !!scheme.value?.locked)
const readonly = computed(() => !canEdit.value)

watch(() => [props.modelValue, props.categoryId], async ([open]) => {
  if (!open || !props.categoryId) return
  loading.value = true
  try {
    scheme.value = await categoryApi.codeScheme(props.categoryId)
    segments.value = JSON.parse(JSON.stringify(scheme.value.segments))
    snapshot.value = JSON.stringify(segments.value)
  } finally {
    loading.value = false
  }
})

/** 示例编码：每段取第一个启用的特征值 */
const example = computed(() => {
  const s = scheme.value
  if (!s) return ''
  const seq = s.codeSeqLength ?? 5
  return s.codePrefix + segments.value.map((sg) => sg.values.find((v) => v.status === 'ENABLED')?.code ?? '?'.repeat(sg.length)).join('') +
    '0'.repeat(seq - 1) + '1'
})

function addSegment() {
  segments.value.push({ name: '', length: 1, values: [] })
}

function moveSegment(i: number, d: number) {
  const list = segments.value
  const j = i + d
  if (j < 0 || j >= list.length) return
  ;[list[i], list[j]] = [list[j], list[i]]
}

function addValue(sg: CodeSegment) {
  sg.values.push({ code: '', name: '', status: 'ENABLED' })
}

/** 已保存的特征值在锁定后不能删除、不能改代码 */
const savedValue = (v: SegmentValue) => !!v.id && locked.value
const asValue = (r: unknown) => r as SegmentValue

function check(): string | undefined {
  const names = new Set<string>()
  for (const sg of segments.value) {
    const name = sg.name.trim()
    if (!name) return '请输入编码段名称'
    if (names.has(name)) return `编码段名称「${name}」重复`
    names.add(name)
    const codes = new Set<string>()
    for (const v of sg.values) {
      const code = v.code.trim().toUpperCase()
      if (!/^[A-Z0-9]+$/.test(code) || code.length !== sg.length) return `编码段「${name}」为 ${sg.length} 位，特征值「${v.code}」位数不对`
      if (!v.name.trim()) return `编码段「${name}」的特征值「${code}」请填写说明`
      if (codes.has(code)) return `编码段「${name}」的特征值「${code}」重复`
      codes.add(code)
    }
  }
  return undefined
}

async function save() {
  const err = check()
  if (err) return ElMessage.warning(err)
  saving.value = true
  try {
    await categoryApi.saveCodeScheme(props.categoryId!, segments.value.map((sg) => ({
      ...sg, name: sg.name.trim(), values: sg.values.map((v) => ({ ...v, code: v.code.trim().toUpperCase(), name: v.name.trim() }))
    })))
    ElMessage.success('保存成功')
    snapshot.value = JSON.stringify(segments.value)
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}

async function beforeClose(done: () => void) {
  if (JSON.stringify(segments.value) !== snapshot.value) {
    const ok = await ElMessageBox.confirm('有未保存的修改，确定关闭吗？', '提示', { type: 'warning' }).then(() => true).catch(() => false)
    if (!ok) return
  }
  done()
}
</script>

<template>
  <el-dialog v-model="visible" :title="`编码段 - ${scheme?.categoryName ?? ''}`" width="880px" :close-on-click-modal="false"
             :before-close="beforeClose" append-to-body>
    <el-skeleton v-if="loading" :rows="6" animated />
    <template v-else-if="scheme">
      <el-alert v-if="!scheme.leaf" type="warning" :closable="false" show-icon title="编码段只能设置在末级类别上" />
      <el-alert v-else-if="locked" type="info" :closable="false" show-icon
                title="该类别已有物料：只能修改名称和说明、新增或停用特征值；编码段的个数、顺序、位数和已有特征值的代码不能修改" />
      <div class="summary">
        <span>编码 = 前缀 <b class="mono">{{ scheme.codePrefix }}</b></span>
        <span v-for="sg in segments" :key="sg.id ?? sg.name">+ {{ sg.name || '未命名' }}（{{ sg.length }} 位）</span>
        <span>+ {{ scheme.codeSeqLength ?? 5 }} 位流水号</span>
        <span class="text-muted">示例：<span class="mono">{{ example }}</span></span>
      </div>
      <ErpEmpty v-if="!segments.length" description="未设置编码段：物料编码 = 前缀 + 流水号" />
      <ErpPanel v-for="(sg, i) in segments" :key="sg.id ?? `new-${i}`" class="segment">
        <template #title>
          <span class="seg-title">
            <span class="seg-no">第 {{ i + 1 }} 段</span>
            <el-input v-model="sg.name" maxlength="32" placeholder="编码段名称，如 线材型号" class="seg-name" :disabled="readonly" />
            <el-input-number v-model="sg.length" :min="1" :max="4" :precision="0" controls-position="right" class="seg-len"
                             :disabled="readonly || locked" />
            <span class="text-muted">位</span>
          </span>
        </template>
        <template #extra>
          <template v-if="!readonly && !locked">
            <ErpIconButton icon="Up" tooltip="左移（上移）" :disabled="i === 0" @click="moveSegment(i, -1)" />
            <ErpIconButton icon="Down" tooltip="右移（下移）" :disabled="i === segments.length - 1" @click="moveSegment(i, 1)" />
            <ErpIconButton icon="Delete" tooltip="删除编码段" @click="segments.splice(i, 1)" />
          </template>
        </template>
        <el-table :data="sg.values" size="small" max-height="260">
          <el-table-column label="特征值" width="120">
            <template #default="{ row }">
              <el-input v-model="asValue(row).code" :maxlength="sg.length" :disabled="readonly || savedValue(asValue(row))"
                        @input="asValue(row).code = String($event).toUpperCase()" />
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="220">
            <template #default="{ row }"><el-input v-model="asValue(row).name" maxlength="64" :disabled="readonly" /></template>
          </el-table-column>
          <el-table-column label="启用" width="80" align="center">
            <template #default="{ row }">
              <el-switch :model-value="asValue(row).status === 'ENABLED'" :disabled="readonly"
                         @update:model-value="(v: string | number | boolean) => (asValue(row).status = v ? 'ENABLED' : 'DISABLED')" />
            </template>
          </el-table-column>
          <el-table-column v-if="!readonly" label="操作" width="70" align="center">
            <template #default="{ row, $index }">
              <el-button v-if="!savedValue(asValue(row))" link type="danger" @click="sg.values.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button v-if="!readonly" link type="primary" icon="Plus" class="add-value" @click="addValue(sg)">新增特征值</el-button>
      </ErpPanel>
      <el-button v-if="!readonly && !locked && scheme.leaf" icon="Plus" @click="addSegment">新增编码段</el-button>
    </template>
    <template #footer>
      <el-button @click="beforeClose(() => (visible = false))">{{ readonly ? '关闭' : '取消' }}</el-button>
      <el-button v-if="!readonly" type="primary" :loading="saving" :disabled="!scheme?.leaf" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.summary { display: flex; flex-wrap: wrap; gap: var(--erp-space-2); align-items: center; margin: var(--erp-space-3) 0; }
.segment { margin-bottom: var(--erp-space-3); }
.seg-title { display: inline-flex; align-items: center; gap: var(--erp-space-2); }
.seg-no { white-space: nowrap; }
.seg-name { width: 220px; }
.seg-len { width: 100px; }
.add-value { margin-top: var(--erp-space-2); }
</style>
