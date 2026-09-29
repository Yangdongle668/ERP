<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatQty } from '@/utils/format'
import OrderSelect from '../components/OrderSelect.vue'
import { MATERIAL_DOC_STATUS, num, RETURN_TYPE_OPTIONS, returnApi, type ReturnCandidate, type ReturnDetail } from '../api/production'

defineOptions({ name: 'MfgReturnEdit' })

/** 退料单编辑（需求 09-03 3.5，T4）：良品可退 = 已领 − 已退 − 理论耗用；不良可退 = 已领 − 已退，必须填写不良描述 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const saving = ref(false)
const detail = ref<ReturnDetail>()

interface Line extends Partial<ReturnCandidate> { materialLineId: string; qty?: string; batchNo?: string; defectDesc?: string; selected: boolean }
const form = ref<{ prodOrderId?: string; returnType: string; warehouseId?: string; remark?: string; lines: Line[] }>({ returnType: 'GOOD', lines: [] })
const guard = useLeaveGuard(() => form.value)
const loadingLines = ref(false)
const isDefect = computed(() => form.value.returnType === 'DEFECT')

async function loadCandidates() {
  const f = form.value
  if (!f.prodOrderId) {
    f.lines = []
    return
  }
  loadingLines.value = true
  try {
    const rows = await returnApi.candidates(f.prodOrderId, f.returnType, id.value)
    const keep = new Map(f.lines.map((l) => [l.materialLineId, l]))
    f.lines = rows.map((c) => {
      const k = keep.get(c.materialLineId)
      return { ...c, qty: k?.qty ?? (isDefect.value ? undefined : c.returnableQty), batchNo: k?.batchNo ?? c.batchNos[0], defectDesc: k?.defectDesc,
        selected: k?.selected ?? (!isDefect.value && num(c.returnableQty) > 0) }
    })
  } finally {
    loadingLines.value = false
  }
}

onMounted(async () => {
  if (id.value) {
    const d = await returnApi.get(id.value)
    if (d.status !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的退料单可以修改')
      router.replace(`/production/return/${id.value}`)
      return
    }
    detail.value = d
    form.value = {
      prodOrderId: d.prodOrderId, returnType: d.returnType, warehouseId: d.warehouseId, remark: d.remark,
      lines: d.lines.map((l) => ({ materialLineId: l.materialLineId, qty: l.qty, batchNo: l.batchNo, defectDesc: l.defectDesc, selected: true }))
    }
    tabs.setTitle(tabKeyOf(route), `编辑退料单 ${d.docNo}`)
    await loadCandidates()
  } else if (typeof route.query.prodOrderId === 'string') {
    form.value.prodOrderId = route.query.prodOrderId
    await loadCandidates()
  }
  guard.markClean()
})

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/production/return/${id.value}` : '/production/return')
}

async function save(submit: boolean) {
  if (saving.value) return
  const f = form.value
  if (!f.prodOrderId) return ElMessage.warning('请选择生产订单')
  const lines = f.lines.filter((l) => l.selected && num(l.qty) > 0)
  if (!lines.length) return ElMessage.warning('请至少选择一行并填写退料数量')
  for (const l of lines) {
    if (num(l.qty) > num(l.returnableQty)) return ElMessage.warning(`${l.code}：退料数量超过可退数量 ${formatQty(l.returnableQty)}`)
    if (isDefect.value && !l.defectDesc?.trim()) return ElMessage.warning(`${l.code}：不良退料必须填写不良描述`)
  }
  const data = {
    prodOrderId: f.prodOrderId, returnType: f.returnType, warehouseId: f.warehouseId, remark: f.remark?.trim() || undefined, version: detail.value?.version,
    lines: lines.map((l) => ({ materialLineId: l.materialLineId, qty: l.qty!, batchNo: l.batchNo || undefined, defectDesc: l.defectDesc?.trim() || undefined }))
  }
  saving.value = true
  try {
    let ids: string[]
    if (id.value) {
      await returnApi.update(id.value, data)
      ids = [id.value]
    } else {
      ids = (await returnApi.create(data)).ids
    }
    guard.markClean()
    if (submit) {
      for (const x of ids) {
        try {
          await returnApi.submit(x)
        } catch {
          if (!id.value) router.replace(`/production/return/${x}/edit`)
          return
        }
      }
      ElMessage.success('已提交，等待仓库确认入库')
    } else {
      ElMessage.success('保存成功')
    }
    tabs.remove([tabKeyOf(route)])
    router.push(ids.length === 1 ? `/production/return/${ids[0]}` : '/production/return')
  } finally {
    saving.value = false
  }
}

const title = computed(() => (detail.value ? `编辑退料单 ${detail.value.docNo}` : '新建退料单'))
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.status" :map="MATERIAL_DOC_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
      <el-button v-if="me.hasPermission('mfg:return:submit')" type="primary" :loading="saving" @click="save(true)">提交</el-button>
    </template>

    <ErpPanel title="基本信息">
      <el-form label-width="100px">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12">
            <el-form-item label="生产订单" required>
              <OrderSelect v-model="form.prodOrderId" statuses="RELEASED,IN_PROGRESS,SUSPENDED,COMPLETED" :disabled="!!id" @update:model-value="loadCandidates" />
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="退料类型">
              <el-radio-group v-model="form.returnType" :disabled="!!id" @change="form.lines = []; loadCandidates()">
                <el-radio v-for="o in RETURN_TYPE_OPTIONS" :key="String(o.value)" :value="o.value">{{ o.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="退入仓库"><WarehouseSelect v-model="form.warehouseId" :placeholder="isDefect ? '默认不良品仓' : '默认物料默认仓'" /></el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </ErpPanel>

    <ErpPanel title="退料明细" :description="isDefect ? '不良可退 = 已领 − 已退；品质会收到不良退料通知' : '良品可退 = 已领 − 已退 − 理论耗用（(完工 + 报废) × 单位用量）'">
      <el-table v-loading="loadingLines" :data="form.lines">
        <el-table-column label="" width="50"><template #default="{ row }"><el-checkbox v-model="row.selected" /></template></el-table-column>
        <el-table-column label="物料" min-width="200"><template #default="{ row }">{{ row.code }} {{ row.name }}</template></el-table-column>
        <el-table-column label="已领" width="90" align="right"><template #default="{ row }">{{ formatQty(row.issuedQty) }}</template></el-table-column>
        <el-table-column label="已退" width="90" align="right"><template #default="{ row }">{{ formatQty(row.returnedQty) }}</template></el-table-column>
        <el-table-column v-if="!isDefect" label="理论耗用" width="90" align="right"><template #default="{ row }">{{ formatQty(row.theoreticalQty) }}</template></el-table-column>
        <el-table-column label="可退" width="90" align="right"><template #default="{ row }">{{ formatQty(row.returnableQty) }}</template></el-table-column>
        <el-table-column label="退料数量" width="130"><template #default="{ row }"><QtyInput v-model="row.qty" :uom="row.uom" /></template></el-table-column>
        <el-table-column prop="uom" label="单位" width="60" />
        <el-table-column label="批次" width="150">
          <template #default="{ row }">
            <el-select v-if="row.batchNos?.length" v-model="row.batchNo" clearable><el-option v-for="b in row.batchNos" :key="b" :value="b" :label="b" /></el-select>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="isDefect" label="不良描述" min-width="200">
          <template #default="{ row }"><el-input v-model="row.defectDesc" maxlength="256" placeholder="必填，如来料引脚氧化" /></template>
        </el-table-column>
        <template #empty><ErpEmpty compact :description="form.prodOrderId ? '该订单没有已领料的物料' : '请先选择生产订单'" /></template>
      </el-table>
    </ErpPanel>
  </ErpPage>
</template>
