<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { etlApi, type EtlJob, type EtlLog, ETL_RESULT, ETL_STATUS } from '../api/bi'
import { formatDateTime, formatElapsed } from '@/utils/format'

/** 数据任务（需求 13-01 第 4 节）：增量处理器、各汇总表全量校对、库存快照的运行记录与差异行数 */
const jobs = ref<EtlJob[]>([])
const logs = ref<EtlLog[]>([])
const jobCode = ref<string>()
const loading = ref(false)
const running = ref<string>()

async function load() {
  loading.value = true
  try {
    ;[jobs.value, logs.value] = await Promise.all([etlApi.jobs(), etlApi.logs(jobCode.value)])
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function run(j: EtlJob) {
  running.value = j.code
  try {
    const r = await etlApi.run(j.code)
    if (r.lastResult === 'SUCCESS') ElMessage.success(`${j.name}完成：处理 ${r.lastRows ?? 0} 行，差异 ${r.lastDiffRows ?? 0} 行`)
    else ElMessage.error(`${j.name}失败：${r.lastMessage ?? ''}`)
    await load()
  } finally {
    running.value = undefined
  }
}
const jobName = (code: string) => jobs.value.find((j) => j.code === code)?.name ?? code
</script>

<template>
  <ErpPage description="增量处理每 10 分钟更新最近数据；每晚全量校对最近 3 个月并修正差异；库存每日 23:50 快照。定时时间可在“定时任务”中调整">
    <ErpPanel title="数据任务">
      <template #extra><el-button icon="Refresh" @click="load">刷新</el-button></template>
      <el-table v-loading="loading" :data="jobs" row-key="code">
        <el-table-column prop="name" label="任务" min-width="170" />
        <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.jobStatus" :map="ETL_STATUS" /></template></el-table-column>
        <el-table-column label="上次运行" width="160"><template #default="{ row }">{{ formatDateTime(row.lastStartedAt) }}</template></el-table-column>
        <el-table-column label="耗时" width="100" align="right"><template #default="{ row }">{{ formatElapsed(row.lastDurationMs) }}</template></el-table-column>
        <el-table-column prop="lastRows" label="处理行数" width="100" align="right" />
        <el-table-column prop="lastDiffRows" label="差异行数" width="100" align="right" />
        <el-table-column label="结果" width="90"><template #default="{ row }"><StatusTag v-if="row.lastResult" :value="row.lastResult" :map="ETL_RESULT" /></template></el-table-column>
        <el-table-column prop="lastMessage" label="说明" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :loading="running === row.code" :disabled="!!running && running !== row.code" @click="run(row as EtlJob)">立即运行</el-button>
          </template>
        </el-table-column>
      </el-table>
    </ErpPanel>
    <ErpPanel title="运行日志">
      <template #extra>
        <el-select v-model="jobCode" placeholder="全部任务" clearable class="bi-sel" @change="load">
          <el-option v-for="j in jobs" :key="j.code" :value="j.code" :label="j.name" />
        </el-select>
      </template>
      <el-table :data="logs" row-key="id">
        <el-table-column label="任务" min-width="170"><template #default="{ row }">{{ jobName(row.jobCode) }}</template></el-table-column>
        <el-table-column label="开始" width="160"><template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template></el-table-column>
        <el-table-column label="结束" width="160"><template #default="{ row }">{{ formatDateTime(row.finishedAt) }}</template></el-table-column>
        <el-table-column label="结果" width="90"><template #default="{ row }"><StatusTag :value="row.result" :map="ETL_RESULT" /></template></el-table-column>
        <el-table-column prop="rowCount" label="处理行数" width="100" align="right" />
        <el-table-column prop="diffRows" label="差异行数" width="100" align="right" />
        <el-table-column prop="message" label="说明" min-width="200" show-overflow-tooltip />
      </el-table>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.bi-sel { width: 200px; }
</style>
