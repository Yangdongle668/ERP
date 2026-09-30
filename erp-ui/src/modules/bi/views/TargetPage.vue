<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { type KpiTargetRow, targetApi } from '../api/bi'

defineOptions({ name: 'BiTarget' })

/** KPI 目标（需求 13-02）：按月设置累计金额类指标的目标（本位币），驾驶舱在整月期间显示目标与达成率；留空表示不设目标 */
const thisYear = new Date().getFullYear()
const year = ref(thisYear)
const rows = ref<KpiTargetRow[]>([])
const loading = ref(false)
const saving = ref(false)
const months = Array.from({ length: 12 }, (_, i) => i + 1)
let original: string[] = []

async function load() {
  loading.value = true
  try {
    const r = await targetApi.year(year.value)
    rows.value = r.rows.map((x) => ({ ...x, values: [...x.values] }))
    original = r.rows.flatMap((x) => x.values.map((v) => v ?? ''))
  } finally {
    loading.value = false
  }
}
onMounted(load)

function sum(r: KpiTargetRow) {
  return r.values.reduce((t, v) => t + (v ? Number(v) : 0), 0)
}

async function save() {
  const items: { metricCode: string; month: number; value: string | null }[] = []
  let i = 0
  for (const r of rows.value) {
    for (const m of months) {
      const v = r.values[m - 1] ?? ''
      if (String(v) !== original[i]) items.push({ metricCode: r.metricCode, month: m, value: v === '' ? null : String(v) })
      i++
    }
  }
  if (!items.length) return ElMessage.info('没有修改')
  saving.value = true
  try {
    await targetApi.save(year.value, items)
    ElMessage.success('已保存')
    await load()
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ErpPage description="累计金额类指标的月度目标（本位币）。驾驶舱选择整月 / 整季 / 整年期间且期间内每月都有目标时，KPI 卡片显示目标与达成率（只对数据范围为“全部”的用户显示）">
    <ErpPanel>
      <template #filter>
        <div class="tg-toolbar">
          <el-input-number v-model="year" :min="2020" :max="2100" controls-position="right" @change="load" />
          <el-button v-perm="'bi:target:manage'" type="primary" :loading="saving" @click="save">保存</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="rows" row-key="metricCode">
        <el-table-column prop="metricName" label="指标" width="130" fixed />
        <el-table-column v-for="m in months" :key="m" :label="`${m} 月`" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.values[m - 1]" :min="0" :precision="2" :controls="false" class="tg-input" placeholder="不设" />
          </template>
        </el-table-column>
        <el-table-column label="全年合计" width="140" align="right" fixed="right">
          <template #default="{ row }"><span class="num">{{ sum(row as KpiTargetRow).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }}</span></template>
        </el-table-column>
      </el-table>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.tg-toolbar { display: flex; align-items: center; gap: var(--erp-space-3); }
.tg-input { width: 110px; }
</style>
