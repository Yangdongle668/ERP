<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { download } from '@/api/http'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatAmount, formatQty } from '@/utils/format'
import { docApi, type CustomsDetail } from '../api/shipping'

defineOptions({ name: 'ShpCustomsEdit' })

/** 报关资料（需求 11-04 3.2，T4）：按 HS 编码合并；编辑申报品名、申报要素；导出 Excel；报关后回填报关单号、日期、报关单附件 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => String(route.params.id))
const d = ref<CustomsDetail>()
const saving = ref(false)
const canEdit = computed(() => me.hasPermission('shp:document:update') && !d.value?.invalid)

async function load() {
  d.value = await docApi.customs(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docCode)
}
onMounted(load)
async function save() {
  const x = d.value!
  const bad = x.items.find((i) => !i.hsCode?.trim() || !i.declareName?.trim())
  if (bad) return ElMessage.warning(`项号 ${bad.seq} 请填写商品编号和申报品名`)
  saving.value = true
  try {
    await docApi.updateCustoms(id.value, {
      customsNo: x.customsNo?.trim() || undefined, declareDate: x.declareDate, tradeMode: x.tradeMode, declarePort: x.declarePort,
      destinationCountry: x.destinationCountry, remark: x.remark,
      items: x.items.map((i) => ({ id: i.id, hsCode: i.hsCode?.trim(), declareName: i.declareName, declareElements: i.declareElements, uom: i.uom,
        secondQty: i.secondQty || undefined, secondUom: i.secondUom, origin: i.origin }))
    })
    ElMessage.success('已保存')
    load()
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ErpPage :title="d ? `报关资料 ${d.docCode}` : '报关资料'" back="/shipping/customs" sticky>
    <template #meta><ErpBadge v-if="d?.invalid" type="danger" :dot="false">已失效</ErpBadge></template>
    <template #actions>
      <el-button v-if="d" v-perm="'shp:document:print'" @click="download(`/shipping/customs/${id}/export`, undefined, `${d.docCode}.xlsx`)">导出 Excel</el-button>
      <el-button v-if="canEdit" type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
    <template v-if="d">
      <el-alert v-if="d.invalid" type="error" :closable="false" title="出货单已反确认或作废，本报关资料已失效" class="gap-b" />
      <ErpPanel title="单头">
        <el-form label-width="100px" :disabled="!canEdit">
          <el-row :gutter="24">
            <el-col :span="8">
              <el-form-item label="出货单">
                <el-link type="primary" underline="never" @click="router.push(`/shipping/shipment/${d.shipmentId}`)">{{ d.shipmentNo }}</el-link>
                <span class="gap-l">{{ d.customerName }}</span>
              </el-form-item>
            </el-col>
            <el-col :span="8"><el-form-item label="报关单号"><el-input v-model="d.customsNo" maxlength="32" placeholder="报关后回填" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="申报日期"><el-date-picker v-model="d.declareDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="贸易方式"><el-input v-model="d.tradeMode" maxlength="32" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="申报口岸"><el-input v-model="d.declarePort" maxlength="64" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="运抵国"><el-input v-model="d.destinationCountry" maxlength="64" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="币制">{{ d.currency }}　总价 {{ formatAmount(d.totalAmount) }}</el-form-item></el-col>
            <el-col :span="16"><el-form-item label="备注"><el-input v-model="d.remark" maxlength="1000" /></el-form-item></el-col>
          </el-row>
        </el-form>
      </ErpPanel>
      <ErpPanel title="商品" description="同 HS 编码的出货行合并为一项；申报要素按“品牌类型|出口享惠情况|用途|…”填写">
        <el-table :data="d.items">
          <el-table-column prop="seq" label="项号" width="60" />
          <el-table-column label="商品编号" width="140"><template #default="{ row }"><el-input v-model="row.hsCode" :disabled="!canEdit" maxlength="16" /></template></el-table-column>
          <el-table-column label="申报品名" width="160"><template #default="{ row }"><el-input v-model="row.declareName" :disabled="!canEdit" maxlength="128" /></template></el-table-column>
          <el-table-column label="申报要素" min-width="220">
            <template #default="{ row }"><el-input v-model="row.declareElements" :disabled="!canEdit" maxlength="512" /></template>
          </el-table-column>
          <el-table-column label="数量" width="120" align="right"><template #default="{ row }">{{ formatQty(row.qty) }} {{ row.uom }}</template></el-table-column>
          <el-table-column label="第二数量" width="110"><template #default="{ row }"><el-input v-model="row.secondQty" :disabled="!canEdit" /></template></el-table-column>
          <el-table-column label="第二单位" width="90"><template #default="{ row }"><el-input v-model="row.secondUom" :disabled="!canEdit" maxlength="16" /></template></el-table-column>
          <el-table-column label="单价" width="100" align="right"><template #default="{ row }">{{ row.unitPrice }}</template></el-table-column>
          <el-table-column label="总价" width="110" align="right"><template #default="{ row }">{{ formatAmount(row.amount) }}</template></el-table-column>
          <el-table-column label="原产国" width="90"><template #default="{ row }"><el-input v-model="row.origin" :disabled="!canEdit" maxlength="32" /></template></el-table-column>
          <el-table-column prop="netWeight" label="净重" width="80" align="right" />
          <el-table-column prop="grossWeight" label="毛重" width="80" align="right" />
        </el-table>
      </ErpPanel>
      <ErpPanel title="附件（报关单 PDF）"><AttachmentPanel biz-type="SHP_CUSTOMS" :biz-id="id" :editable="canEdit" /></ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
.gap-b { margin-bottom: var(--erp-section-gap); }
.gap-l { margin-left: var(--erp-space-2); }
</style>
