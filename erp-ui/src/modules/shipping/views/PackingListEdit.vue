<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { download } from '@/api/http'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatQty } from '@/utils/format'
import { docApi, type PackingListDetail } from '../api/shipping'

defineOptions({ name: 'ShpPackingListEdit' })

/** Packing List 编辑（需求 11-04 3.1，T4）：可改单号、日期、收货人、通知方、唛头、描述、料号、备注；数量、重量只读 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => String(route.params.id))
const d = ref<PackingListDetail>()
const saving = ref(false)
const canEdit = computed(() => me.hasPermission('shp:document:update') && !d.value?.invalid)

async function load() {
  d.value = await docApi.packingList(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.plNo)
}
onMounted(load)
async function save() {
  const x = d.value!
  if (!x.plNo?.trim()) return ElMessage.warning('请填写 PL No.')
  saving.value = true
  try {
    await docApi.updatePackingList(id.value, {
      plNo: x.plNo.trim(), plDate: x.plDate, consignee: x.consignee, notifyParty: x.notifyParty, shippingMarks: x.shippingMarks, remark: x.remark,
      lines: x.lines.map((l) => ({ description: l.description, partNo: l.partNo }))
    })
    ElMessage.success('已保存')
    load()
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ErpPage :title="d ? `Packing List ${d.plNo}` : 'Packing List'" back="/shipping/packing-list" sticky>
    <template #meta><ErpBadge v-if="d?.invalid" type="danger" :dot="false">已失效</ErpBadge></template>
    <template #actions>
      <PrintButton v-if="d" biz-type="SHP_PACKING_LIST" :ids="[id]" permission="shp:document:print" />
      <el-button v-if="d" v-perm="'shp:document:print'" @click="download(`/shipping/packing-lists/${id}/export`, undefined, `${d.plNo}.xlsx`)">导出 Excel</el-button>
      <el-button v-if="canEdit" type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
    <template v-if="d">
      <el-alert v-if="d.invalid" type="error" :closable="false" title="出货单已反确认或作废，本单证已失效（打印带水印）" class="gap-b" />
      <ErpPanel title="单头">
        <el-form label-width="110px" :disabled="!canEdit">
          <el-row :gutter="24">
            <el-col :span="8"><el-form-item label="PL No." required><el-input v-model="d.plNo" maxlength="32" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="Date" required><el-date-picker v-model="d.plDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item></el-col>
            <el-col :span="8">
              <el-form-item label="出货单">
                <template v-if="d.shipmentNos.length > 1"><span v-for="no in d.shipmentNos" :key="no" class="gap-r">{{ no }}</span></template>
                <el-link v-else type="primary" underline="never" @click="router.push(`/shipping/shipment/${d.shipmentId}`)">{{ d.shipmentNo }}</el-link>
                <span class="gap-l">{{ d.customerName }}</span>
              </el-form-item>
            </el-col>
            <el-col :span="12"><el-form-item label="Consignee"><el-input v-model="d.consignee" type="textarea" :rows="3" maxlength="512" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="Notify Party"><el-input v-model="d.notifyParty" type="textarea" :rows="3" maxlength="512" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="Shipping Marks"><el-input v-model="d.shippingMarks" type="textarea" :rows="3" maxlength="1000" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="Remark"><el-input v-model="d.remark" type="textarea" :rows="3" maxlength="1000" /></el-form-item></el-col>
          </el-row>
        </el-form>
      </ErpPanel>
      <ErpPanel title="明细" description="箱号区间、数量、重量来自装箱数据，不可修改">
        <el-table :data="d.lines">
          <el-table-column v-if="d.shipmentNos.length > 1" prop="shipmentNo" label="出货单" width="150" />
          <el-table-column prop="cartonRange" label="Carton No." width="100" />
          <el-table-column label="Description" min-width="200">
            <template #default="{ row }"><el-input v-model="row.description" :disabled="!canEdit" maxlength="512" /></template>
          </el-table-column>
          <el-table-column label="Part No." width="150"><template #default="{ row }"><el-input v-model="row.partNo" :disabled="!canEdit" maxlength="64" /></template></el-table-column>
          <el-table-column label="Qty/Ctn" width="90" align="right"><template #default="{ row }">{{ formatQty(row.qtyPerCarton) }}</template></el-table-column>
          <el-table-column prop="cartons" label="Ctns" width="60" align="right" />
          <el-table-column label="Qty" width="100" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
          <el-table-column prop="uom" label="Unit" width="60" />
          <el-table-column prop="netWeight" label="N.W." width="80" align="right" />
          <el-table-column prop="grossWeight" label="G.W." width="80" align="right" />
          <el-table-column prop="cbm" label="CBM" width="80" align="right" />
        </el-table>
        <div v-if="d.totals" class="totals">
          Total：{{ d.totals.cartons }} Ctns　Qty {{ formatQty(d.totals.qty) }}　N.W. {{ d.totals.netWeight }} kg　G.W. {{ d.totals.grossWeight }} kg　{{ d.totals.cbm }} m³
        </div>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
.gap-b { margin-bottom: var(--erp-section-gap); }
.gap-l { margin-left: var(--erp-space-2); }
.totals { padding-top: var(--erp-space-3); text-align: right; font-weight: var(--erp-font-weight-medium); }
.gap-r { margin-right: var(--erp-space-2); }
</style>
