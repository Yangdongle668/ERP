<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatAmount, today } from '@/utils/format'
import { RECORD_TYPE_OPTIONS, toolingApi, TOOLING_STATUS, type RecordAction, type RecordReq, type RecordRow, type ToolingRow } from '../api/tooling'
import { labelOf } from '../api/material'

defineOptions({ name: 'EngToolingDetail' })

/** 工装详情（需求 05-08 3.2，T5）：借出、归还、保养、送修、修复、报废、次数调整与履历 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<ToolingRow>()
const records = ref<RecordRow[]>([])

async function load() {
  d.value = await toolingApi.get(id.value)
  records.value = await toolingApi.records(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.code)
}
const s = computed(() => d.value?.toolingStatus)

// ---------- 登记弹窗 ----------
interface Spec { title: string; user?: string; content?: string; contentRequired?: boolean; vendor?: boolean; cost?: boolean; date?: boolean; expected?: boolean; count?: boolean }
const SPECS: Record<RecordAction, Spec> = {
  lend: { title: '借出', user: '借用人', content: '用途', expected: true },
  return: { title: '归还', user: '归还人', content: '说明' },
  maintain: { title: '保养', user: '经办人', content: '保养内容', contentRequired: true, cost: true, date: true },
  'repair-start': { title: '送修', user: '经办人', content: '维修内容', contentRequired: true, vendor: true, cost: true },
  'repair-end': { title: '修复', user: '经办人', content: '维修内容', contentRequired: true, vendor: true, cost: true },
  scrap: { title: '报废', user: '经办人', content: '报废原因', contentRequired: true, date: true },
  adjust: { title: '次数调整', content: '调整原因', contentRequired: true, count: true }
}
const visible = ref(false)
const action = ref<RecordAction>('lend')
const spec = computed(() => SPECS[action.value])
const req = ref<RecordReq>({})
function open(a: RecordAction) {
  action.value = a
  req.value = { date: today(), count: a === 'adjust' ? d.value?.usedCount : undefined }
  visible.value = true
}
async function submit() {
  if (action.value === 'lend' && !req.value.userId) return ElMessage.warning('请选择借用人')
  if (spec.value.contentRequired && !req.value.content?.trim()) return ElMessage.warning(`请填写${spec.value.content}`)
  if (action.value === 'adjust' && (req.value.count === undefined || req.value.count === null)) return ElMessage.warning('请填写调整后次数')
  await toolingApi.record(id.value, action.value, req.value)
  ElMessage.success(`已登记${spec.value.title}`)
  visible.value = false
  load()
}

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'eng:tooling:delete', visible: () => s.value === 'IN_STOCK', confirm: `确定删除 ${d.value?.code} 吗？`,
    handler: async () => {
      await toolingApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/engineering/tooling')
    } },
  { key: 'scrap', label: '报废', permission: 'eng:tooling:record', visible: () => s.value === 'IN_STOCK' || s.value === 'REPAIRING', handler: () => open('scrap') },
  { key: 'adjust', label: '次数调整', permission: 'eng:tooling:record', visible: () => s.value !== 'SCRAPPED', handler: () => open('adjust') },
  { key: 'repairStart', label: '送修', permission: 'eng:tooling:record', visible: () => s.value === 'IN_STOCK', handler: () => open('repair-start') },
  { key: 'repairEnd', label: '修复', type: 'primary', permission: 'eng:tooling:record', visible: () => s.value === 'REPAIRING', handler: () => open('repair-end') },
  { key: 'maintain', label: '保养', permission: 'eng:tooling:record', visible: () => s.value === 'IN_STOCK', handler: () => open('maintain') },
  { key: 'return', label: '归还', type: 'primary', permission: 'eng:tooling:record', visible: () => s.value === 'LENT' || s.value === 'IN_USE', handler: () => open('return') },
  { key: 'lend', label: '借出', type: 'primary', permission: 'eng:tooling:record', visible: () => s.value === 'IN_STOCK', handler: () => open('lend') }
])

const pct = computed(() => (d.value?.lifePct ? Math.min(100, Math.round(Number(d.value.lifePct) * 100)) : 0))
const pctStatus = computed(() => (pct.value >= 100 ? 'exception' : d.value?.lifeWarn ? 'warning' : undefined))
onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d ? `${d.code} ${d.name}` : '工装'" :status="d?.toolingStatus" :status-map="TOOLING_STATUS" :actions="actions"
                     @back="router.push('/engineering/tooling')" />
    </template>
    <template v-if="d">
      <ErpPanel>
        <el-descriptions :column="3">
          <el-descriptions-item label="类型"><DictTag type="eng_tooling_type" :value="d.toolingType" /></el-descriptions-item>
          <el-descriptions-item label="规格">{{ d.spec || '-' }}</el-descriptions-item>
          <el-descriptions-item label="归属">{{ d.ownership === 'CUSTOMER' ? `客户资产：${d.customerName ?? d.customerId}` : '自有' }}</el-descriptions-item>
          <el-descriptions-item label="模穴数">{{ d.cavity }}</el-descriptions-item>
          <el-descriptions-item label="使用次数">{{ d.usedCount }} / {{ d.designLife ?? '不限' }}</el-descriptions-item>
          <el-descriptions-item label="寿命使用率">
            <el-progress v-if="d.designLife" :percentage="pct" :status="pctStatus" :stroke-width="8" class="progress" />
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="保养周期">{{ d.maintainCycle ?? '-' }}（上次保养 {{ d.lastMaintainCount }} 次）</el-descriptions-item>
          <el-descriptions-item label="距下次保养">{{ d.toMaintain ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="领用人">{{ d.holderName ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="存放位置">{{ d.location || '-' }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ d.supplierName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="购置">{{ d.purchaseDate ?? '-' }} {{ d.purchaseAmount ? formatAmount(d.purchaseAmount) : '' }}</el-descriptions-item>
          <el-descriptions-item label="适用物料" :span="3">
            <template v-if="d.materials.length">
              <el-link v-for="m in d.materials" :key="m.id" type="primary" underline="never" class="mat" @click="router.push(`/engineering/material/${m.id}`)">{{ m.code }} {{ m.name }}</el-link>
            </template>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="d.allowOverLife" label="超寿命使用" :span="3">允许：{{ d.overLifeReason }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>
      <ErpPanel title="履历" flush>
        <el-table :data="records">
          <el-table-column label="时间" width="160"><template #default="{ row }">{{ row.occurredAt?.slice(0, 16) }}</template></el-table-column>
          <el-table-column label="类型" width="90"><template #default="{ row }">{{ labelOf(RECORD_TYPE_OPTIONS, row.recordType) }}</template></el-table-column>
          <el-table-column prop="count" label="次数" width="80" align="right" />
          <el-table-column prop="userName" label="人员" width="100" />
          <el-table-column prop="sourceDocNo" label="来源单据" width="150" />
          <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
          <el-table-column label="费用" width="100" align="right"><template #default="{ row }"><span class="num">{{ row.cost ? formatAmount(row.cost) : '' }}</span></template></el-table-column>
          <el-table-column prop="createdByName" label="登记人" width="100" />
          <template #empty><ErpEmpty compact description="没有履历" /></template>
        </el-table>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="visible" :title="`登记${spec.title}`" width="520px" append-to-body>
      <el-form label-width="100px">
        <el-form-item v-if="spec.count" label="调整后次数" required><el-input-number v-model="req.count" :min="0" :precision="0" /></el-form-item>
        <el-form-item v-if="spec.user" :label="spec.user" :required="action === 'lend'"><UserSelect v-model="req.userId" :placeholder="action === 'lend' ? '' : '默认为当前用户'" /></el-form-item>
        <el-form-item v-if="spec.expected" label="预计归还"><el-date-picker v-model="req.expectedReturn" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item v-if="spec.date" label="日期"><el-date-picker v-model="req.date" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item v-if="spec.vendor" label="维修厂商"><el-input v-model="req.vendor" maxlength="128" /></el-form-item>
        <el-form-item v-if="spec.cost" label="费用"><AmountInput v-model="req.cost" /></el-form-item>
        <el-form-item :label="spec.content" :required="spec.contentRequired"><el-input v-model="req.content" type="textarea" :rows="2" maxlength="512" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.progress { width: 200px; }
.mat { margin-right: var(--erp-space-3); }
</style>
