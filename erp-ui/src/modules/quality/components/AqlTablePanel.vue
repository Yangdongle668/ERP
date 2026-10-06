<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { basicApi, type AqlCodeRow, type AqlRow, type AqlTableView } from '../api/quality'

/** AQL 抽样表（需求 10-01 第 2、3 节）：样本量字码表 + 主表（箭头规则已展开）；有“抽样方案维护”权限可修改 */
const me = useUserStore()
const canEdit = computed(() => me.hasPermission('qc:sampling:manage'))
const data = ref<AqlTableView>()
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    data.value = await basicApi.aqlTable()
  } finally {
    loading.value = false
  }
}
onMounted(load)

/** 字码表：每个批量范围一行，各检验水平一列 */
const codeRows = computed(() => {
  const map = new Map<string, { key: string; range: string; cells: Record<string, AqlCodeRow> }>()
  for (const c of data.value?.codes ?? []) {
    const key = `${c.lotMin}`
    if (!map.has(key)) map.set(key, { key, range: c.lotMax ? `${c.lotMin} ~ ${c.lotMax}` : `${c.lotMin} 及以上`, cells: {} })
    map.get(key)!.cells[c.inspectionLevel] = c
  }
  return [...map.values()]
})

/** 主表：每个字码一行，各 AQL 一列 */
const mainRows = computed(() => {
  const map = new Map<string, Record<string, AqlRow>>()
  for (const r of data.value?.rows ?? []) {
    if (!map.has(r.codeLetter)) map.set(r.codeLetter, {})
    map.get(r.codeLetter)![norm(r.aql)] = r
  }
  return (data.value?.letters ?? []).map((letter) => ({ letter, cells: map.get(letter) ?? {} }))
})
function norm(aql: string) {
  const n = Number(aql)
  return n === 0 ? '0' : String(n)
}

const codeVisible = ref(false)
const codeForm = ref<{ row?: AqlCodeRow; letter?: string }>({})
function editCode(c?: AqlCodeRow) {
  if (!c || !canEdit.value) return
  codeForm.value = { row: c, letter: c.codeLetter }
  codeVisible.value = true
}
async function saveCode() {
  const f = codeForm.value
  await basicApi.saveAqlCode(f.row!.id, { codeLetter: f.letter, version: f.row!.version })
  ElMessage.success('已保存')
  codeVisible.value = false
  load()
}

const rowVisible = ref(false)
const rowForm = ref<{ row?: AqlRow; sampleLetter?: string; sampleSize?: number; ac?: number; re?: number }>({})
function editRow(r?: AqlRow) {
  if (!r || !canEdit.value) return
  rowForm.value = { row: r, sampleLetter: r.sampleLetter, sampleSize: r.sampleSize, ac: r.ac, re: r.re }
  rowVisible.value = true
}
async function saveRow() {
  const f = rowForm.value
  await basicApi.saveAqlRow(f.row!.id, { sampleLetter: f.sampleLetter, sampleSize: f.sampleSize, ac: f.ac, re: f.re, version: f.row!.version })
  ElMessage.success('已保存')
  rowVisible.value = false
  load()
}
</script>

<template>
  <div v-loading="loading" class="aql-panel">
    <p class="hint">
      GB/T 2828.1 一次正常检验。检验单按“批量 + 检验水平 → 字码”“字码 + AQL → 样本量 n 与 Ac/Re”计算，箭头指向的方案已展开为实际使用的字码与样本量。
      <template v-if="canEdit">点击单元格可修改，修改只影响之后新建的检验单。</template>
    </p>

    <h4 class="title">样本量字码表</h4>
    <el-table :data="codeRows" border size="small" row-key="key">
      <el-table-column label="批量" width="160" fixed><template #default="{ row }">{{ row.range }}</template></el-table-column>
      <el-table-column v-for="lv in data?.levels ?? []" :key="lv" :label="lv" align="center" min-width="60">
        <template #default="{ row }">
          <span :class="{ cell: canEdit }" @click="editCode(row.cells[lv])">{{ row.cells[lv]?.codeLetter ?? '-' }}</span>
        </template>
      </el-table-column>
    </el-table>

    <h4 class="title">主表（n · Ac/Re）</h4>
    <el-table :data="mainRows" border size="small" row-key="letter">
      <el-table-column label="字码" width="70" fixed align="center"><template #default="{ row }">{{ row.letter }}</template></el-table-column>
      <el-table-column v-for="a in data?.aqls ?? []" :key="a" :label="a === '0' ? '0（零缺陷）' : a" align="center" min-width="92">
        <template #default="{ row }">
          <span v-if="row.cells[a]" :class="{ cell: canEdit }" @click="editRow(row.cells[a])">
            <span class="num">{{ row.cells[a].sampleSize }}</span>
            <span class="ac">{{ row.cells[a].ac }}/{{ row.cells[a].re }}</span>
            <span v-if="row.cells[a].sampleLetter !== row.letter" class="arrow">→{{ row.cells[a].sampleLetter }}</span>
          </span>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="codeVisible" title="修改样本量字码" width="400px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="批量">{{ codeForm.row?.lotMin }} ~ {{ codeForm.row?.lotMax ?? '以上' }}</el-form-item>
        <el-form-item label="检验水平">{{ codeForm.row?.inspectionLevel }}</el-form-item>
        <el-form-item label="字码">
          <el-select v-model="codeForm.letter" class="w-full"><el-option v-for="l in data?.letters ?? []" :key="l" :value="l" :label="l" /></el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="codeVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCode">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rowVisible" title="修改抽样方案" width="440px" append-to-body>
      <el-form label-width="110px">
        <el-form-item label="字码 / AQL">{{ rowForm.row?.codeLetter }} / {{ rowForm.row?.aql }}</el-form-item>
        <el-form-item label="实际使用字码">
          <el-select v-model="rowForm.sampleLetter" class="w-full"><el-option v-for="l in data?.letters ?? []" :key="l" :value="l" :label="l" /></el-select>
        </el-form-item>
        <el-form-item label="样本量 n"><el-input-number v-model="rowForm.sampleSize" :min="1" controls-position="right" class="w-full" /></el-form-item>
        <el-form-item label="Ac"><el-input-number v-model="rowForm.ac" :min="0" controls-position="right" class="w-full" /></el-form-item>
        <el-form-item label="Re"><el-input-number v-model="rowForm.re" :min="1" controls-position="right" class="w-full" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rowVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRow">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.aql-panel { padding: var(--erp-space-4); }
.aql-panel :deep(.el-table .cell) { white-space: nowrap; }
.hint { margin: 0 0 var(--erp-space-3); color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-secondary); }
.title { margin: var(--erp-space-4) 0 var(--erp-space-2); font-size: var(--erp-font-size-section-title); font-weight: var(--erp-font-weight-semibold); }
.cell { cursor: pointer; }
.cell:hover { color: var(--erp-color-primary); }
.ac { margin-left: var(--erp-space-1); color: var(--erp-color-text-secondary); }
.arrow { margin-left: var(--erp-space-1); color: var(--erp-color-text-tertiary); font-size: var(--erp-font-size-caption); }
</style>
