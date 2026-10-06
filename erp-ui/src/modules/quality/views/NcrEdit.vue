<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatQty } from '@/utils/format'
import {
  DISPOSITION, NCR_SOURCE, SEVERITY, allowedDispositions, basicApi, ncrApi, num, optionsOf, type DefectCodeRow, type DispositionRow, type NcrDetail
} from '../api/quality'

defineOptions({ name: 'QcNcrEdit' })

/** NCR 编辑（需求 10-03 3.2，T4）：单头 + 处置明细（合计须等于不合格数量）；检验来源的物料 / 数量只读 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => (route.params.id === 'new' ? undefined : String(route.params.id)))
const d = ref<NcrDetail>()
const codes = ref<DefectCodeRow[]>([])
const form = ref<{
  source: string; sourceNo?: string; materialId?: string; batchNo?: string; ncrQty?: string; supplierId?: string; customerId?: string; defectDescription?: string
  defectCodes: string[]; severity: string; responsibility: string; containment?: string; capaRequired: boolean; scarRequired: boolean; fileIds: string[]
}>({ source: 'INVENTORY', defectCodes: [], severity: 'MAJOR', responsibility: 'UNKNOWN', capaRequired: false, scarRequired: false, fileIds: [] })
const disps = ref<DispositionRow[]>([])
const saving = ref(false)
const headLocked = computed(() => !!d.value?.inspectionId || d.value?.source === 'COMPLAINT')
const allowed = computed(() => allowedDispositions(form.value.source))
const dispSum = computed(() => disps.value.reduce((s, x) => s + num(x.qty), 0))
const suggested = ref(false)

onMounted(async () => {
  codes.value = await basicApi.enabledDefects()
  if (!id.value) return
  d.value = await ncrApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
  const n = d.value
  form.value = { source: n.source, sourceNo: n.sourceNo, materialId: n.materialId, batchNo: n.batchNo, ncrQty: n.ncrQty, supplierId: n.supplierId,
    customerId: n.customerId, defectDescription: n.defectDescription, defectCodes: [...n.defectCodes], severity: n.severity, responsibility: n.responsibility,
    containment: n.containment, capaRequired: n.capaRequired, scarRequired: n.scarRequired, fileIds: [] }
  disps.value = n.dispositions.map((x) => ({ disposition: x.disposition, qty: x.qty, remark: x.remark, targetMaterialId: x.targetMaterialId }))
  suggested.value = n.capaSuggested
})
/** QC-NCR-R05 预判是否需要 CAPA */
watch(() => [form.value.materialId, form.value.defectCodes.join(','), form.value.severity], async () => {
  if (!form.value.materialId) return
  suggested.value = await ncrApi.suggestCapa({ materialId: form.value.materialId, defectCodes: form.value.defectCodes, severity: form.value.severity, source: form.value.source })
  if (suggested.value) form.value.capaRequired = true
})
watch(() => form.value.responsibility, (r) => {
  form.value.scarRequired = r === 'SUPPLIER' && !!form.value.supplierId
})

async function save(submit: boolean) {
  if (!form.value.defectDescription?.trim()) return ElMessage.warning('请填写不合格描述')
  saving.value = true
  try {
    const body = { ...form.value, dispositions: disps.value.filter((x) => x.disposition && num(x.qty) > 0), version: d.value?.version }
    const newId = (await ncrApi.save(id.value, body)) || id.value!
    if (submit) {
      if (dispSum.value !== num(form.value.ncrQty)) return ElMessage.warning(`处置数量合计 ${formatQty(dispSum.value)} 必须等于不合格数量 ${formatQty(form.value.ncrQty)}`)
      await ncrApi.submit(newId)
      ElMessage.success('已提交 MRB')
    } else {
      ElMessage.success('已保存')
    }
    tabs.remove([tabKeyOf(route)])
    router.push(`/quality/ncr/${newId}`)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ErpPage :title="d ? `编辑 NCR ${d.docNo}` : '新建 NCR'" back="/quality/ncr">
    <ErpPanel title="不合格信息">
      <el-form label-width="110px" class="grid">
        <el-form-item label="来源">
          <el-select v-if="!d" v-model="form.source">
            <el-option v-for="s in ['INVENTORY', 'PRODUCTION', 'IPQC']" :key="s" :value="s" :label="NCR_SOURCE[s]" />
          </el-select>
          <span v-else>{{ NCR_SOURCE[form.source] }} {{ d.sourceNo ?? '' }}</span>
        </el-form-item>
        <el-form-item v-if="!headLocked" label="来源单号"><el-input v-model="form.sourceNo" maxlength="64" placeholder="生产订单号等" /></el-form-item>
        <el-form-item label="物料" required><MaterialSelect v-model="form.materialId" :disabled="headLocked" /></el-form-item>
        <el-form-item label="批次"><el-input v-model="form.batchNo" :disabled="headLocked" maxlength="64" /></el-form-item>
        <el-form-item label="不合格数量" required><QtyInput v-model="form.ncrQty" :disabled="headLocked && !d?.inspectionId" /></el-form-item>
        <el-form-item label="供应商"><SupplierSelect v-model="form.supplierId" :disabled="headLocked" /></el-form-item>
        <el-form-item label="客户"><CustomerSelect v-model="form.customerId" :disabled="headLocked" /></el-form-item>
        <el-form-item label="严重度" required>
          <el-radio-group v-model="form.severity"><el-radio-button v-for="o in optionsOf(SEVERITY)" :key="String(o.value)" :value="o.value">{{ o.label }}</el-radio-button></el-radio-group>
        </el-form-item>
        <el-form-item label="责任归属" required><DictSelect v-model="form.responsibility" type="qc_ncr_responsibility" :clearable="false" /></el-form-item>
        <el-form-item label="缺陷代码">
          <el-select v-model="form.defectCodes" multiple filterable>
            <el-option v-for="c in codes" :key="c.code" :value="c.code" :label="`${c.code} ${c.name}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="不合格描述" required class="wide"><el-input v-model="form.defectDescription" type="textarea" :rows="3" maxlength="2000" show-word-limit /></el-form-item>
        <el-form-item label="围堵措施" class="wide"><el-input v-model="form.containment" type="textarea" :rows="2" maxlength="1000" placeholder="如：冻结同批次库存、通知产线" /></el-form-item>
        <el-form-item label="需要 CAPA">
          <el-switch v-model="form.capaRequired" /><span v-if="suggested" class="muted hint">系统建议：同物料同缺陷重复 / 致命 / 客诉</span>
        </el-form-item>
        <el-form-item label="需要 SCAR"><el-switch v-model="form.scarRequired" /></el-form-item>
        <el-form-item label="附件" class="wide"><AttachmentUpload v-model="form.fileIds" :biz-type="id ? 'QC_NCR' : undefined" :biz-id="id" /></el-form-item>
      </el-form>
    </ErpPanel>

    <ErpPanel title="处置明细">
      <el-table :data="disps" border>
        <el-table-column label="处置方式" width="180">
          <template #default="{ row }">
            <el-select v-model="row.disposition"><el-option v-for="k in allowed" :key="k" :value="k" :label="DISPOSITION[k]" /></el-select>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="160"><template #default="{ row }"><QtyInput v-model="row.qty" /></template></el-table-column>
        <el-table-column label="降级后的物料" min-width="220">
          <template #default="{ row }">
            <MaterialSelect v-if="row.disposition === 'DOWNGRADE'" v-model="row.targetMaterialId" />
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="260"><template #default="{ row }"><el-input v-model="row.remark" maxlength="512" placeholder="如特采条件：仅用于 FG1 订单" /></template></el-table-column>
        <el-table-column label="" width="60" align="center"><template #default="{ $index }"><el-button link type="danger" @click="disps.splice($index, 1)">删除</el-button></template></el-table-column>
      </el-table>
      <div class="sum">
        <el-button @click="disps.push({ disposition: allowed[0], qty: '' })">+ 添加处置</el-button>
        <span :class="dispSum === num(form.ncrQty) ? 'ok' : 'danger'">合计 {{ formatQty(dispSum) }} / 不合格数量 {{ formatQty(form.ncrQty) }}</span>
      </div>
    </ErpPanel>

    <div class="footer">
      <el-button @click="router.back()">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存</el-button>
      <el-button v-perm="'qc:ncr:submit'" type="primary" :loading="saving" @click="save(true)">提交 MRB</el-button>
    </div>
  </ErpPage>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: var(--erp-space-6); }
.grid .wide { grid-column: span 2; }
.muted { color: var(--erp-color-text-secondary); }
.hint { margin-left: var(--erp-space-2); font-size: var(--erp-font-size-caption); }
.sum { display: flex; justify-content: space-between; align-items: center; margin-top: var(--erp-space-3); }
.ok { color: var(--erp-color-success); }
.danger { color: var(--erp-color-error); }
.footer { display: flex; justify-content: flex-end; gap: var(--erp-space-2); }
</style>
