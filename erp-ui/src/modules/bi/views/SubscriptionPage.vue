<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { type Metric, metricApi, SUB_FREQUENCY, SUB_PERIOD, type Subscription, type SubscriptionSave, subscriptionApi, WEEKDAYS } from '../api/bi'

defineOptions({ name: 'BiSubscription' })

/** 报表订阅（需求 13-03）：按频率把指标汇总以工作台消息（可选邮件）发给自己；内容按订阅人的权限和数据范围生成 */
const rows = ref<Subscription[]>([])
const metrics = ref<Metric[]>([])
const dims = ref<{ code: string; label: string }[]>([])
const loading = ref(false)
const saving = ref(false)
const sending = ref('')
const preview = reactive({ visible: false, title: '', content: '' })
const dialog = reactive({ visible: false, id: '' })

const blank = (): SubscriptionSave => ({
  name: '', metrics: [], dimension: undefined, filters: undefined, periodType: 'LAST_WEEK', topN: 10,
  frequency: 'WEEKLY', weekday: 1, monthday: 1, sendEmail: false, enabled: true
})
const form = reactive<SubscriptionSave>(blank())
const monthdays = Array.from({ length: 28 }, (_, i) => i + 1)
const isEdit = computed(() => !!dialog.id)

async function load() {
  loading.value = true
  try {
    rows.value = await subscriptionApi.list()
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  await load()
  metrics.value = await metricApi.visible()
  dims.value = await subscriptionApi.dimensions()
})

function scheduleText(r: Subscription) {
  if (r.frequency === 'WEEKLY') return `每${WEEKDAYS[(r.weekday ?? 1) - 1]}`
  if (r.frequency === 'MONTHLY') return `每月 ${r.monthday} 日`
  return SUB_FREQUENCY[r.frequency]
}

function openCreate() {
  Object.assign(form, blank())
  dialog.id = ''
  dialog.visible = true
}
function openEdit(r: Subscription) {
  Object.assign(form, blank(), {
    name: r.name, metrics: [...r.metrics], dimension: r.dimension, filters: r.filters, periodType: r.periodType, topN: r.topN,
    frequency: r.frequency, weekday: r.weekday ?? 1, monthday: r.monthday ?? 1, sendEmail: r.sendEmail, enabled: r.enabled
  })
  dialog.id = r.id
  dialog.visible = true
}

async function save() {
  saving.value = true
  try {
    if (isEdit.value) await subscriptionApi.update(dialog.id, form)
    else await subscriptionApi.create(form)
    ElMessage.success('已保存')
    dialog.visible = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggle(r: Subscription, enabled: boolean) {
  await subscriptionApi.update(r.id, { ...r, enabled })
  await load()
}

async function sendNow(r: Subscription) {
  sending.value = r.id
  try {
    const rep = await subscriptionApi.send(r.id)
    Object.assign(preview, { visible: true, title: rep.title, content: rep.content })
    await load()
  } finally {
    sending.value = ''
  }
}

async function remove(r: Subscription) {
  await ElMessageBox.confirm(`删除订阅“${r.name}”？`, '删除', { type: 'warning' })
  await subscriptionApi.remove(r.id)
  ElMessage.success('已删除')
  await load()
}
</script>

<template>
  <ErpPage description="按设定的频率把指标汇总发到工作台消息（可同时发邮件）。只能订阅自己有权限的指标，内容按你的数据范围生成">
    <ErpPanel>
      <template #filter>
        <el-button v-perm="'bi:subscription:manage'" type="primary" @click="openCreate">新建订阅</el-button>
      </template>
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="指标" min-width="220"><template #default="{ row }">{{ (row as Subscription).metricNames.join('、') }}</template></el-table-column>
        <el-table-column label="期间" width="100"><template #default="{ row }">{{ SUB_PERIOD[(row as Subscription).periodType] }}</template></el-table-column>
        <el-table-column label="发送" width="120"><template #default="{ row }">{{ scheduleText(row as Subscription) }}</template></el-table-column>
        <el-table-column label="邮件" width="70"><template #default="{ row }">{{ (row as Subscription).sendEmail ? '是' : '否' }}</template></el-table-column>
        <el-table-column label="上次发送" width="200">
          <template #default="{ row }">
            <template v-if="(row as Subscription).lastSentOn">
              {{ (row as Subscription).lastSentOn }}
              <ErpBadge :type="(row as Subscription).lastStatus === 'SUCCESS' ? 'success' : 'danger'">{{ (row as Subscription).lastStatus === 'SUCCESS' ? '成功' : '失败' }}</ErpBadge>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }"><el-switch :model-value="(row as Subscription).enabled" @change="(v: boolean | string | number) => toggle(row as Subscription, Boolean(v))" /></template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :loading="sending === (row as Subscription).id" @click="sendNow(row as Subscription)">立即发送</el-button>
            <el-button link type="primary" @click="openEdit(row as Subscription)">编辑</el-button>
            <el-button link type="danger" @click="remove(row as Subscription)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><ErpEmpty compact description="还没有订阅" /></template>
      </el-table>
    </ErpPanel>

    <el-dialog v-model="dialog.visible" :title="isEdit ? '编辑订阅' : '新建订阅'" width="560px">
      <el-form label-width="90px">
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="64" /></el-form-item>
        <el-form-item label="指标" required>
          <el-select v-model="form.metrics" multiple :multiple-limit="8" filterable placeholder="选择 1～8 个指标" class="full">
            <el-option v-for="m in metrics" :key="m.code" :label="m.name" :value="m.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="分组维度">
          <el-select v-model="form.dimension" clearable placeholder="不分组，只发汇总" class="full">
            <el-option v-for="d in dims" :key="d.code" :label="d.label" :value="d.code" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.dimension" label="取前 N 项"><el-input-number v-model="form.topN" :min="1" :max="50" /></el-form-item>
        <el-form-item label="统计期间" required>
          <el-select v-model="form.periodType"><el-option v-for="(l, k) in SUB_PERIOD" :key="k" :label="l" :value="k" /></el-select>
        </el-form-item>
        <el-form-item label="发送频率" required>
          <el-select v-model="form.frequency" class="gap"><el-option v-for="(l, k) in SUB_FREQUENCY" :key="k" :label="l" :value="k" /></el-select>
          <el-select v-if="form.frequency === 'WEEKLY'" v-model="form.weekday"><el-option v-for="(l, i) in WEEKDAYS" :key="i" :label="l" :value="i + 1" /></el-select>
          <el-select v-if="form.frequency === 'MONTHLY'" v-model="form.monthday"><el-option v-for="d in monthdays" :key="d" :label="`${d} 日`" :value="d" /></el-select>
        </el-form-item>
        <el-form-item label="同时发邮件"><el-switch v-model="form.sendEmail" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="preview.visible" :title="preview.title" width="560px">
      <pre class="sub-preview">{{ preview.content }}</pre>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.full { width: 100%; }
.gap { margin-right: var(--erp-space-2); }
.sub-preview { margin: 0; white-space: pre-wrap; font-family: inherit; font-size: var(--erp-font-size-body); color: var(--erp-color-text); }
</style>
