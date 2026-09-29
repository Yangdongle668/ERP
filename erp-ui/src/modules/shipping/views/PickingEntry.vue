<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatDateTime, formatQty } from '@/utils/format'
import { num, PICKING_STATUS, pickingApi, type BatchOption, type PickingDetail, type PickingLine } from '../api/shipping'

defineOptions({ name: 'ShpPickingEntry' })

/**
 * 拣货录入（需求 11-02 3.1）：推荐库位 / 批次 / 数量，录入实拣；可更换批次、拆行；扫码批次标签自动定位并录入整批数量。
 * 完成拣货校验每个通知行实拣合计 = 通知数量，不足时可“按实拣数量完成”（差额释放回订单）。
 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => String(route.params.id))
const d = ref<PickingDetail>()
const lines = ref<PickingLine[]>([])
const saving = ref(false)
const scan = ref('')

async function load() {
  d.value = await pickingApi.get(id.value)
  lines.value = d.value.lines.map((l) => ({ ...l, pickedQty: l.pickedQty }))
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}
onMounted(load)
const editable = computed(() => ['WAITING', 'PICKING'].includes(d.value?.pickingStatus ?? '') && me.hasPermission('shp:picking:pick'))
const pickedOf = (noticeLineId: string) => lines.value.filter((l) => l.noticeLineId === noticeLineId).reduce((s, l) => s + num(l.pickedQty), 0)

function fillSuggested() {
  for (const l of lines.value) l.pickedQty = l.shortage ? '0' : l.suggestedQty
}
function split(i: number) {
  const l = lines.value[i]
  lines.value.splice(i + 1, 0, { noticeLineId: l.noticeLineId, noticeLineNo: l.noticeLineNo, materialId: l.materialId, materialCode: l.materialCode,
    materialName: l.materialName, baseUom: l.baseUom, suggestedQty: '0', pickedQty: '0' })
}

// 更换批次
const batchDialog = ref(false)
const batchOptions = ref<BatchOption[]>([])
let batchLine: PickingLine | undefined
async function changeBatch(l: PickingLine) {
  batchLine = l
  batchOptions.value = await pickingApi.batches(id.value, l.noticeLineId)
  batchDialog.value = true
}
function chooseBatch(b: BatchOption) {
  if (batchLine) {
    batchLine.batchNo = b.batchNo
    batchLine.locationId = b.locationId
    batchLine.shortage = false
  }
  batchDialog.value = false
}

/** 扫码：批次号定位到行，录入该行推荐数量（整批）；未匹配时提示 */
function onScan() {
  const code = scan.value.trim()
  scan.value = ''
  if (!code) return
  const l = lines.value.find((x) => x.batchNo === code)
  if (!l) return ElMessage.warning(`没有批次为 ${code} 的拣货行，可使用“更换批次”`)
  l.pickedQty = String(num(l.pickedQty) + Math.max(num(l.suggestedQty) - num(l.pickedQty), 1))
}

async function save(silent = false) {
  saving.value = true
  try {
    await pickingApi.saveLines(id.value, lines.value.filter((l) => num(l.pickedQty) > 0 || num(l.suggestedQty) > 0).map((l) => ({
      noticeLineId: l.noticeLineId, batchNo: l.batchNo || undefined, locationId: l.locationId, suggestedQty: l.suggestedQty, pickedQty: l.pickedQty || '0',
      serialNos: l.serialNos?.trim() || undefined, shortage: l.shortage
    })))
    if (!silent) ElMessage.success('已保存')
    await load()
  } finally {
    saving.value = false
  }
}
async function complete() {
  await save(true)
  const short = (d.value?.summary ?? []).filter((s) => num(s.pickedQty) < num(s.qty) - num(s.shortageQty))
  let acceptShort = false
  if (short.length) {
    const text = short.map((s) => `第 ${s.lineNo} 行 ${s.materialCode}：实拣 ${formatQty(s.pickedQty)} / 通知 ${formatQty(num(s.qty) - num(s.shortageQty))}`).join('；')
    acceptShort = await ElMessageBox.confirm(`${text}。按实拣数量完成后，差额释放回订单并提醒船务。`, '拣货数量不足', {
      confirmButtonText: '按实拣完成', cancelButtonText: '继续拣货', type: 'warning'
    }).then(() => true).catch(() => false)
    if (!acceptShort) return
  }
  await pickingApi.complete(id.value, acceptShort)
  ElMessage.success('拣货完成')
  load()
}
async function start() {
  await pickingApi.start(id.value)
  load()
}
</script>

<template>
  <ErpPage :title="d ? `拣货 ${d.docNo}` : '拣货'" back="/shipping/picking" sticky>
    <template #meta><StatusTag v-if="d" :value="d.pickingStatus" :map="PICKING_STATUS" /></template>
    <template #actions>
      <PrintButton v-if="d" biz-type="SHP_PICKING" :ids="[id]" permission="shp:picking:print" />
      <template v-if="editable">
        <el-button v-if="d?.pickingStatus === 'WAITING'" @click="start">开始拣货</el-button>
        <el-button @click="fillSuggested">按推荐填入</el-button>
        <el-button :loading="saving" @click="save()">保存</el-button>
        <el-button type="primary" :loading="saving" @click="complete">完成拣货</el-button>
      </template>
    </template>
    <template v-if="d">
      <ErpPanel>
        <el-descriptions :column="4">
          <el-descriptions-item label="出货通知">
            <el-link type="primary" underline="never" @click="router.push(`/shipping/notice/${d.noticeId}`)">{{ d.noticeNo }}</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="客户">{{ d.customerName }}</el-descriptions-item>
          <el-descriptions-item label="出货日期">{{ d.shipDate }}</el-descriptions-item>
          <el-descriptions-item label="仓库">{{ d.warehouseName }}</el-descriptions-item>
          <el-descriptions-item label="拣货人">{{ d.pickerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="开始">{{ d.startedAt ? formatDateTime(d.startedAt) : '-' }}</el-descriptions-item>
          <el-descriptions-item label="完成">{{ d.completedAt ? formatDateTime(d.completedAt) : '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel title="通知行汇总">
        <el-table :data="d.summary">
          <el-table-column prop="lineNo" label="行" width="50" />
          <el-table-column label="物料" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
          <el-table-column label="通知数量" width="110" align="right"><template #default="{ row }">{{ formatQty(num(row.qty) - num(row.shortageQty)) }}</template></el-table-column>
          <el-table-column label="实拣合计" width="110" align="right">
            <template #default="{ row }">
              <span :class="{ short: pickedOf(row.noticeLineId) < num(row.qty) - num(row.shortageQty) }">{{ formatQty(pickedOf(row.noticeLineId)) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="缺货" width="90" align="right"><template #default="{ row }">{{ num(row.shortageQty) ? formatQty(row.shortageQty) : '' }}</template></el-table-column>
          <el-table-column prop="baseUom" label="单位" width="60" />
        </el-table>
      </ErpPanel>

      <ErpPanel title="拣货明细" description="按库位顺序拣货；批次不足时可更换批次或拆行">
        <template #extra>
          <el-input v-if="editable" v-model="scan" placeholder="扫描批次标签" clearable class="scan" @keyup.enter="onScan">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </template>
        <el-table :data="lines">
          <el-table-column prop="noticeLineNo" label="通知行" width="70" />
          <el-table-column label="物料" min-width="180"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
          <el-table-column label="库位" width="90"><template #default="{ row }">{{ row.locationId ?? '-' }}</template></el-table-column>
          <el-table-column label="批次" width="170">
            <template #default="{ row }">
              <span>{{ row.batchNo || '-' }}</span>
              <ErpBadge v-if="row.shortage" type="warning" :dot="false" class="gap-l">缺货</ErpBadge>
            </template>
          </el-table-column>
          <el-table-column label="推荐" width="100" align="right"><template #default="{ row }">{{ formatQty(row.suggestedQty) }}</template></el-table-column>
          <el-table-column label="实拣" width="140">
            <template #default="{ row }">
              <QtyInput v-if="editable" v-model="row.pickedQty" :uom="row.baseUom" /><span v-else>{{ formatQty(row.pickedQty) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="序列号" min-width="140">
            <template #default="{ row }"><el-input v-if="editable" v-model="row.serialNos" placeholder="多个用逗号分隔" /><span v-else>{{ row.serialNos }}</span></template>
          </el-table-column>
          <el-table-column v-if="editable" label="" width="150">
            <template #default="{ row, $index }">
              <el-button link type="primary" @click="changeBatch(row as PickingLine)">更换批次</el-button>
              <el-button link type="primary" @click="split($index)">拆行</el-button>
            </template>
          </el-table-column>
        </el-table>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="batchDialog" title="更换批次（出货仓可用批次）" width="640px">
      <el-table :data="batchOptions" @row-click="chooseBatch">
        <el-table-column prop="batchNo" label="批次" min-width="160" />
        <el-table-column prop="locationId" label="库位" width="90" />
        <el-table-column label="可用数量" width="110" align="right"><template #default="{ row }">{{ formatQty(row.availableQty) }}</template></el-table-column>
        <el-table-column prop="productionDate" label="生产日期" width="110" />
        <el-table-column prop="expireDate" label="到期日" width="110" />
        <template #empty><ErpEmpty compact description="出货仓没有该物料的可用库存" /></template>
      </el-table>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.short { color: var(--erp-color-warning); }
.scan { width: 240px; }
.gap-l { margin-left: var(--erp-space-2); }
</style>
