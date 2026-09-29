<script setup lang="ts">
import { computed, onActivated, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { formatQty, today } from '@/utils/format'
import { demandApi, num, type KitResult, type ReplyRow } from '../api/pmc'

defineOptions({ name: 'PmcDeliveryReply' })

/** 交期回复（需求 06-01 4.2）：待回复的销售订单行，按建议交期或手工填写承诺交期后写回销售订单 */
const router = useRouter()
interface Row extends ReplyRow { promised?: string; remark?: string }
const rows = ref<Row[]>([])
const selected = ref<Row[]>([])
const loading = ref(false)
const q = ref<{ customerId?: string; materialId?: string }>({})
const batchDate = ref<string>()
const saving = ref(false)

async function load() {
  loading.value = true
  try {
    rows.value = (await demandApi.pending(q.value)).map((r) => ({ ...r, promised: r.suggestedDate }))
  } finally {
    loading.value = false
  }
}
onMounted(load)
onActivated(load)

function applyBatch() {
  if (!batchDate.value) return ElMessage.warning('请选择日期')
  if (!selected.value.length) return ElMessage.warning('请勾选订单行')
  selected.value.forEach((r) => (r.promised = batchDate.value))
}
function useSuggested() {
  const list = selected.value.length ? selected.value : rows.value
  list.forEach((r) => (r.promised = r.suggestedDate))
}
async function save() {
  const list = selected.value.filter((r) => r.promised)
  if (!list.length) return ElMessage.warning('请勾选订单行并填写承诺交期')
  if (list.some((r) => r.promised! < today())) return ElMessage.warning('承诺交期不能早于今天')
  saving.value = true
  try {
    const n = await demandApi.reply(list.map((r) => ({ demandId: r.demandId, promisedDate: r.promised!, remark: r.remark?.trim() || undefined })))
    ElMessage.success(`已回复 ${n} 行`)
    load()
  } finally {
    saving.value = false
  }
}
const lateCount = computed(() => rows.value.filter((r) => r.promised && r.promised > r.customerDate).length)

// ---------- 齐套分析 ----------
const kitVisible = ref(false)
const kit = ref<KitResult>()
const kitTitle = ref('')
async function analyze(row: unknown) {
  const r = row as Row
  kit.value = await demandApi.kit(r.materialId, r.openQty)
  kitTitle.value = `物料齐套 - ${r.materialCode} × ${formatQty(r.openQty)}`
  kitVisible.value = true
}
const disabledDate = (d: Date) => d.getTime() < new Date(today()).getTime() - 8 * 3600 * 1000
</script>

<template>
  <ErpPage description="回复已审核销售订单行的承诺交期：建议交期 = 库存可满足时明天，否则最晚齐套日期 + 生产提前期 + 1 天">
    <ErpPanel>
      <template #filter>
        <el-form inline @submit.prevent>
          <el-form-item label="客户"><CustomerSelect v-model="q.customerId" /></el-form-item>
          <el-form-item label="物料"><MaterialSelect v-model="q.materialId" /></el-form-item>
          <el-form-item><el-button type="primary" :loading="loading" @click="load">查询</el-button></el-form-item>
        </el-form>
      </template>
      <div class="bar">
        <el-date-picker v-model="batchDate" value-format="YYYY-MM-DD" placeholder="批量设置承诺交期" :disabled-date="disabledDate" />
        <el-button @click="applyBatch">应用到勾选行</el-button>
        <el-button @click="useSuggested">按建议回复</el-button>
        <el-button v-perm="'pmc:delivery:reply'" type="primary" :loading="saving" @click="save">保存回复</el-button>
        <span v-if="lateCount" class="hint">{{ lateCount }} 行承诺交期晚于要求交期，销售订单将标记交期风险</span>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="demandId" max-height="640" @selection-change="(v: Row[]) => (selected = v)">
        <el-table-column type="selection" width="44" />
        <el-table-column label="订单" width="170">
          <template #default="{ row }">
            <el-link type="primary" underline="never" @click="router.push(`/sales/order/${row.orderId}`)">{{ row.orderNo }}</el-link> 行 {{ row.lineNo }}
            <ErpBadge v-if="row.rereply" type="warning" :dot="false">需重新回复</ErpBadge>
          </template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户" width="120" show-overflow-tooltip />
        <el-table-column prop="ownerName" label="业务员" width="80" />
        <el-table-column label="物料" min-width="180"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
        <el-table-column label="未出货" width="90" align="right"><template #default="{ row }">{{ formatQty(row.openQty) }}</template></el-table-column>
        <el-table-column prop="customerDate" label="要求交期" width="105" />
        <el-table-column label="可用" width="80" align="right"><template #default="{ row }">{{ formatQty(row.availableQty) }}</template></el-table-column>
        <el-table-column label="在制" width="80" align="right"><template #default="{ row }">{{ formatQty(row.wipQty) }}</template></el-table-column>
        <el-table-column label="齐套" width="70"><template #default="{ row }"><el-button link type="primary" @click="analyze(row)">分析</el-button></template></el-table-column>
        <el-table-column label="建议交期" width="110">
          <template #default="{ row }"><el-tooltip :content="row.suggestBasis"><span class="text-muted">{{ row.suggestedDate }}</span></el-tooltip></template>
        </el-table-column>
        <el-table-column label="承诺交期" width="160">
          <template #default="{ row }">
            <el-date-picker v-model="row.promised" value-format="YYYY-MM-DD" :clearable="false" :disabled-date="disabledDate"
                            :class="{ late: row.promised && row.promised > row.customerDate }" class="w-full" />
          </template>
        </el-table-column>
        <el-table-column label="回复说明" min-width="140"><template #default="{ row }"><el-input v-model="row.remark" maxlength="256" /></template></el-table-column>
        <template #empty><ErpEmpty compact description="没有待回复的订单行" /></template>
      </el-table>
    </ErpPanel>

    <el-dialog v-model="kitVisible" :title="kitTitle" width="820px" append-to-body>
      <p v-if="kit" class="hint">
        最晚齐套 <strong class="num">{{ kit.kitDate }}</strong>，生产提前期 {{ kit.leadTimeDays }} 天 → 建议交期 <strong class="num">{{ kit.suggestedDate }}</strong>
      </p>
      <el-table :data="kit?.lines ?? []">
        <el-table-column label="子件" min-width="180"><template #default="{ row }">{{ row.code }} {{ row.name }}</template></el-table-column>
        <el-table-column label="需求" width="100" align="right"><template #default="{ row }">{{ formatQty(row.requiredQty) }}</template></el-table-column>
        <el-table-column label="可用" width="100" align="right">
          <template #default="{ row }"><span :class="{ 'text-danger': num(row.availableQty) < num(row.requiredQty) }">{{ formatQty(row.availableQty) }}</span></template>
        </el-table-column>
        <el-table-column label="在途" width="100" align="right"><template #default="{ row }">{{ formatQty(row.inTransitQty) }}</template></el-table-column>
        <el-table-column prop="kitDate" label="齐套日期" width="110" />
        <el-table-column prop="basis" label="依据" min-width="160" />
        <template #empty><ErpEmpty compact description="没有已审核的 BOM" /></template>
      </el-table>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.bar { display: flex; flex-wrap: wrap; align-items: center; gap: var(--erp-space-2); margin-bottom: var(--erp-space-3); }
.hint { color: var(--erp-color-text-secondary); }
.late :deep(.el-input__inner) { color: var(--erp-color-warning); }
</style>
