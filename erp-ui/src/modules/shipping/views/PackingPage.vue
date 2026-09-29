<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TableColumn } from '@/components'
import { formatQty } from '@/utils/format'
import { NOTICE_STATUS, noticeApi, num, OQC_RESULT, type Carton, type NoticeRow, type PackItem, type PackingView } from '../api/shipping'

defineOptions({ name: 'ShpPackingPage' })

/**
 * 装箱（需求 11-02 3.2）：路由 /shipping/packing?noticeId=。按规格批量装箱（尾箱为余数）、手工新增混装箱、编辑 / 删除箱、打印箱唛、完成装箱。
 * 未指定通知时列出待装箱的出货通知。
 */
const route = useRoute()
const router = useRouter()
const noticeId = computed(() => (typeof route.query.noticeId === 'string' ? route.query.noticeId : undefined))
const v = ref<PackingView>()
const pending = ref<NoticeRow[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    if (noticeId.value) {
      v.value = await noticeApi.packing(noticeId.value)
    } else {
      v.value = undefined
      pending.value = (await noticeApi.page({ pageNo: 1, pageSize: 100, statuses: 'PICKING,PACKED,OQC,READY' } as never)).list
    }
  } finally {
    loading.value = false
  }
}
watch(noticeId, load, { immediate: true })

const pendingColumns: TableColumn<NoticeRow>[] = [
  { prop: 'docNo', label: '出货通知', width: 160, type: 'link', onClick: (r) => router.push({ path: '/shipping/packing', query: { noticeId: r.id } }) },
  { prop: 'customerName', label: '客户', minWidth: 150 },
  { prop: 'shipDate', label: '出货日期', width: 110, type: 'date' },
  { prop: 'totalQty', label: '数量', width: 100, type: 'qty' },
  { prop: 'pickedQty', label: '已拣货', width: 100, type: 'qty' },
  { prop: 'packedQty', label: '已装箱', width: 100, type: 'qty' },
  { prop: 'noticeStatus', label: '状态', width: 90, type: 'status', statusMap: NOTICE_STATUS }
]

/** 相同内容的连续箱合并显示为“1-12” */
interface Group { from: number; to: number; cartons: Carton[]; first: Carton }
const groups = computed<Group[]>(() => {
  const out: Group[] = []
  for (const c of v.value?.cartons ?? []) {
    const last = out[out.length - 1]
    if (last && same(last.first, c) && c.cartonNo === last.to + 1) {
      last.to = c.cartonNo
      last.cartons.push(c)
    } else {
      out.push({ from: c.cartonNo, to: c.cartonNo, cartons: [c], first: c })
    }
  }
  return out
})
function same(a: Carton, b: Carton) {
  if (a.lines.length !== 1 || b.lines.length !== 1 || (a.shipmentId ?? '') !== (b.shipmentId ?? '')) return false
  const x = a.lines[0]
  const y = b.lines[0]
  return x.noticeLineId === y.noticeLineId && (x.batchNo ?? '') === (y.batchNo ?? '') && num(x.qty) === num(y.qty)
    && num(a.grossWeightKg) === num(b.grossWeightKg) && num(a.cbm) === num(b.cbm)
}
const itemKey = (i: PackItem) => `${i.noticeLineId}|${i.batchNo ?? ''}`

// 按规格批量装箱
const batch = ref({ key: '', qtyPerCarton: '', totalQty: '', cartonSpec: '', lengthCm: '', widthCm: '', heightCm: '', tareWeightKg: '', palletNo: '' })
const selectedItem = computed(() => v.value?.items.find((i) => itemKey(i) === batch.value.key))
const cartonCount = computed(() => {
  const per = num(batch.value.qtyPerCarton)
  const total = num(batch.value.totalQty) || num(selectedItem.value?.remainingQty)
  return per > 0 ? Math.ceil(total / per) : 0
})
function pickItem(i: PackItem) {
  batch.value.key = itemKey(i)
  batch.value.totalQty = ''
}
async function confirmUnpack() {
  const st = v.value?.noticeStatus
  if (st === 'OQC' || st === 'READY' || st === 'PACKED') {
    return ElMessageBox.confirm(st === 'PACKED' ? '已完成装箱，修改后需要重新完成装箱，确定吗？' : '已申请 OQC，修改装箱将重新提交 OQC，确定吗？', '修改装箱', { type: 'warning' })
      .then(() => true).catch(() => false)
  }
  return true
}
async function doBatch() {
  const it = selectedItem.value
  if (!it) return ElMessage.warning('请选择待装物料')
  if (!(num(batch.value.qtyPerCarton) > 0)) return ElMessage.warning('请填写每箱数量')
  if (!(await confirmUnpack())) return
  const b = batch.value
  const ids = await noticeApi.batchPack(noticeId.value!, {
    noticeLineId: it.noticeLineId, batchNo: it.batchNo, qtyPerCarton: b.qtyPerCarton, totalQty: b.totalQty || undefined, cartonSpec: b.cartonSpec || undefined,
    lengthCm: b.lengthCm || undefined, widthCm: b.widthCm || undefined, heightCm: b.heightCm || undefined, tareWeightKg: b.tareWeightKg || undefined,
    palletNo: b.palletNo || undefined
  })
  ElMessage.success(`已生成 ${ids.length} 箱`)
  load()
}

// 手工新增 / 编辑箱（可混装）
interface CartonForm {
  id?: string; cartonSpec?: string; lengthCm?: string; widthCm?: string; heightCm?: string; grossWeightKg?: string; netWeightKg?: string; tareWeightKg?: string
  palletNo?: string; lines: { key: string; qty: string; serialNos?: string }[]
}
const cartonDialog = ref(false)
const cf = ref<CartonForm>({ lines: [] })
function openCarton(c?: Carton) {
  cf.value = c ? {
    id: c.id, cartonSpec: c.cartonSpec, lengthCm: c.lengthCm, widthCm: c.widthCm, heightCm: c.heightCm, grossWeightKg: c.grossWeightKg,
    netWeightKg: c.netWeightKg, palletNo: c.palletNo, lines: c.lines.map((l) => ({ key: `${l.noticeLineId}|${l.batchNo ?? ''}`, qty: l.qty, serialNos: l.serialNos }))
  } : { lines: [{ key: '', qty: '' }] }
  cartonDialog.value = true
}
async function saveCarton() {
  const lines = cf.value.lines.filter((l) => l.key && num(l.qty) > 0)
  if (!lines.length) return ElMessage.warning('请至少填写一行箱内物料')
  if (!(await confirmUnpack())) return
  const data = {
    cartonSpec: cf.value.cartonSpec || undefined, lengthCm: cf.value.lengthCm || undefined, widthCm: cf.value.widthCm || undefined, heightCm: cf.value.heightCm || undefined,
    grossWeightKg: cf.value.grossWeightKg || undefined, netWeightKg: cf.value.netWeightKg || undefined, tareWeightKg: cf.value.tareWeightKg || undefined,
    palletNo: cf.value.palletNo || undefined,
    lines: lines.map((l) => {
      const [noticeLineId, batchNo] = l.key.split('|')
      return { noticeLineId, batchNo: batchNo || undefined, qty: l.qty, serialNos: l.serialNos?.trim() || undefined }
    })
  }
  if (cf.value.id) await noticeApi.updateCarton(cf.value.id, data)
  else await noticeApi.addCarton(noticeId.value!, data)
  cartonDialog.value = false
  ElMessage.success('已保存')
  load()
}
async function removeGroup(g: Group) {
  if (!(await confirmUnpack())) return
  await ElMessageBox.confirm(`删除箱 ${g.from === g.to ? g.from : `${g.from}-${g.to}`}？`, '删除', { type: 'warning' })
  for (const c of [...g.cartons].reverse()) await noticeApi.deleteCarton(c.id)
  load()
}
async function clearAll() {
  if (!(await confirmUnpack())) return
  await ElMessageBox.confirm('清空全部未出货的箱？', '清空', { type: 'warning' })
  await noticeApi.clearCartons(noticeId.value!)
  load()
}
async function complete() {
  let oqc = false
  if (v.value?.oqcRequired) {
    oqc = await ElMessageBox.confirm('该出货通知需要 OQC，是否同时申请 OQC？', '完成装箱', { confirmButtonText: '完成并申请 OQC', cancelButtonText: '仅完成装箱',
      distinguishCancelAndClose: true }).then(() => true).catch((a) => (a === 'cancel' ? false : Promise.reject(a)))
  }
  await noticeApi.packComplete(noticeId.value!, oqc)
  ElMessage.success(oqc ? '已完成装箱并申请 OQC' : '已完成装箱')
  load()
}
async function requestOqc() {
  await noticeApi.requestOqc(noticeId.value!)
  ElMessage.success('已申请 OQC')
  load()
}
const groupText = (g: Group) => (g.from === g.to ? String(g.from) : `${g.from}-${g.to}`)
</script>

<template>
  <ErpPage v-if="!noticeId" description="选择出货通知进行装箱（拣货完成后可装箱）">
    <ErpPanel>
      <ErpTable :columns="pendingColumns" :data="pending" :loading="loading" no-toolbar>
        <template #empty><ErpEmpty compact description="没有待装箱的出货通知" /></template>
      </ErpTable>
    </ErpPanel>
  </ErpPage>
  <ErpPage v-else :title="v ? `装箱：${v.noticeNo}` : '装箱'" back="/shipping/packing" sticky>
    <template #meta>
      <StatusTag v-if="v" :value="v.noticeStatus" :map="NOTICE_STATUS" />
      <StatusTag v-if="v?.oqcResult" :value="v.oqcResult" :map="OQC_RESULT" />
    </template>
    <template #actions>
      <el-button @click="router.push(`/shipping/notice/${noticeId}`)">出货通知</el-button>
      <PrintButton v-if="v?.cartons.length" biz-type="SHP_CARTON_LABEL" :ids="[noticeId]" permission="shp:packing:print-label" label="打印全部箱唛" />
      <el-button v-if="v?.noticeStatus === 'PACKED' && v.oqcRequired" v-perm="'shp:packing:pack'" @click="requestOqc">申请 OQC</el-button>
      <el-button v-if="v?.editable && v.noticeStatus === 'PICKING'" v-perm="'shp:packing:pack'" type="primary" :disabled="!v.complete" @click="complete">完成装箱</el-button>
    </template>
    <template v-if="v">
      <el-alert v-if="!v.packingEnabled" type="info" :closable="false" title="未启用装箱：拣货完成即已装箱，Packing List 按出货单行生成" class="gap-b" />
      <el-alert v-else-if="!v.editable && v.noticeStatus === 'PICKING'" type="warning" :closable="false" title="拣货尚未完成，不能装箱" class="gap-b" />
      <ErpPanel title="待装" :description="`${v.customerName ?? ''}  出货日期 ${v.shipDate ?? ''}`">
        <el-table :data="v.items" highlight-current-row @row-click="(r: PackItem) => pickItem(r)">
          <el-table-column prop="lineNo" label="行" width="50" />
          <el-table-column label="物料" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
          <el-table-column prop="customerPartNo" label="客户料号" width="120" />
          <el-table-column prop="batchNo" label="批次" width="150" />
          <el-table-column label="实拣" width="100" align="right"><template #default="{ row }">{{ formatQty(row.pickedQty) }}</template></el-table-column>
          <el-table-column label="已装" width="100" align="right"><template #default="{ row }">{{ formatQty(row.packedQty) }}</template></el-table-column>
          <el-table-column label="剩余" width="100" align="right">
            <template #default="{ row }"><strong :class="{ remain: num(row.remainingQty) > 0 }">{{ formatQty(row.remainingQty) }}</strong></template>
          </el-table-column>
          <el-table-column prop="uom" label="单位" width="60" />
        </el-table>
      </ErpPanel>

      <ErpPanel v-if="v.editable" title="按规格批量装箱" description="自动生成连续箱号，尾箱数量为余数；毛重 = 净重（物料单位净重 × 数量）+ 箱皮重">
        <el-form inline class="batch-form">
          <el-form-item label="物料">
            <el-select v-model="batch.key" placeholder="选择待装物料" class="item-select">
              <el-option v-for="i in v.items" :key="itemKey(i)" :value="itemKey(i)" :disabled="num(i.remainingQty) <= 0"
                         :label="`${i.materialCode} ${i.batchNo ? `批次 ${i.batchNo}` : ''} 剩余 ${formatQty(i.remainingQty)}`" />
            </el-select>
          </el-form-item>
          <el-form-item label="每箱数量"><el-input v-model="batch.qtyPerCarton" class="short-input" /></el-form-item>
          <el-form-item label="装箱数量"><el-input v-model="batch.totalQty" :placeholder="selectedItem ? `剩余 ${formatQty(selectedItem.remainingQty)}` : ''" class="short-input" /></el-form-item>
          <el-form-item label="箱规格"><DictSelect v-model="batch.cartonSpec" type="shp_carton_spec" class="short-input" /></el-form-item>
          <el-form-item label="长×宽×高 cm">
            <el-input v-model="batch.lengthCm" class="dim" /> × <el-input v-model="batch.widthCm" class="dim" /> × <el-input v-model="batch.heightCm" class="dim" />
          </el-form-item>
          <el-form-item label="箱皮重 kg"><el-input v-model="batch.tareWeightKg" class="dim" /></el-form-item>
          <el-form-item label="托盘"><el-input v-model="batch.palletNo" class="dim" maxlength="16" /></el-form-item>
          <el-form-item>
            <span class="hint">箱数 {{ cartonCount }}</span>
            <el-button v-perm="'shp:packing:pack'" type="primary" @click="doBatch">生成</el-button>
          </el-form-item>
        </el-form>
      </ErpPanel>

      <ErpPanel title="箱">
        <template #extra>
          <span class="total">合计：{{ v.totals.cartonCount }} 箱　数量 {{ formatQty(v.totals.qty) }}　毛重 {{ v.totals.grossWeight }} kg　净重 {{ v.totals.netWeight }} kg　体积 {{ v.totals.cbm }} m³</span>
          <template v-if="v.editable">
            <el-button v-perm="'shp:packing:pack'" icon="Plus" @click="openCarton()">新增箱（混装）</el-button>
            <el-button v-perm="'shp:packing:pack'" @click="clearAll">清空</el-button>
          </template>
        </template>
        <el-table :data="groups">
          <el-table-column label="箱号" width="90"><template #default="{ row }">{{ groupText(row as Group) }}</template></el-table-column>
          <el-table-column label="内容" min-width="260">
            <template #default="{ row }">
              <div v-for="l in row.first.lines" :key="l.id">{{ l.materialCode }} {{ l.materialName }} {{ l.batchNo ? `批次 ${l.batchNo}` : '' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="110" align="right">
            <template #default="{ row }">
              <div v-for="l in row.first.lines" :key="l.id">{{ formatQty(l.qty) }}{{ row.cartons.length > 1 ? '/箱' : '' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="尺寸 cm" width="120">
            <template #default="{ row }">{{ row.first.lengthCm ? `${row.first.lengthCm}×${row.first.widthCm}×${row.first.heightCm}` : '-' }}</template>
          </el-table-column>
          <el-table-column label="毛重" width="80" align="right"><template #default="{ row }">{{ row.first.grossWeightKg ?? '-' }}</template></el-table-column>
          <el-table-column label="净重" width="80" align="right"><template #default="{ row }">{{ row.first.netWeightKg ?? '-' }}</template></el-table-column>
          <el-table-column label="CBM" width="80" align="right"><template #default="{ row }">{{ row.first.cbm ?? '-' }}</template></el-table-column>
          <el-table-column label="出货单" width="140"><template #default="{ row }">{{ row.first.shipmentNo ?? '' }}</template></el-table-column>
          <el-table-column v-if="v.editable" label="" width="110">
            <template #default="{ row }">
              <template v-if="!row.first.shipmentId">
                <el-button v-if="row.cartons.length === 1" link type="primary" @click="openCarton(row.first)">编辑</el-button>
                <el-button link type="danger" @click="removeGroup(row as Group)">删除</el-button>
              </template>
            </template>
          </el-table-column>
          <template #empty><ErpEmpty compact description="选择待装物料后按规格批量装箱，或新增混装箱" /></template>
        </el-table>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="cartonDialog" :title="cf.id ? '编辑箱' : '新增箱'" width="720px">
      <el-form label-width="90px">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="箱规格"><DictSelect v-model="cf.cartonSpec" type="shp_carton_spec" /></el-form-item></el-col>
          <el-col :span="16">
            <el-form-item label="长×宽×高">
              <el-input v-model="cf.lengthCm" class="dim" /> × <el-input v-model="cf.widthCm" class="dim" /> × <el-input v-model="cf.heightCm" class="dim" />
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="净重 kg"><el-input v-model="cf.netWeightKg" placeholder="默认按物料" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="毛重 kg"><el-input v-model="cf.grossWeightKg" placeholder="默认净重+皮重" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="箱皮重 kg"><el-input v-model="cf.tareWeightKg" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="托盘"><el-input v-model="cf.palletNo" maxlength="16" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <el-table :data="cf.lines">
        <el-table-column label="物料 / 批次" min-width="260">
          <template #default="{ row }">
            <el-select v-model="row.key" class="w-full">
              <el-option v-for="i in v?.items ?? []" :key="itemKey(i)" :value="itemKey(i)" :label="`${i.materialCode} ${i.batchNo ? `批次 ${i.batchNo}` : ''}（剩余 ${formatQty(i.remainingQty)}）`" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="130"><template #default="{ row }"><el-input v-model="row.qty" /></template></el-table-column>
        <el-table-column label="序列号" width="160"><template #default="{ row }"><el-input v-model="row.serialNos" /></template></el-table-column>
        <el-table-column label="" width="60"><template #default="{ $index }"><el-button link type="danger" @click="cf.lines.splice($index, 1)">删除</el-button></template></el-table-column>
      </el-table>
      <el-button link type="primary" icon="Plus" class="gap-t" @click="cf.lines.push({ key: '', qty: '' })">添加物料</el-button>
      <template #footer>
        <el-button @click="cartonDialog = false">取消</el-button>
        <el-button type="primary" @click="saveCarton">保存</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.gap-b { margin-bottom: var(--erp-section-gap); }
.gap-t { margin-top: var(--erp-space-2); }
.batch-form :deep(.el-form-item) { margin-bottom: var(--erp-space-2); }
.item-select { width: 280px; }
.short-input { width: 120px; }
.dim { width: 70px; }
.hint { margin-right: var(--erp-space-3); color: var(--erp-color-text-secondary); }
.total { margin-right: var(--erp-space-3); color: var(--erp-color-text-secondary); }
.remain { color: var(--erp-color-warning); }
</style>
