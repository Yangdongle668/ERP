<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { useUserStore } from '@/stores/user'
import { today } from '@/utils/format'
import { countApi, COUNT_STATUS, COUNT_STATUS_OPTIONS, type CountRow, type CountSave } from '../api/inventory'

defineOptions({ name: 'InvCountList' })

/** 盘点单列表（需求 08-06 4.1，T1）与新建盘点（4.2，T2 大弹窗） */
const router = useRouter()
const me = useUserStore()

type Query = { docNo?: string; countType?: string; warehouseId?: string; countStatuses?: string[]; dates?: string[] }
const { query, list, total, loading, load, search, reset } = useListPage<Query, CountRow>({
  api: (q) => {
    const { countStatuses, dates, ...rest } = q
    return countApi.page({ ...rest, countStatuses: countStatuses?.length ? countStatuses.join(',') : undefined, dateFrom: dates?.[0], dateTo: dates?.[1] })
  },
  defaultQuery: () => ({ countStatuses: ['DRAFT', 'COUNTING', 'SUBMITTED'] }),
  refreshOnActivated: true
})

const TYPE_OPTIONS = [{ value: 'FULL', label: '全盘' }, { value: 'PARTIAL', label: '抽盘' }]
const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'countType', label: '盘点类型', type: 'select', options: TYPE_OPTIONS },
  { prop: 'warehouseId', label: '仓库', type: 'slot' },
  { prop: 'countStatuses', label: '状态', type: 'select', options: COUNT_STATUS_OPTIONS, multiple: true },
  { prop: 'dates', label: '日期', type: 'daterange' }
]

const columns = computed<TableColumn<CountRow>[]>(() => [
  { prop: 'docNo', label: '单号', width: 150, type: 'link', onClick: (r) => router.push(`/inventory/count/${r.id}`) },
  { prop: 'countType', label: '类型', width: 70, formatter: (r) => (r.countType === 'FULL' ? '全盘' : '抽盘') },
  { prop: 'warehouseNames', label: '仓库', minWidth: 150 },
  { prop: 'scopeSummary', label: '范围', minWidth: 150 },
  { prop: 'lineCount', label: '行数', width: 70, align: 'right' },
  { prop: 'inputCount', key: 'progress', label: '已录入/总行数', width: 120, slot: true },
  { prop: 'diffCount', label: '差异行数', width: 90, align: 'right' },
  ...(me.hasPermission('inv:stock:cost') ? [{ prop: 'diffAmount', label: '差异金额', width: 110, type: 'amount' as const }] : []),
  { prop: 'countStatus', label: '状态', width: 90, type: 'status', statusMap: COUNT_STATUS },
  { prop: 'createdByName', label: '创建人', width: 90 },
  { prop: 'docDate', label: '日期', width: 100, type: 'date' }
])

// ---------- 新建 ----------
const visible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = ref<CountSave>({ countType: 'FULL', warehouseIds: [], includeZero: false, blindCount: true, docDate: today() })
const warehouseId = ref<string>()
const rules: FormRules = { warehouseIds: [{ required: true, type: 'array', min: 1, message: '请选择仓库', trigger: 'change' }] }
function openCreate() {
  form.value = { countType: 'FULL', warehouseIds: [], includeZero: false, blindCount: true, docDate: today(), materialIds: [] }
  warehouseId.value = undefined
  visible.value = true
}
function onWarehouse(v?: string) {
  form.value.warehouseIds = v ? [v] : []
}
async function create() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    const id = await countApi.create(form.value)
    ElMessage.success('已新建，请在详情页生成盘点表')
    visible.value = false
    router.push(`/inventory/count/${id}`)
  } finally {
    saving.value = false
  }
}
const asRow = (r: unknown) => r as CountRow
</script>

<template>
  <ErpPage description="核对账面库存与实物库存：生成盘点表后冻结范围内的库存，审核后生成盘盈入库、盘亏出库并解除冻结">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-warehouseId><WarehouseSelect v-model="query.warehouseId" placeholder="全部" class="w160" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="inv.count" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'inv:count:create'" type="primary" icon="Plus" @click="openCreate">新建盘点</el-button>
        </template>
        <template #col-inputCount="{ row }">
          <el-progress :percentage="asRow(row).lineCount ? Math.round((asRow(row).inputCount / asRow(row).lineCount) * 100) : 0" :stroke-width="6"
                       :format="() => `${asRow(row).inputCount}/${asRow(row).lineCount}`" />
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="visible" title="新建盘点" width="720px" :close-on-click-modal="false" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="盘点类型">
          <el-radio-group v-model="form.countType">
            <el-radio-button value="FULL">全盘</el-radio-button>
            <el-radio-button value="PARTIAL">抽盘</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="仓库" prop="warehouseIds"><WarehouseSelect v-model="warehouseId" class="w-full" @update:model-value="onWarehouse" /></el-form-item>
        <template v-if="form.countType === 'PARTIAL'">
          <el-form-item label="指定物料"><MaterialSelect v-model="form.materialIds" multiple class="w-full" placeholder="不选表示仓库内全部物料" /></el-form-item>
        </template>
        <el-form-item label="包含零库存"><el-switch v-model="form.includeZero" /></el-form-item>
        <el-form-item label="盲盘">
          <el-switch v-model="form.blindCount" />
          <span class="form-tip">录入和打印时不显示账面数量</span>
        </el-form-item>
        <el-form-item label="单据日期"><el-date-picker v-model="form.docDate" value-format="YYYY-MM-DD" :clearable="false" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" maxlength="512" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">保存</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>
