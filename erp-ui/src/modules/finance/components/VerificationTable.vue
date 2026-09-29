<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { formatAmount, formatDateTime } from '@/utils/format'
import { VERIFY_TYPES, verifyApi, type Verification } from '../api/finance'

/** 单据详情中的核销记录：对方单据可跳转；未反核销的记录可反核销（FIN-RV-R05） */
const props = defineProps<{ data: Verification[]; docType: string; docId: string; unverifyPermission: string }>()
const emit = defineEmits<{ changed: [] }>()
const router = useRouter()
const me = useUserStore()

const ROUTES: Record<string, string> = { RECEIPT: '/finance/receipt/', RECEIVABLE: '/finance/receivable/', PAYMENT: '/finance/payment/', PAYABLE: '/finance/payable/' }
/** 对方单据：本单是 A 时显示 B，反之显示 A */
const other = (v: Verification) =>
  v.docAType === props.docType && v.docAId === props.docId
    ? { type: v.docBType, id: v.docBId, no: v.docBNo }
    : { type: v.docAType, id: v.docAId, no: v.docANo }

async function reverse(v: Verification) {
  await ElMessageBox.confirm(`确定反核销 ${formatAmount(v.amount)} 吗？双方未核销金额将恢复。`, '反核销', { type: 'warning' })
  await verifyApi.reverse(v.id)
  ElMessage.success('已反核销')
  emit('changed')
}
</script>

<template>
  <el-table :data="data">
    <el-table-column label="类型" width="120"><template #default="{ row }">{{ VERIFY_TYPES[row.verifyType] ?? row.verifyType }}</template></el-table-column>
    <el-table-column label="对方单据" width="170">
      <template #default="{ row }">
        <el-link type="primary" underline="never" @click="router.push(ROUTES[other(row as Verification).type] + other(row as Verification).id)">{{ other(row as Verification).no }}</el-link>
      </template>
    </el-table-column>
    <el-table-column label="核销金额" width="130" align="right"><template #default="{ row }">{{ row.currency }} {{ formatAmount(row.amount) }}</template></el-table-column>
    <el-table-column label="汇兑差异" width="110" align="right"><template #default="{ row }">{{ formatAmount(row.fxDiff) }}</template></el-table-column>
    <el-table-column prop="period" label="期间" width="80" />
    <el-table-column label="核销时间" width="150"><template #default="{ row }">{{ formatDateTime(row.verifiedAt, true) }}</template></el-table-column>
    <el-table-column prop="operatorName" label="操作人" width="90" />
    <el-table-column label="状态" width="130">
      <template #default="{ row }">
        <span v-if="row.reversed" class="muted">已反核销 {{ formatDateTime(row.reversedAt, true) }}</span>
        <span v-else>有效</span>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="80" fixed="right">
      <template #default="{ row }">
        <el-button v-if="!row.reversed && me.hasPermission(unverifyPermission)" link type="danger" @click="reverse(row as Verification)">反核销</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.muted { color: var(--erp-color-text-secondary); }
</style>
