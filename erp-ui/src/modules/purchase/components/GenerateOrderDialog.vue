<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi } from '../api/order'
import type { PendingLine } from '../api/requisition'

/**
 * 从采购申请生成订单（需求 07-05 3.2）：确认每行供应商与本次数量后，按供应商分组生成草稿订单。
 * open(lines) 打开；生成后只有一张订单时跳转到编辑页。
 */
const emit = defineEmits<{ done: [orderIds: string[]] }>()
const router = useRouter()

interface GenLine extends PendingLine {
  supplierId?: string
  genQty?: string
}
const visible = ref(false)
const lines = ref<GenLine[]>([])
const generating = ref(false)

function open(rows: PendingLine[]) {
  lines.value = rows.map((l) => ({ ...l, supplierId: l.suggestedSupplierId, genQty: l.pendingQty }))
  visible.value = true
}

async function generate() {
  const miss = lines.value.findIndex((l) => !l.supplierId)
  if (miss >= 0) return ElMessage.warning(`第 ${miss + 1} 行：请选择供应商`)
  generating.value = true
  try {
    const r = await orderApi.fromRequisitions(lines.value.map((l) => ({ requisitionLineId: l.id, supplierId: l.supplierId!, qty: l.genQty })))
    visible.value = false
    if (r.messages.length) {
      await ElMessageBox.alert(`<p>已生成 ${r.orderIds.length} 张草稿订单。</p><ul>${r.messages.map((m) => `<li>${m}</li>`).join('')}</ul>`, '生成结果',
        { dangerouslyUseHTMLString: true })
    } else {
      ElMessage.success(`已生成 ${r.orderIds.length} 张草稿订单`)
    }
    emit('done', r.orderIds)
    if (r.orderIds.length === 1) router.push(`/purchase/order/${r.orderIds[0]}/edit`)
  } finally {
    generating.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="生成采购订单" width="1200px" :close-on-click-modal="false" append-to-body>
    <p class="form-tip">按供应商分组生成草稿订单；数量会按供应商 MOQ / MPQ 调整，单价取有效价格</p>
    <el-table :data="lines" max-height="480">
      <el-table-column prop="docNo" label="申请单号" width="150" />
      <el-table-column prop="materialCode" label="物料编码" width="130" />
      <el-table-column prop="materialName" label="名称" min-width="150" />
      <el-table-column prop="baseUom" label="单位" width="60" />
      <el-table-column prop="pendingQty" label="未转数量" width="100" align="right" />
      <el-table-column label="本次数量" width="140"><template #default="{ row }"><QtyInput v-model="row.genQty" :uom="row.baseUom" /></template></el-table-column>
      <el-table-column label="供应商" min-width="220"><template #default="{ row }"><SupplierSelect v-model="row.supplierId" :statuses="['QUALIFIED']" /></template></el-table-column>
      <el-table-column prop="requiredDate" label="需求日期" width="110" />
    </el-table>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="generating" @click="generate">生成</el-button>
    </template>
  </el-dialog>
</template>
