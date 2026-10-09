<script setup lang="ts">
import { computed, ref } from 'vue'
import { exprOf, type LayoutColumn, type LayoutField, type LayoutSign, type PrintLayout } from '@/utils/print/layoutTemplate'
import type { PrintVariable } from '../api/print'

/**
 * 可视化版式设计（需求 01-09 第 9 节）：勾选要打印的条目，改名称、顺序、宽度 / 占列，添加条目（单据字段或手写空白项）。
 * 每次修改都通过 v-model 回传，编辑器据此重新生成模板并实时预览。
 */
const layout = defineModel<PrintLayout>({ required: true })
const props = defineProps<{ variables: PrintVariable[]; disabled?: boolean }>()

/** 各单据打印数据都会带的签名 / 抬头类字段（不在单据变量声明里） */
const COMMON: PrintVariable[] = [
  { path: 'createdByName', name: '制单人', type: 'string' },
  { path: 'auditByName', name: '审核人', type: 'string' },
  { path: 'ownerName', name: '责任人 / 业务员', type: 'string' },
  { path: 'deptName', name: '部门 / 车间', type: 'string' },
  { path: 'statusName', name: '状态', type: 'string' },
  { path: 'remark', name: '备注', type: 'string' },
  { path: 'customerCode', name: '客户编号', type: 'string' }
]

const headVars = computed(() => {
  const own = props.variables.filter((v) => !v.path.includes('.') && v.type !== 'array' && v.type !== 'object')
  const seen = new Set(own.map((v) => v.path))
  return [...own, ...COMMON.filter((v) => !seen.has(v.path))]
})
const lineVars = computed(() => props.variables.filter((v) => v.path.startsWith('lines.')))

const NUMERIC = new Set(['qty', 'amount', 'price', 'number'])
const uid = (prefix: string) => `${prefix}${Date.now().toString(36)}${Math.random().toString(36).slice(2, 5)}`
const isAmount = (c: LayoutColumn) => /formatAmount/.test(c.expr)
const isQty = (c: LayoutColumn) => /formatQty/.test(c.expr)
const fieldOf = (c: LayoutColumn) => /\b(?:formatQty|formatAmount) (\w+)/.exec(c.expr)?.[1] ?? c.key

function move<T>(list: T[], i: number, d: number) {
  const j = i + d
  if (j < 0 || j >= list.length) return
  ;[list[i], list[j]] = [list[j], list[i]]
}
function remove<T>(list: T[], i: number) {
  list.splice(i, 1)
}

// ---------- 添加条目 ----------
function addInfo(path: string) {
  if (path === '__blank') {
    layout.value.info.push({ key: uid('x'), label: '新条目', expr: '', span: 1, visible: true })
    return
  }
  const v = headVars.value.find((x) => x.path === path)
  if (v) layout.value.info.push({ key: uid('x'), label: v.name.replace(/（.*$/, ''), expr: exprOf(v.path, v.type), span: 1, visible: true })
}
function addColumn(path: string) {
  if (path === '__blank') {
    layout.value.columns.push({ key: uid('x'), label: '空白列', width: 16, expr: '', align: 'left', visible: true })
    return
  }
  const v = lineVars.value.find((x) => x.path === path)
  if (!v) return
  const field = v.path.replace(/^lines\./, '')
  const numeric = NUMERIC.has(v.type)
  layout.value.columns.push({
    key: uid('x'), label: v.name, width: numeric ? 20 : 26, expr: exprOf(v.path, v.type, true),
    align: numeric ? 'right' : v.path.endsWith('uom') ? 'center' : 'left', visible: true,
    total: v.type === 'qty' ? { kind: 'qty', field } : v.type === 'amount' ? { kind: 'amount', field } : null
  })
}
function addSign(path: string) {
  if (path === '__blank') {
    layout.value.signs.push({ key: uid('x'), label: '签名', expr: '', wide: false, visible: true })
    return
  }
  const v = headVars.value.find((x) => x.path === path)
  if (v) layout.value.signs.push({ key: uid('x'), label: v.name.replace(/（.*$/, '').replace(/人$/, ''), expr: `{{${v.path}}}`, wide: false, visible: true })
}

/** 合计：数量 / 金额列可勾选 */
function canTotal(c: LayoutColumn) {
  return isQty(c) || isAmount(c)
}
function setTotal(c: LayoutColumn, on: string | number | boolean) {
  c.total = on ? { kind: isAmount(c) ? 'amount' : 'qty', field: fieldOf(c), docExpr: c.total?.docExpr ?? null } : null
}

const asField = (r: unknown) => r as LayoutField
const asCol = (r: unknown) => r as LayoutColumn
const asSign = (r: unknown) => r as LayoutSign
const open = ref(['head', 'info', 'columns', 'signs', 'other'])
const FONT_SIZES = [8, 8.5, 9, 9.5, 10]
const ALIGNS = [{ value: 'left', label: '左' }, { value: 'center', label: '中' }, { value: 'right', label: '右' }]
const visibleCount = (list: { visible: boolean }[]) => list.filter((x) => x.visible).length
</script>

<template>
  <div class="designer" :class="{ 'is-disabled': disabled }">
    <el-collapse v-model="open">
      <el-collapse-item name="head" title="页眉">
        <div class="opts">
          <el-checkbox v-model="layout.header.logo" :disabled="disabled">公司 Logo</el-checkbox>
          <el-checkbox v-model="layout.header.nameEn" :disabled="disabled">公司英文名</el-checkbox>
          <el-checkbox v-model="layout.header.barcode" :disabled="disabled">单号条码</el-checkbox>
          <span class="opt-label">字号</span>
          <el-select v-model="layout.fontSize" size="small" class="w80" :disabled="disabled">
            <el-option v-for="f in FONT_SIZES" :key="f" :value="f" :label="`${f}pt`" />
          </el-select>
        </div>
        <div class="opts">
          <span class="opt-label">标题</span><el-input v-model="layout.title" size="small" :disabled="disabled" class="grow" />
        </div>
        <div class="opts">
          <span class="opt-label">副标题</span><el-input v-model="layout.subtitle" size="small" :disabled="disabled" placeholder="可留空" class="grow" />
        </div>
      </el-collapse-item>

      <el-collapse-item name="info">
        <template #title>单头信息<span class="count">{{ visibleCount(layout.info) }}/{{ layout.info.length }}</span></template>
        <el-table :data="layout.info" size="small" row-key="key">
          <el-table-column label="打印" width="52" align="center">
            <template #default="{ row }"><el-checkbox v-model="asField(row).visible" :disabled="disabled" /></template>
          </el-table-column>
          <el-table-column label="名称" min-width="110">
            <template #default="{ row }"><el-input v-model="asField(row).label" size="small" maxlength="16" :disabled="disabled" /></template>
          </el-table-column>
          <el-table-column label="占列" width="82">
            <template #default="{ row }">
              <el-select v-model="asField(row).span" size="small" :disabled="disabled"><el-option v-for="n in 4" :key="n" :value="n" :label="String(n)" /></el-select>
            </template>
          </el-table-column>
          <el-table-column label="内容" min-width="90">
            <template #default="{ row }"><span class="text-muted expr">{{ asField(row).expr || '（手写）' }}</span></template>
          </el-table-column>
          <el-table-column v-if="!disabled" width="128" align="right">
            <template #default="{ $index }">
              <ErpIconButton icon="Up" tooltip="上移" :disabled="$index === 0" @click="move(layout.info, $index, -1)" />
              <ErpIconButton icon="Down" tooltip="下移" :disabled="$index === layout.info.length - 1" @click="move(layout.info, $index, 1)" />
              <ErpIconButton icon="Delete" tooltip="删除" @click="remove(layout.info, $index)" />
            </template>
          </el-table-column>
        </el-table>
        <el-select v-if="!disabled" model-value="" size="small" filterable placeholder="＋ 添加单头条目" class="add" @update:model-value="addInfo">
          <el-option value="__blank" label="空白项（打印标签，内容手写）" />
          <el-option v-for="v in headVars" :key="v.path" :value="v.path" :label="`${v.name}（${v.path}）`" />
        </el-select>
      </el-collapse-item>

      <el-collapse-item name="columns">
        <template #title>明细列<span class="count">{{ visibleCount(layout.columns) }}/{{ layout.columns.length }}</span></template>
        <el-table :data="layout.columns" size="small" row-key="key">
          <el-table-column label="打印" width="52" align="center">
            <template #default="{ row }"><el-checkbox v-model="asCol(row).visible" :disabled="disabled" /></template>
          </el-table-column>
          <el-table-column label="列名" min-width="100">
            <template #default="{ row }"><el-input v-model="asCol(row).label" size="small" maxlength="12" :disabled="disabled" /></template>
          </el-table-column>
          <el-table-column label="宽度" width="86">
            <template #default="{ row }">
              <el-input-number v-model="asCol(row).width" size="small" :min="4" :max="120" :controls="false" :disabled="disabled" class="w60" />
            </template>
          </el-table-column>
          <el-table-column label="对齐" width="76">
            <template #default="{ row }">
              <el-select v-model="asCol(row).align" size="small" :disabled="disabled"><el-option v-for="a in ALIGNS" :key="a.value" v-bind="a" /></el-select>
            </template>
          </el-table-column>
          <el-table-column label="合计" width="52" align="center">
            <template #default="{ row }">
              <el-checkbox v-if="canTotal(asCol(row))" :model-value="!!asCol(row).total" :disabled="disabled" @update:model-value="setTotal(asCol(row), $event)" />
            </template>
          </el-table-column>
          <el-table-column v-if="!disabled" width="128" align="right">
            <template #default="{ $index }">
              <ErpIconButton icon="Up" tooltip="左移" :disabled="$index === 0" @click="move(layout.columns, $index, -1)" />
              <ErpIconButton icon="Down" tooltip="右移" :disabled="$index === layout.columns.length - 1" @click="move(layout.columns, $index, 1)" />
              <ErpIconButton icon="Delete" tooltip="删除" @click="remove(layout.columns, $index)" />
            </template>
          </el-table-column>
        </el-table>
        <div class="tip text-muted">宽度为相对值：取消勾选的列不占位置，其余列按宽度比例铺满整行</div>
        <el-select v-if="!disabled" model-value="" size="small" filterable placeholder="＋ 添加明细列" class="add" @update:model-value="addColumn">
          <el-option value="__blank" label="空白列（手写）" />
          <el-option v-for="v in lineVars" :key="v.path" :value="v.path" :label="`${v.name}（${v.path.replace(/^lines\./, '')}）`" />
        </el-select>
      </el-collapse-item>

      <el-collapse-item name="signs">
        <template #title>签名栏<span class="count">{{ visibleCount(layout.signs) }}/{{ layout.signs.length }}</span></template>
        <el-table :data="layout.signs" size="small" row-key="key">
          <el-table-column label="打印" width="52" align="center">
            <template #default="{ row }"><el-checkbox v-model="asSign(row).visible" :disabled="disabled" /></template>
          </el-table-column>
          <el-table-column label="名称" min-width="120">
            <template #default="{ row }"><el-input v-model="asSign(row).label" size="small" maxlength="20" :disabled="disabled" /></template>
          </el-table-column>
          <el-table-column label="姓名" min-width="90">
            <template #default="{ row }"><span class="text-muted expr">{{ asSign(row).expr || '（手签）' }}</span></template>
          </el-table-column>
          <el-table-column label="双倍宽" width="64" align="center">
            <template #default="{ row }"><el-checkbox v-model="asSign(row).wide" :disabled="disabled" /></template>
          </el-table-column>
          <el-table-column v-if="!disabled" width="128" align="right">
            <template #default="{ $index }">
              <ErpIconButton icon="Up" tooltip="左移" :disabled="$index === 0" @click="move(layout.signs, $index, -1)" />
              <ErpIconButton icon="Down" tooltip="右移" :disabled="$index === layout.signs.length - 1" @click="move(layout.signs, $index, 1)" />
              <ErpIconButton icon="Delete" tooltip="删除" @click="remove(layout.signs, $index)" />
            </template>
          </el-table-column>
        </el-table>
        <el-select v-if="!disabled" model-value="" size="small" filterable placeholder="＋ 添加签名项" class="add" @update:model-value="addSign">
          <el-option value="__blank" label="空白签名项（手签）" />
          <el-option v-for="v in headVars.filter((x) => /Name$/.test(x.path))" :key="v.path" :value="v.path" :label="`${v.name}（自动带出姓名）`" />
        </el-select>
      </el-collapse-item>

      <el-collapse-item name="other" title="备注与页脚">
        <div class="opts">
          <el-checkbox v-model="layout.memo.visible" :disabled="disabled">备注行</el-checkbox>
          <el-checkbox v-model="layout.footer.printInfo" :disabled="disabled">打印时间 / 打印人 / 补打次数</el-checkbox>
          <el-checkbox v-model="layout.footer.address" :disabled="disabled">公司地址电话</el-checkbox>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style scoped>
.designer { padding: 0 var(--erp-space-4) var(--erp-space-4); }
.opts { display: flex; align-items: center; flex-wrap: wrap; gap: var(--erp-space-3); margin-bottom: var(--erp-space-2); }
.opt-label { color: var(--erp-color-text-secondary); white-space: nowrap; min-width: 40px; }
.grow { flex: 1; }
.w80 { width: 80px; }
.w60 { width: 60px; }
.count { margin-left: var(--erp-space-2); color: var(--erp-color-text-tertiary); font-size: var(--erp-font-size-caption); }
.add { width: 100%; margin-top: var(--erp-space-2); }
.tip { font-size: var(--erp-font-size-caption); margin-top: var(--erp-space-1); }
.expr { font-family: var(--erp-font-family-mono); font-size: var(--erp-font-size-caption); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; display: block; }
</style>
