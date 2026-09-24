<script setup lang="ts">
import type { RelatedDoc } from '../types'
import { useRouter } from 'vue-router'
import { formatDate } from '@/utils/format'
import StatusTag from '../form/StatusTag.vue'

/**
 * 上下游关联单据：单据类型、单号、日期、状态，点击单号跳转详情。
 * 数据由业务模块的详情接口提供（各模块自己知道上下游）。
 */
defineProps<{ docs: RelatedDoc[] }>()
const router = useRouter()
</script>

<template>
  <el-table :data="docs" border>
    <el-table-column label="方向" width="80" align="center">
      <template #default="{ row }"><el-tag :type="row.direction === 'UP' ? 'info' : 'primary'" size="small">{{ row.direction === 'UP' ? '上游' : '下游' }}</el-tag></template>
    </el-table-column>
    <el-table-column prop="docTypeName" label="单据类型" width="140" />
    <el-table-column label="单号" min-width="180">
      <template #default="{ row }">
        <el-link v-if="row.route" type="primary" underline="never" @click="router.push(row.route)">{{ row.docNo }}</el-link>
        <span v-else>{{ row.docNo }}</span>
      </template>
    </el-table-column>
    <el-table-column label="日期" width="120" align="center"><template #default="{ row }">{{ formatDate(row.docDate) }}</template></el-table-column>
    <el-table-column label="状态" width="120" align="center">
      <template #default="{ row }"><span v-if="row.statusLabel">{{ row.statusLabel }}</span><StatusTag v-else :value="row.status" /></template>
    </el-table-column>
    <template #empty><el-empty description="暂无关联单据" :image-size="60" /></template>
  </el-table>
</template>
