<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { useUserStore } from '@/stores/user'
import {
  currentPeriod, joinList, labelOf, optionsOf, VOUCHER_BIZ_TYPES, VOUCHER_SOURCES, VOUCHER_STATUS, voucherApi, type VoucherPending, type VoucherRow
} from '../api/finance'

defineOptions({ name: 'FinVoucherList' })

/**
 * 凭证列表（需求 12-06 4.1，T1）：按期间查询；[生成凭证] 选择业务类型按单据或按类型汇总生成草稿；批量审核 / 过账；整理凭证号；导出通用 Excel
 */
const router = useRouter()
const me = useUserStore()
type Query = { period?: string; voucherNo?: string; statuses?: string[]; source?: string; accountCode?: string; summary?: string; creatorId?: string }
const toParams = (q: Query) => ({ ...q, statuses: joinList(q.statuses) })
const { query, list, total, loading, load, search, reset, selection, onSelectionChange } = useListPage<Query, VoucherRow>({
  api: (q) => voucherApi.page(toParams(q) as never),
  defaultQuery: () => ({ period: currentPeriod() }),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'period', label: '期间', placeholder: 'yyyyMM' },
  { prop: 'voucherNo', label: '凭证号' },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(VOUCHER_STATUS), multiple: true },
  { prop: 'source', label: '来源', type: 'select', options: VOUCHER_SOURCES },
  { prop: 'accountCode', label: '科目' },
  { prop: 'summary', label: '摘要' },
  { prop: 'creatorId', label: '制单人', type: 'user' }
]
const columns: TableColumn<VoucherRow>[] = [
  { prop: 'voucherNo', label: '凭证号', width: 160, type: 'link', onClick: (r) => router.push(`/finance/voucher/${r.id}`) },
  { prop: 'voucherDate', label: '日期', width: 100, type: 'date' },
  { prop: 'summary', label: '摘要', minWidth: 200 },
  { prop: 'totalDebit', label: '借方合计', width: 130, type: 'amount', summary: true },
  { prop: 'totalCredit', label: '贷方合计', width: 130, type: 'amount', summary: true },
  { prop: 'attachmentCount', label: '附件', width: 60, align: 'right' },
  { prop: 'source', label: '来源', width: 110, formatter: (r) => r.source === 'AUTO' ? labelOf(VOUCHER_BIZ_TYPES, r.bizType) || '自动' : '手工' },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: VOUCHER_STATUS },
  { prop: 'creatorName', label: '制单人', width: 90 },
  { prop: 'auditorName', label: '审核人', width: 90 },
  { prop: 'posterName', label: '过账人', width: 90 }
]

async function batch(action: 'batch-audit' | 'batch-post') {
  if (!selection.value.length) return ElMessage.warning('请勾选凭证')
  const r = await voucherApi.batch(selection.value.map((v) => v.id), action)
  if (r.errors.length) ElNotification({ type: 'warning', title: `成功 ${r.success} 张，失败 ${r.errors.length} 张`, message: r.errors.join('；'), duration: 10000 })
  else ElMessage.success(`已处理 ${r.success} 张`)
  load()
}
async function renumber() {
  const period = query.period || currentPeriod()
  await ElMessageBox.confirm(`按日期重排 ${period} 草稿与已审核凭证的凭证号（已过账的不变）？`, '整理凭证号', { type: 'warning' })
  const r = await voucherApi.renumber(period)
  ElMessage.success(`已整理，${r.changed} 张凭证号变化`)
  load()
}

// 生成凭证
const genVisible = ref(false)
const genLoading = ref(false)
const gen = ref<{ period: string; bizTypes: string[]; mode: string }>({ period: currentPeriod(), bizTypes: [], mode: 'SUMMARY' })
const pending = ref<VoucherPending[]>([])
async function openGenerate() {
  gen.value = { period: query.period || currentPeriod(), bizTypes: [], mode: 'SUMMARY' }
  await loadPending()
  genVisible.value = true
}
async function loadPending() {
  pending.value = await voucherApi.pending(gen.value.period)
  gen.value.bizTypes = pending.value.filter((p) => p.count > 0).map((p) => p.bizType)
}
async function generate() {
  if (!gen.value.bizTypes.length) return ElMessage.warning('请选择业务类型')
  genLoading.value = true
  try {
    const r = await voucherApi.generate(gen.value.period, gen.value.bizTypes, gen.value.mode)
    genVisible.value = false
    if (r.messages.length) ElNotification({ type: 'warning', title: '部分单据未生成', message: r.messages.join('；'), duration: 10000 })
    ElMessage.success(`已生成 ${r.voucherCount} 张凭证（${r.docCount} 张单据）`)
    query.period = gen.value.period
    load()
  } finally {
    genLoading.value = false
  }
}
</script>

<template>
  <ErpPage description="业务单据按科目映射生成凭证草稿；借贷相等才能审核，审核人不能是制单人；已结账期间不能新增、修改、过账">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset" />
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" selection storage-key="fin.voucher" @refresh="load" @selection-change="onSelectionChange">
        <template #toolbar>
          <el-button v-if="me.hasPermission('fin:voucher:create')" type="primary" @click="router.push('/finance/voucher/new')">新建凭证</el-button>
          <el-button v-if="me.hasPermission('fin:voucher:create')" @click="openGenerate">生成凭证</el-button>
          <el-button v-if="me.hasPermission('fin:voucher:audit')" @click="batch('batch-audit')">批量审核</el-button>
          <el-button v-if="me.hasPermission('fin:voucher:post')" @click="batch('batch-post')">批量过账</el-button>
          <el-button v-if="me.hasPermission('fin:voucher:update')" @click="renumber">整理凭证号</el-button>
          <ExportButton url="/finance/vouchers/export" :params="() => toParams(query)" permission="fin:voucher:export" />
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="genVisible" title="生成凭证" width="560px">
      <el-form label-width="90px">
        <el-form-item label="期间">
          <el-input v-model="gen.period" class="period" placeholder="yyyyMM" @change="loadPending" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-checkbox-group v-model="gen.bizTypes" class="types">
            <el-checkbox v-for="p in pending" :key="p.bizType" :value="p.bizType" :disabled="p.count === 0">{{ p.label }}（{{ p.count }}）</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="生成方式">
          <el-radio-group v-model="gen.mode">
            <el-radio value="SUMMARY">按类型汇总</el-radio>
            <el-radio value="PER_DOC">按单据生成</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="genVisible = false">取消</el-button>
        <el-button type="primary" :loading="genLoading" @click="generate">生成</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.period { width: 140px; }
.types { display: grid; grid-template-columns: 1fr 1fr; }
</style>
