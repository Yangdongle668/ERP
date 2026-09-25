<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { LineColumn, Option } from '@/components'
import type { MaterialBrief } from '@/api/refs'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { today } from '@/utils/format'
import { submitText } from '../api/common'
import { REQ_STATUS, requisitionApi, type ReqDetail, type ReqLine, type ReqSave } from '../api/requisition'
import { supplierApi } from '../api/supplier'

defineOptions({ name: 'PurRequisitionEdit' })

/** 采购申请编辑（需求 07-03 3.2，T4） */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const linesRef = ref<{ validate: () => boolean; validRows: () => ReqLine[] }>()
const saving = ref(false)
const detail = ref<ReqDetail>()

interface Form {
  requisitionType?: string
  requestDeptId?: string
  urgent: boolean
  remark?: string
  fileIds: string[]
  lines: ReqLine[]
}
const newLine = (): ReqLine => ({})
const form = ref<Form>({ requisitionType: 'MANUAL', urgent: false, fileIds: [], lines: [newLine()] })
const guard = useLeaveGuard(() => form.value)

const rules: FormRules = {
  requisitionType: [{ required: true, message: '请选择申请类型', trigger: 'change' }],
  requestDeptId: [{ required: true, message: '请选择申请部门', trigger: 'change' }]
}

/** 物料的可供供应商（建议供应商下拉） */
const supplierOptions = ref<Record<string, Option[]>>({})
async function loadSuppliers(materialId?: string) {
  if (!materialId || supplierOptions.value[materialId]) return
  const list = await supplierApi.suppliersOfMaterial(materialId).catch(() => [])
  supplierOptions.value[materialId] = list.filter((s) => s.supplyStatus !== 'DISABLED')
    .map((s) => ({ value: s.supplierId!, label: `${s.supplierCode} ${s.supplierName}${s.isDefault ? '（默认）' : ''}` }))
}

const columns = computed<LineColumn<ReqLine>[]>(() => [
  { prop: 'materialCode', label: '物料编码', type: 'material', width: 150, required: true },
  { prop: 'materialName', label: '名称', type: 'readonly', width: 160 },
  { prop: 'materialSpec', label: '规格', type: 'readonly', width: 160 },
  { prop: 'uom', label: '单位', type: 'readonly', width: 60 },
  { prop: 'qty', label: '数量', type: 'qty', width: 110, required: true, uomProp: 'uom', validate: (v) => (Number(v) > 0 ? undefined : '数量必须大于 0') },
  { prop: 'requiredDate', label: '需求日期', type: 'date', width: 140, required: true, validate: (v) => (String(v) < today() ? '需求日期不能早于今天' : undefined) },
  { prop: 'suggestedSupplierId', label: '建议供应商', type: 'slot', width: 220 },
  { prop: 'purpose', label: '用途', type: 'text', width: 140 },
  { prop: 'remark', label: '备注', type: 'text', width: 140 }
])

async function onMaterial(row: ReqLine, m: MaterialBrief | undefined) {
  row.materialName = m?.name
  row.materialSpec = m?.spec
  row.uom = m?.baseUom
  row.baseUom = m?.baseUom
  row.suggestedSupplierId = undefined
  if (m) {
    await loadSuppliers(m.id)
    row.suggestedSupplierId = supplierOptions.value[m.id]?.find((o) => o.label.endsWith('（默认）'))?.value as string | undefined
  }
}

onMounted(async () => {
  if (id.value) {
    const d = await requisitionApi.get(id.value)
    if (d.status !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的申请可以修改')
      router.replace(`/purchase/requisition/${id.value}`)
      return
    }
    detail.value = d
    form.value = { requisitionType: d.requisitionType, requestDeptId: d.requestDeptId, urgent: d.urgent, remark: d.remark, fileIds: [],
      lines: [...d.lines.map((l) => ({ ...l })), newLine()] }
    await Promise.all(d.lines.map((l) => loadSuppliers(l.materialId)))
    tabs.setTitle(tabKeyOf(route), `编辑采购申请 ${d.docNo}`)
  } else {
    form.value.requestDeptId = me.user?.deptId ?? undefined
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
  router.push(id.value ? `/purchase/requisition/${id.value}` : '/purchase/requisition')
}

function payload(): ReqSave {
  const f = form.value
  return {
    requisitionType: f.requisitionType, requestDeptId: f.requestDeptId, urgent: f.urgent, remark: f.remark?.trim() || undefined, fileIds: f.fileIds,
    version: detail.value?.version,
    lines: linesRef.value!.validRows().map((l) => ({
      materialId: l.materialId!, uom: l.uom, qty: l.qty!, requiredDate: l.requiredDate!, suggestedSupplierId: l.suggestedSupplierId,
      purpose: l.purpose?.trim() || undefined, remark: l.remark?.trim() || undefined
    }))
  }
}

async function save(submit: boolean) {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!linesRef.value?.validate()) return
  saving.value = true
  try {
    const data = payload()
    let rid = id.value
    if (rid) await requisitionApi.update(rid, data)
    else rid = await requisitionApi.create(data)
    guard.markClean()
    if (submit) {
      try {
        ElMessage.success(submitText((await requisitionApi.submit(rid)).status))
      } catch {
        if (!id.value) router.replace(`/purchase/requisition/${rid}/edit`)
        return
      }
    } else {
      ElMessage.success('保存成功')
    }
    tabs.remove([tabKeyOf(route)])
    router.push(`/purchase/requisition/${rid}`)
  } finally {
    saving.value = false
  }
}

const title = computed(() => (detail.value ? `编辑采购申请 ${detail.value.docNo}` : '新建采购申请'))
const asLine = (r: unknown) => r as ReqLine
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.status" :map="REQ_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
      <el-button v-if="me.hasPermission('pur:requisition:submit')" type="primary" :loading="saving" @click="save(true)">提交</el-button>
    </template>

    <ErpPanel title="基本信息">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12">
            <el-form-item label="申请类型" prop="requisitionType"><DictSelect v-model="form.requisitionType" type="pur_requisition_type" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="申请部门" prop="requestDeptId"><OrgTreeSelect v-model="form.requestDeptId" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="紧急"><el-switch v-model="form.urgent" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" maxlength="1000" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </ErpPanel>

    <ErpPanel title="明细">
      <LinesEditor ref="linesRef" v-model="form.lines" :columns="columns" :new-row="newLine" :on-material="onMaterial">
        <template #cell-suggestedSupplierId="{ row, disabled }">
          <el-select v-model="asLine(row).suggestedSupplierId" :disabled="disabled || !asLine(row).materialId" clearable placeholder="可供供应商" class="w-full">
            <el-option v-for="o in supplierOptions[asLine(row).materialId ?? ''] ?? []" :key="String(o.value)" :value="o.value" :label="o.label" />
          </el-select>
        </template>
      </LinesEditor>
    </ErpPanel>

    <ErpPanel title="附件">
      <AttachmentUpload v-if="!id" v-model="form.fileIds" biz-type="PUR_REQUISITION" multiple />
      <AttachmentPanel v-else biz-type="PUR_REQUISITION" :biz-id="id" editable />
    </ErpPanel>
  </ErpPage>
</template>
