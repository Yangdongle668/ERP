<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { formatAmount } from '@/utils/format'
import { periodApi, PERIOD_STATUS, type CheckResult, type OpeningInfo, type PeriodRow } from '../api/inventory'

defineOptions({ name: 'InvPeriodList' })

/** 期初与月结（需求 08-09）：启用期间、期初导入/清空/完成；月结检查、月结、反结账 */
const router = useRouter()
const me = useUserStore()
const tab = ref('period')
const periods = ref<PeriodRow[]>([])
const opening = ref<OpeningInfo>()
const importRef = ref<{ open: () => void }>()

async function load() {
  const [p, o] = await Promise.all([periodApi.list(), periodApi.opening()])
  periods.value = p
  opening.value = o
  if (!o.period || !o.completed) tab.value = 'opening'
}

// ---------- 启用 ----------
const initPeriod = ref<string>()
async function init() {
  if (!initPeriod.value) return ElMessage.warning('请选择启用期间')
  await ElMessageBox.confirm(`启用期间设为 ${initPeriod.value} 后不能修改，启用期间之前的日期不能有出入库。确定吗？`, '设置启用期间', { type: 'warning' })
  await periodApi.init(initPeriod.value)
  ElMessage.success('已设置启用期间')
  load()
}
async function clearOpening() {
  await ElMessageBox.confirm('将冲销并作废全部期初入库单，确定清空吗？', '清空期初', { type: 'warning' })
  await periodApi.clearOpening()
  ElMessage.success('已清空期初')
  load()
}
async function completeOpening() {
  await ElMessageBox.confirm('完成后不能再导入或清空期初，其他出入库单才能确认。确定完成期初吗？', '完成期初', { type: 'warning' })
  await periodApi.completeOpening()
  ElMessage.success('期初已完成')
  load()
}

// ---------- 月结 ----------
const checkVisible = ref(false)
const check = ref<CheckResult>()
const closing = ref(false)
async function openClose(p: PeriodRow) {
  check.value = await periodApi.check(p.period)
  checkVisible.value = true
}
async function close() {
  closing.value = true
  try {
    await periodApi.close(check.value!.period)
    ElMessage.success(`${check.value!.period} 已结账`)
    checkVisible.value = false
    load()
  } finally {
    closing.value = false
  }
}
async function reopen(p: PeriodRow) {
  await ElMessageBox.confirm(`反结账后删除 ${p.period} 的期末结存，期间回到未结账。确定吗？`, '反结账', { type: 'warning' })
  await periodApi.reopen(p.period)
  ElMessage.success('已反结账')
  load()
}

const asP = (r: unknown) => r as PeriodRow
onMounted(load)
</script>

<template>
  <ErpPage description="启用期间之前的日期不能有出入库；期初完成前不能确认其他单据；已结账期间不能过账">
    <ErpPanel flush>
      <el-tabs v-model="tab" class="tabs">
        <el-tab-pane label="库存期间" name="period">
          <el-table :data="periods">
            <el-table-column prop="period" label="期间" width="100" />
            <el-table-column label="开始 / 结束" width="220"><template #default="{ row }">{{ row.startDate }} ～ {{ row.endDate }}</template></el-table-column>
            <el-table-column label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.periodStatus" :map="PERIOD_STATUS" /></template></el-table-column>
            <el-table-column label="" width="140">
              <template #default="{ row }">
                <ErpBadge v-if="row.opening" type="primary">启用期间</ErpBadge>
                <ErpBadge v-if="row.financeClosed" type="warning">财务已结账</ErpBadge>
              </template>
            </el-table-column>
            <el-table-column prop="closedByName" label="结账人" width="100" />
            <el-table-column label="结账时间" width="170"><template #default="{ row }">{{ row.closedAt?.slice(0, 16) ?? '' }}</template></el-table-column>
            <el-table-column label="操作" min-width="140">
              <template #default="{ row }">
                <el-button v-if="row.canClose && me.hasPermission('inv:period:close')" link type="primary" @click="openClose(asP(row))">月结</el-button>
                <el-button v-if="row.canReopen && me.hasPermission('inv:period:reopen')" link type="danger" @click="reopen(asP(row))">反结账</el-button>
              </template>
            </el-table-column>
            <template #empty><ErpEmpty compact description="尚未设置启用期间，请在“期初”页签设置" /></template>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="期初" name="opening">
          <template v-if="opening && !opening.period">
            <div class="init">
              <p>首次使用时设置启用期间（通常为上线首月），系统创建该期间；随后导入期初库存并完成期初。</p>
              <el-date-picker v-model="initPeriod" type="month" value-format="YYYYMM" placeholder="选择启用期间" />
              <el-button v-perm="'inv:opening:import'" type="primary" @click="init">设置启用期间</el-button>
            </div>
          </template>
          <template v-else-if="opening">
            <el-descriptions :column="3" class="info">
              <el-descriptions-item label="启用期间">{{ opening.period }}</el-descriptions-item>
              <el-descriptions-item label="期初日期">{{ opening.openingDate }}（启用期间第一天的前一天）</el-descriptions-item>
              <el-descriptions-item label="状态">
                <ErpBadge :type="opening.completed ? 'success' : 'warning'">{{ opening.completed ? '期初已完成' : '期初未完成' }}</ErpBadge>
              </el-descriptions-item>
              <el-descriptions-item label="期初入库单">
                <el-link type="primary" underline="never" @click="router.push({ path: '/inventory/in' })">{{ opening.docCount }} 张</el-link>，{{ opening.lineCount }} 行
              </el-descriptions-item>
              <el-descriptions-item v-if="opening.totalAmount" label="期初金额"><span class="num">{{ formatAmount(opening.totalAmount) }}</span></el-descriptions-item>
            </el-descriptions>
            <div v-if="!opening.completed" class="actions">
              <el-button v-perm="'inv:opening:import'" type="primary" icon="Upload" @click="importRef?.open()">导入期初</el-button>
              <el-button v-if="opening.canClear" v-perm="'inv:opening:import'" @click="clearOpening">清空期初</el-button>
              <el-button v-perm="'inv:opening:import'" type="success" icon="Check" @click="completeOpening">完成期初</el-button>
            </div>
            <el-alert v-else type="success" :closable="false" show-icon title="期初已完成，不能再导入或清空期初" />
          </template>
        </el-tab-pane>
      </el-tabs>
    </ErpPanel>

    <el-dialog v-model="checkVisible" :title="`月结检查 - ${check?.period ?? ''}`" width="620px" append-to-body>
      <template v-if="check">
        <el-alert v-if="!check.items.length" type="success" :closable="false" show-icon title="检查全部通过，可以结账" />
        <div v-for="(it, i) in check.items" :key="i" class="check-item">
          <div class="check-title">
            <ErpBadge :type="it.level === 'BLOCK' ? 'danger' : 'warning'">{{ it.level === 'BLOCK' ? '阻止' : '警告' }}</ErpBadge>
            <span>{{ it.title }}</span>
          </div>
          <ul v-if="it.details.length" class="check-details">
            <li v-for="x in it.details.slice(0, 20)" :key="x">{{ x }}</li>
            <li v-if="it.details.length > 20" class="text-muted">… 共 {{ it.details.length }} 项</li>
          </ul>
        </div>
      </template>
      <template #footer>
        <el-button @click="checkVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!check?.passed" :loading="closing" @click="close">确认结账</el-button>
      </template>
    </el-dialog>

    <ImportDialog ref="importRef" title="导入期初" base="/inventory/opening" template-name="期初库存" @done="load" />
  </ErpPage>
</template>

<style scoped>
.tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
.init { display: flex; flex-direction: column; align-items: flex-start; gap: var(--erp-space-3); }
.init p { margin: 0; color: var(--erp-color-text-secondary); }
.info { margin-bottom: var(--erp-space-4); }
.actions { display: flex; gap: var(--erp-space-2); }
.check-item { margin-bottom: var(--erp-space-3); }
.check-title { display: flex; align-items: center; gap: var(--erp-space-2); }
.check-details { margin: var(--erp-space-1) 0 0; padding-left: var(--erp-space-6); color: var(--erp-color-text-secondary); }
</style>
