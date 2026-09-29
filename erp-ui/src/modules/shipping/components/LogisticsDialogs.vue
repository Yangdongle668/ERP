<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { LOGISTICS_STATUS, optionsOf, shipmentApi, type ForwarderOption } from '../api/shipping'

/**
 * 物流登记弹窗（需求 11-03 登记物流 / 签收，11-05 更新状态）：出货单详情与物流跟踪列表共用。
 * 物流状态不可倒退（倒退需填写说明）；更新为“已签收”或登记签收时出货单完成。
 */
defineProps<{ forwarders: ForwarderOption[] }>()
const emit = defineEmits<{ changed: [] }>()

interface Target {
  id: string; docNo: string; blNo?: string; blDate?: string; etd?: string; eta?: string; forwarderId?: string; containerNo?: string; sealNo?: string
  logisticsStatus?: string
}
const target = ref<Target>()
const now = () => {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:00`
}

// 登记物流
const lgDialog = ref(false)
const lg = ref<{ blNo?: string; blDate?: string; etd?: string; eta?: string; forwarderId?: string; containerNo?: string; sealNo?: string }>({})
function openLogistics(t: Target) {
  target.value = t
  lg.value = { blNo: t.blNo, blDate: t.blDate, etd: t.etd, eta: t.eta, forwarderId: t.forwarderId, containerNo: t.containerNo, sealNo: t.sealNo }
  lgDialog.value = true
}
async function saveLogistics() {
  await shipmentApi.saveLogistics(target.value!.id, { ...lg.value, blNo: lg.value.blNo?.trim() || undefined })
  lgDialog.value = false
  ElMessage.success('已登记')
  emit('changed')
}

// 更新物流状态
const evDialog = ref(false)
const ev = ref<{ logisticsStatus?: string; occurredAt?: string; location?: string; remark?: string }>({})
function openEvent(t: Target) {
  target.value = t
  ev.value = { occurredAt: now() }
  evDialog.value = true
}
const order = Object.keys(LOGISTICS_STATUS)
const backward = () => !!target.value?.logisticsStatus && !!ev.value.logisticsStatus
  && order.indexOf(ev.value.logisticsStatus) < order.indexOf(target.value.logisticsStatus)
async function saveEvent() {
  if (!ev.value.logisticsStatus || !ev.value.occurredAt) return ElMessage.warning('请选择状态和时间')
  if (backward() && !ev.value.remark?.trim()) return ElMessage.warning('物流状态不能倒退，如需更正请填写说明')
  await shipmentApi.addEvent(target.value!.id, ev.value)
  evDialog.value = false
  ElMessage.success(ev.value.logisticsStatus === 'DELIVERED' ? '已签收，出货单已完成' : '已更新')
  emit('changed')
}

// 登记签收
const signDialog = ref(false)
const sign = ref<{ signedAt?: string; signedBy?: string; remark?: string; fileIds: string[] }>({ fileIds: [] })
function openSign(t: Target) {
  target.value = t
  sign.value = { signedAt: now(), fileIds: [] }
  signDialog.value = true
}
async function saveSign() {
  if (!sign.value.signedAt) return ElMessage.warning('请选择签收时间')
  await shipmentApi.sign(target.value!.id, sign.value)
  signDialog.value = false
  ElMessage.success('已登记签收')
  emit('changed')
}
defineExpose({ openLogistics, openEvent, openSign })
</script>

<template>
  <el-dialog v-model="lgDialog" :title="`登记物流 ${target?.docNo ?? ''}`" width="520px">
    <el-form label-width="90px">
      <el-form-item label="提单号"><el-input v-model="lg.blNo" maxlength="64" placeholder="提单号 / 运单号 / 快递单号" /></el-form-item>
      <el-form-item label="提单日期"><el-date-picker v-model="lg.blDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
      <el-form-item label="ETD"><el-date-picker v-model="lg.etd" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
      <el-form-item label="ETA"><el-date-picker v-model="lg.eta" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
      <el-form-item label="货代">
        <el-select v-model="lg.forwarderId" clearable filterable class="w-full">
          <el-option v-for="f in forwarders" :key="f.id" :value="f.id" :label="`${f.code} ${f.name}`" />
        </el-select>
      </el-form-item>
      <el-form-item label="柜号"><el-input v-model="lg.containerNo" maxlength="32" /></el-form-item>
      <el-form-item label="封条号"><el-input v-model="lg.sealNo" maxlength="32" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="lgDialog = false">取消</el-button>
      <el-button type="primary" @click="saveLogistics">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="evDialog" :title="`更新物流状态 ${target?.docNo ?? ''}`" width="480px">
    <el-form label-width="80px">
      <el-form-item label="状态" required>
        <el-select v-model="ev.logisticsStatus" class="w-full">
          <el-option v-for="o in optionsOf(LOGISTICS_STATUS)" :key="String(o.value)" :value="o.value" :label="o.label" />
        </el-select>
      </el-form-item>
      <el-form-item label="时间" required>
        <el-date-picker v-model="ev.occurredAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="w-full" />
      </el-form-item>
      <el-form-item label="地点"><el-input v-model="ev.location" maxlength="128" /></el-form-item>
      <el-form-item label="备注" :required="backward()">
        <el-input v-model="ev.remark" type="textarea" :rows="2" maxlength="256" :placeholder="backward() ? '状态倒退，请说明原因' : ''" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="evDialog = false">取消</el-button>
      <el-button type="primary" @click="saveEvent">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="signDialog" :title="`登记签收 ${target?.docNo ?? ''}`" width="480px">
    <el-form label-width="80px">
      <el-form-item label="签收时间" required>
        <el-date-picker v-model="sign.signedAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="w-full" />
      </el-form-item>
      <el-form-item label="签收人"><el-input v-model="sign.signedBy" maxlength="64" /></el-form-item>
      <el-form-item label="备注"><el-input v-model="sign.remark" maxlength="256" /></el-form-item>
      <el-form-item label="签收单"><AttachmentUpload v-model="sign.fileIds" biz-type="SHP_SHIPMENT" multiple /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="signDialog = false">取消</el-button>
      <el-button type="primary" @click="saveSign">保存</el-button>
    </template>
  </el-dialog>
</template>
