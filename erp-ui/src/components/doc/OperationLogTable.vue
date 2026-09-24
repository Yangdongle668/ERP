<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { systemCommonApi, type DocLog } from '@/api/system'
import type { StatusMap } from '../types'
import StatusTag from '../form/StatusTag.vue'
import { formatDateTime } from '@/utils/format'

/** 单据操作日志（01-11 日志审计 3.2）：时间、操作人、动作、状态变化、原因，按时间正序 */
const props = defineProps<{ bizType: string; bizId?: string; statusMap?: StatusMap }>()
const list = ref<DocLog[]>([])
const loading = ref(false)

async function load() {
  if (!props.bizId) return
  loading.value = true
  try {
    list.value = await systemCommonApi.docLogs(props.bizType, props.bizId)
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch(() => props.bizId, load)
defineExpose({ reload: load })
</script>

<template>
  <el-table v-loading="loading" :data="list" border>
    <el-table-column label="时间" width="170"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
    <el-table-column prop="operatorName" label="操作人" width="120" />
    <el-table-column prop="actionName" label="动作" width="120" />
    <el-table-column label="状态变化" width="240">
      <template #default="{ row }">
        <template v-if="row.fromStatus || row.toStatus">
          <StatusTag :value="row.fromStatus" :map="statusMap" /> → <StatusTag :value="row.toStatus" :map="statusMap" />
        </template>
        <span v-else>-</span>
      </template>
    </el-table-column>
    <el-table-column prop="reason" label="原因" min-width="200" show-overflow-tooltip />
    <template #empty><el-empty description="暂无操作记录" :image-size="60" /></template>
  </el-table>
</template>
