<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { SEVERITY, complaintApi, optionsOf } from '../api/quality'

defineOptions({ name: 'QcComplaintEdit' })

/** 客诉登记 / 编辑（需求 10-05 3.2，T4）。回复期限默认收到 + 参数天数（后端计算） */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (route.params.id === 'new' ? undefined : String(route.params.id)))
const pad = (n: number) => String(n).padStart(2, '0')
const now = () => {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}
interface Form {
  customerId?: string; contactId?: string; complaintType?: string; severity: string; materialId?: string; customerPartNo?: string; orderNo?: string
  shipmentNo?: string; batchNo?: string; serialNos?: string; complaintQty?: string; description?: string; receivedAt?: string; replyDueDate?: string
  qeId?: string; fileIds: string[]
}
const form = ref<Form>({
  severity: 'MAJOR', complaintType: 'QUALITY', receivedAt: now(), qeId: me.user?.id, fileIds: []
})
const contacts = ref<{ id: string; name: string }[]>([])
const version = ref<number>()
const saving = ref(false)

onMounted(async () => {
  if (!id.value) return
  const d = await complaintApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.docNo)
  version.value = d.version
  form.value = { customerId: d.customerId, contactId: d.contactId, complaintType: d.complaintType, severity: d.severity, materialId: d.materialId,
    customerPartNo: d.customerPartNo, orderNo: d.orderNo, shipmentNo: d.shipmentNo, batchNo: d.batchNo, serialNos: d.serialNos, complaintQty: d.complaintQty,
    description: d.description, receivedAt: d.receivedAt, replyDueDate: d.replyDueDate, qeId: d.qeId, fileIds: [] }
})
watch(() => form.value.customerId, async (c) => {
  contacts.value = c ? await complaintApi.contacts(c) : []
}, { immediate: true })
async function save() {
  if (!form.value.customerId || !form.value.description || !form.value.qeId) return ElMessage.warning('请填写客户、问题描述和负责 QE')
  saving.value = true
  try {
    const newId = (await complaintApi.save(id.value, { ...form.value, version: version.value })) || id.value!
    ElMessage.success('已保存')
    tabs.remove([tabKeyOf(route)])
    router.push(`/quality/complaint/${newId}`)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ErpPage :title="id ? '编辑客诉' : '登记客诉'" back="/quality/complaint">
    <ErpPanel>
      <el-form label-width="110px" class="grid">
        <el-form-item label="客户" required><CustomerSelect v-model="form.customerId" /></el-form-item>
        <el-form-item label="联系人">
          <el-select v-model="form.contactId" clearable><el-option v-for="c in contacts" :key="c.id" :value="c.id" :label="c.name" /></el-select>
        </el-form-item>
        <el-form-item label="客诉类型" required><DictSelect v-model="form.complaintType" type="qc_complaint_type" :clearable="false" /></el-form-item>
        <el-form-item label="严重度" required>
          <el-radio-group v-model="form.severity"><el-radio-button v-for="o in optionsOf(SEVERITY)" :key="String(o.value)" :value="o.value">{{ o.label }}</el-radio-button></el-radio-group>
        </el-form-item>
        <el-form-item label="物料"><MaterialSelect v-model="form.materialId" /></el-form-item>
        <el-form-item label="客户料号"><el-input v-model="form.customerPartNo" maxlength="64" /></el-form-item>
        <el-form-item label="订单号"><el-input v-model="form.orderNo" maxlength="64" /></el-form-item>
        <el-form-item label="出货单号"><el-input v-model="form.shipmentNo" maxlength="64" /></el-form-item>
        <el-form-item label="批次"><el-input v-model="form.batchNo" maxlength="64" /></el-form-item>
        <el-form-item label="投诉数量"><QtyInput v-model="form.complaintQty" /></el-form-item>
        <el-form-item label="序列号" class="wide"><el-input v-model="form.serialNos" type="textarea" :rows="2" placeholder="多个用逗号或换行分隔" /></el-form-item>
        <el-form-item label="问题描述" required class="wide"><el-input v-model="form.description" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="收到时间" required>
          <el-date-picker v-model="form.receivedAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="回复期限"><el-date-picker v-model="form.replyDueDate" value-format="YYYY-MM-DD" placeholder="默认收到 + 参数天数" /></el-form-item>
        <el-form-item label="负责 QE" required><UserSelect v-model="form.qeId" /></el-form-item>
        <el-form-item label="附件" class="wide"><AttachmentUpload v-model="form.fileIds" :biz-type="id ? 'QC_COMPLAINT' : undefined" :biz-id="id" /></el-form-item>
      </el-form>
    </ErpPanel>
    <div class="footer">
      <el-button @click="router.back()">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </div>
  </ErpPage>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: var(--erp-space-6); }
.grid .wide { grid-column: span 2; }
.footer { display: flex; justify-content: flex-end; gap: var(--erp-space-2); }
</style>
