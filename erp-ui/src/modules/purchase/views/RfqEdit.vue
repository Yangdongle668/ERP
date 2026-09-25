<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { LineColumn } from '@/components'
import type { MaterialBrief, SupplierBrief } from '@/api/refs'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { today } from '@/utils/format'
import { RFQ_STATUS, rfqApi, type RfqDetail, type RfqLine, type RfqSave } from '../api/rfq'

defineOptions({ name: 'PurRfqEdit' })

/** 询价单编辑（需求 07-04 3.2，T4）：询价物料 + 询价供应商 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const linesRef = ref<{ validate: () => boolean; validRows: () => RfqLine[] }>()
const saving = ref(false)
const detail = ref<RfqDetail>()

interface SupplierPick {
  supplierId?: string
  code?: string
  name?: string
}
interface Form {
  title?: string
  currency?: string
  quoteDeadline?: string
  remark?: string
  fileIds: string[]
  lines: RfqLine[]
  suppliers: SupplierPick[]
}
const newLine = (): RfqLine => ({})
const form = ref<Form>({ currency: 'CNY', fileIds: [], lines: [newLine()], suppliers: [] })
const guard = useLeaveGuard(() => form.value)

const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  currency: [{ required: true, message: '请选择币别', trigger: 'change' }],
  quoteDeadline: [{ required: true, message: '请选择截止日期', trigger: 'change' }, {
    validator: (_r, v, cb) => (!v || v >= today() ? cb() : cb(new Error('截止日期不能早于今天'))), trigger: 'change'
  }]
}

const columns: LineColumn<RfqLine>[] = [
  { prop: 'materialCode', label: '物料编码', type: 'material', width: 150, required: true },
  { prop: 'materialName', label: '名称', type: 'readonly', width: 180 },
  { prop: 'materialSpec', label: '规格', type: 'readonly', width: 180 },
  { prop: 'baseUom', label: '单位', type: 'readonly', width: 60 },
  { prop: 'qty', label: '数量', type: 'qty', width: 120, required: true, uomProp: 'baseUom' },
  { prop: 'requiredDate', label: '需求日期', type: 'date', width: 140 },
  { prop: 'remark', label: '备注', type: 'text', width: 160 }
]

/** 选物料后带出这些物料的可供供应商（去重追加） */
async function onMaterial(row: RfqLine, m: MaterialBrief | undefined) {
  row.materialName = m?.name
  row.materialSpec = m?.spec
  row.baseUom = m?.baseUom
  if (!m) return
  const list = await rfqApi.defaultSuppliers([m.id]).catch(() => [])
  const exist = new Set(form.value.suppliers.map((s) => s.supplierId))
  for (const s of list) {
    if (s.supplierId && !exist.has(s.supplierId) && s.supplierStatus === 'QUALIFIED') {
      form.value.suppliers.push({ supplierId: s.supplierId, code: s.supplierCode, name: s.supplierName })
      exist.add(s.supplierId)
    }
  }
}

const pickSupplier = ref<string>()
function addSupplier(s?: SupplierBrief | SupplierBrief[]) {
  const x = Array.isArray(s) ? s[0] : s
  if (x && !form.value.suppliers.some((p) => p.supplierId === x.id)) form.value.suppliers.push({ supplierId: x.id, code: x.code, name: x.shortName || x.name })
  pickSupplier.value = undefined
}

onMounted(async () => {
  if (id.value) {
    const d = await rfqApi.get(id.value)
    if (d.status !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的询价单可以修改')
      router.replace(`/purchase/rfq/${id.value}`)
      return
    }
    detail.value = d
    form.value = {
      title: d.title, currency: d.currency, quoteDeadline: d.quoteDeadline, remark: d.remark, fileIds: [], lines: [...d.lines.map((l) => ({ ...l })), newLine()],
      suppliers: d.suppliers.map((s) => ({ supplierId: s.supplierId, code: s.supplierCode, name: s.supplierName }))
    }
    tabs.setTitle(tabKeyOf(route), `编辑询价单 ${d.docNo}`)
  }
  guard.markClean()
  window.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))

function onKey(e: KeyboardEvent) {
  if (!(e.ctrlKey || e.metaKey)) return
  if (e.key === 's') {
    e.preventDefault()
    save(false)
  } else if (e.key === 'Enter') {
    e.preventDefault()
    save(true)
  }
}

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/purchase/rfq/${id.value}` : '/purchase/rfq')
}

function payload(): RfqSave {
  const f = form.value
  return {
    title: f.title!.trim(), currency: f.currency, quoteDeadline: f.quoteDeadline!, remark: f.remark?.trim() || undefined, fileIds: f.fileIds,
    version: detail.value?.version, supplierIds: f.suppliers.map((s) => s.supplierId!),
    lines: linesRef.value!.validRows().map((l) => ({ materialId: l.materialId!, qty: l.qty, requiredDate: l.requiredDate, remark: l.remark?.trim() || undefined }))
  }
}

/** send = true：保存并发出询价 */
async function save(send: boolean) {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!linesRef.value?.validate()) return
  if (send && !form.value.suppliers.length) return ElMessage.warning('请至少选择 1 家供应商')
  saving.value = true
  try {
    const data = payload()
    let rid = id.value
    if (rid) await rfqApi.update(rid, data)
    else rid = await rfqApi.create(data)
    guard.markClean()
    if (send) {
      try {
        await rfqApi.send(rid)
        ElMessage.success('已发出询价')
      } catch {
        if (!id.value) router.replace(`/purchase/rfq/${rid}/edit`)
        return
      }
    } else {
      ElMessage.success('保存成功')
    }
    tabs.remove([tabKeyOf(route)])
    router.push(`/purchase/rfq/${rid}`)
  } finally {
    saving.value = false
  }
}

const title = computed(() => (detail.value ? `编辑询价单 ${detail.value.docNo}` : '新建询价单'))
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.status" :map="RFQ_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存</el-button>
      <el-button v-if="me.hasPermission('pur:rfq:update')" type="primary" :loading="saving" @click="save(true)">发出询价</el-button>
    </template>

    <ErpPanel title="基本信息">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12"><el-form-item label="标题" prop="title"><el-input v-model="form.title" maxlength="128" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="币别" prop="currency"><CurrencySelect v-model="form.currency" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="截止日期" prop="quoteDeadline"><el-date-picker v-model="form.quoteDeadline" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" maxlength="1000" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </ErpPanel>

    <ErpPanel title="询价物料">
      <LinesEditor ref="linesRef" v-model="form.lines" :columns="columns" :new-row="newLine" :on-material="onMaterial" />
    </ErpPanel>

    <ErpPanel title="询价供应商">
      <template #extra>
        <SupplierSelect v-model="pickSupplier" :statuses="['QUALIFIED', 'POTENTIAL']" placeholder="添加供应商" class="w260" @select="addSupplier" />
      </template>
      <el-table :data="form.suppliers">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="code" label="编码" width="140" />
        <el-table-column prop="name" label="供应商" min-width="200" />
        <el-table-column width="56" align="center"><template #default="{ $index }"><el-button link type="danger" icon="Delete" @click="form.suppliers.splice($index, 1)" /></template></el-table-column>
        <template #empty><ErpEmpty compact description="选择物料后自动带出可供供应商，也可手工添加" /></template>
      </el-table>
    </ErpPanel>

    <ErpPanel title="附件（图纸、规格书）">
      <AttachmentUpload v-if="!id" v-model="form.fileIds" biz-type="PUR_RFQ" multiple />
      <AttachmentPanel v-else biz-type="PUR_RFQ" :biz-id="id" editable />
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.w260 { width: 260px; }
</style>
