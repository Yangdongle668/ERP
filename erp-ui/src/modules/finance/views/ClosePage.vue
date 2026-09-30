<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { formatAmount } from '@/utils/format'
import { closeApi, PERIOD_STATUS, type CloseCheck, type ClosePeriod, type FxResult } from '../api/finance'

defineOptions({ name: 'FinClosePage' })

/**
 * 月结（需求 12-09，专用）：期间列表 + 结账向导（检查 → 外币重估 → 期末凭证 → 结账）。
 * 结账顺序：仓库月结 → 成本计算并锁定 → 外币重估 → 期末凭证过账 → 财务结账；只能反结账最近一个已结账期间，原因必填。
 */
const router = useRouter()
const me = useUserStore()
const year = ref(new Date().getFullYear())
const periods = ref<ClosePeriod[]>([])
const loading = ref(false)
const current = ref<ClosePeriod>()
const step = ref(0)
const check = ref<CloseCheck>()
const fx = ref<FxResult>()
const busy = ref(false)

async function load() {
  loading.value = true
  try {
    periods.value = await closeApi.periods(year.value)
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function openWizard(p: ClosePeriod) {
  current.value = p
  step.value = 0
  await reloadWizard()
}
async function reloadWizard() {
  if (!current.value) return
  const p = current.value.period
  const [c, f] = await Promise.all([closeApi.check(p), closeApi.fxPreview(p)])
  check.value = c
  fx.value = f
}
const item = (key: string) => check.value?.items.find((i) => i.key === key)
const blockingFailed = computed(() => check.value?.items.filter((i) => i.blocking && !i.passed && i.key !== 'FX' && !i.key.startsWith('VOUCHER')) ?? [])
const fxOk = computed(() => !!fx.value && (fx.value.done || fx.value.rows.length === 0))
const vouchersOk = computed(() => item('VOUCHER_POSTED')?.passed ?? true)

async function revalue() {
  busy.value = true
  try {
    fx.value = await closeApi.revalue(current.value!.period)
    ElMessage.success(fx.value.voucherNo ? `已生成重估凭证 ${fx.value.voucherNo}` : '无重估差异')
    await reloadWizard()
  } finally {
    busy.value = false
  }
}
async function doClose() {
  const p = current.value!.period
  await ElMessageBox.confirm(`结账 ${p}？结账后该期间的财务单据、凭证、核销不能再修改。`, '财务结账', { type: 'warning' })
  busy.value = true
  try {
    await closeApi.close(p)
    ElMessage.success(`${p} 已结账`)
    current.value = undefined
    await load()
  } finally {
    busy.value = false
  }
}
async function reopen(p: ClosePeriod) {
  const { value } = await ElMessageBox.prompt(`反结账 ${p.period}：成本将解锁可重新计算，下月的外币重估冲回凭证（草稿）将删除。`, '反结账', {
    inputPlaceholder: '请填写反结账原因', inputValidator: (v) => (!!v && v.trim().length > 0) || '请填写反结账原因', type: 'warning'
  })
  await closeApi.reopen(p.period, value)
  ElMessage.success(`${p.period} 已反结账`)
  await load()
}
const DOC_LABEL: Record<string, string> = { AR: '应收', AP: '应付', BANK: '银行存款' }
</script>

<template>
  <ErpPage description="结账顺序：仓库月结 → 成本计算并锁定 → 外币重估 → 期末凭证过账 → 财务结账；结账后该期间财务单据、凭证、核销均不能修改">
    <ErpPanel flush>
      <template #extra>
        <el-input-number v-model="year" :min="2000" :max="2100" controls-position="right" class="year" @change="load" />
      </template>
      <el-table v-loading="loading" :data="periods">
        <el-table-column prop="period" label="期间" width="100" />
        <el-table-column label="日期" width="210"><template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status" :map="PERIOD_STATUS" /></template></el-table-column>
        <el-table-column label="成本" width="90"><template #default="{ row }">{{ row.costLocked ? '已锁定' : '未锁定' }}</template></el-table-column>
        <el-table-column label="外币重估" width="90"><template #default="{ row }">{{ row.fxDone ? '已重估' : '-' }}</template></el-table-column>
        <el-table-column prop="closedByName" label="结账人" width="100" />
        <el-table-column prop="closedAt" label="结账时间" width="170" />
        <el-table-column label="操作" min-width="160">
          <template #default="{ row }">
            <el-button v-if="row.canClose && me.hasPermission('fin:close:query')" link type="primary" @click="openWizard(row as ClosePeriod)">结账向导</el-button>
            <el-button v-if="row.canReopen && me.hasPermission('fin:close:reopen')" link type="danger" @click="reopen(row as ClosePeriod)">反结账</el-button>
          </template>
        </el-table-column>
      </el-table>
      <ErpEmpty v-if="!loading && !periods.length" description="该年度没有会计期间，请在财务设置中初始化年度" />
    </ErpPanel>

    <ErpPanel v-if="current && check" :title="`${current.period} 结账向导`">
      <template #extra><el-button icon="Refresh" @click="reloadWizard">重新检查</el-button></template>
      <el-steps :active="step" finish-status="success" class="steps">
        <el-step title="检查" />
        <el-step title="外币重估" />
        <el-step title="期末凭证" />
        <el-step title="结账" />
      </el-steps>

      <div v-if="step === 0">
        <div v-for="i in check.items.filter((x) => x.key !== 'FX' && !x.key.startsWith('VOUCHER'))" :key="i.key" class="item">
          <el-icon :class="i.passed ? 'ok' : i.blocking ? 'red' : 'warn'"><CircleCheck v-if="i.passed" /><CircleClose v-else-if="i.blocking" /><Warning v-else /></el-icon>
          <span class="item-label">{{ i.label }}</span>
          <span v-if="i.message" class="sub">{{ i.message }}</span>
          <el-link v-if="!i.passed && i.route" type="primary" underline="never" @click="router.push(i.route)">去处理</el-link>
        </div>
        <div class="nav"><el-button type="primary" :disabled="blockingFailed.length > 0" @click="step = 1">下一步</el-button></div>
      </div>

      <div v-else-if="step === 1 && fx">
        <div v-if="fx.missingRates.length" class="red">请先维护 {{ fx.missingRates.join('、') }} {{ current.period }} 的月末汇率（系统管理 → 币别汇率）</div>
        <ErpEmpty v-if="!fx.rows.length" description="无外币余额，跳过重估" />
        <template v-else>
          <el-table :data="fx.rows" max-height="360">
            <el-table-column label="类型" width="90"><template #default="{ row }">{{ DOC_LABEL[row.docType] }}</template></el-table-column>
            <el-table-column prop="docNo" label="单据 / 账户" width="170" />
            <el-table-column prop="partnerName" label="往来单位" min-width="160" />
            <el-table-column prop="currency" label="币别" width="60" />
            <el-table-column label="原币余额" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.fcBalance) }}</template></el-table-column>
            <el-table-column label="账面本位币" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.bookBase) }}</template></el-table-column>
            <el-table-column label="月末汇率" width="100" align="right"><template #default="{ row }">{{ row.periodEndRate ?? '-' }}</template></el-table-column>
            <el-table-column label="重估本位币" width="120" align="right"><template #default="{ row }">{{ row.revaluedBase ? formatAmount(row.revaluedBase) : '-' }}</template></el-table-column>
            <el-table-column label="差异" width="110" align="right">
              <template #default="{ row }"><span :class="{ red: Number(row.diff) < 0 }">{{ row.diff ? formatAmount(row.diff) : '-' }}</span></template>
            </el-table-column>
          </el-table>
          <div class="sum">
            差异合计 <b>{{ formatAmount(fx.totalDiff) }}</b>
            <template v-if="fx.voucherNo">，重估凭证 <el-link type="primary" underline="never" @click="router.push(`/finance/voucher/${fx.voucherId}`)">{{ fx.voucherNo }}</el-link></template>
          </div>
        </template>
        <div class="nav">
          <el-button @click="step = 0">上一步</el-button>
          <el-button v-if="fx.rows.length && me.hasPermission('fin:close:execute')" :loading="busy" :disabled="fx.missingRates.length > 0" @click="revalue">
            {{ fx.done ? '重新生成重估凭证' : '生成重估凭证' }}
          </el-button>
          <el-button type="primary" :disabled="!fxOk" @click="step = 2">下一步</el-button>
        </div>
      </div>

      <div v-else-if="step === 2">
        <div v-for="k in ['VOUCHER_PENDING', 'VOUCHER_POSTED']" :key="k" class="item">
          <template v-if="item(k)">
            <el-icon :class="item(k)!.passed ? 'ok' : item(k)!.blocking ? 'red' : 'warn'"><CircleCheck v-if="item(k)!.passed" /><CircleClose v-else-if="item(k)!.blocking" /><Warning v-else /></el-icon>
            <span class="item-label">{{ item(k)!.label }}</span>
            <span v-if="item(k)!.message" class="sub">{{ item(k)!.message }}</span>
          </template>
        </div>
        <div class="sub">在凭证页 [生成凭证] 生成成本结转、汇兑损益等期末凭证，审核并过账后返回重新检查。</div>
        <div class="nav">
          <el-button @click="step = 1">上一步</el-button>
          <el-button @click="router.push('/finance/voucher')">去凭证</el-button>
          <el-button type="primary" :disabled="!vouchersOk" @click="step = 3">下一步</el-button>
        </div>
      </div>

      <div v-else>
        <p>确认结账后，{{ current.period }} 状态变为已结账，下一期间开启，并通知其他模块（发布财务期间结账事件）。</p>
        <div class="nav">
          <el-button @click="step = 2">上一步</el-button>
          <el-button v-if="me.hasPermission('fin:close:execute')" type="primary" :loading="busy" :disabled="!check.passed" @click="doClose">结账</el-button>
        </div>
      </div>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.year { width: 120px; }
.steps { margin-bottom: var(--erp-space-4); }
.item { display: flex; align-items: center; gap: var(--erp-space-2); padding: var(--erp-space-1) 0; }
.item-label { min-width: 180px; }
.sub { color: var(--erp-color-text-secondary); }
.nav { margin-top: var(--erp-space-4); display: flex; gap: var(--erp-space-2); }
.sum { margin-top: var(--erp-space-2); }
.ok { color: var(--erp-color-success); }
.warn { color: var(--erp-color-warning); }
.red { color: var(--erp-color-error); }
</style>
