<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { toDateString } from '@/utils/format'
import FollowupDialog from '../components/FollowupDialog.vue'
import { followupApi, type FollowupQuery, type FollowupRow } from '../api/crm'

defineOptions({ name: 'CrmFollowupList' })

/** 跟进记录（需求 03-04 3.1，T1）：默认近 30 天；只有记录人 24 小时内可以编辑、删除 */
const router = useRouter()
const dialogRef = ref<InstanceType<typeof FollowupDialog>>()
type Query = Omit<FollowupQuery, 'pageNo' | 'pageSize' | 'dateFrom' | 'dateTo'> & { dates?: string[] }
function last30(): string[] {
  const from = new Date()
  from.setDate(from.getDate() - 30)
  return [toDateString(from), toDateString(new Date())]
}
function toParams(q: Query) {
  const { dates, ...rest } = q
  return { ...rest, dateFrom: rest.pendingOnly ? undefined : dates?.[0], dateTo: rest.pendingOnly ? undefined : dates?.[1], pendingOnly: rest.pendingOnly || undefined }
}
const { query, list, total, loading, load, search, reset } = useListPage<Query, FollowupRow>({
  api: (q) => followupApi.page(toParams(q) as FollowupQuery),
  defaultQuery: () => ({ dates: last30() }),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'ownerId', label: '记录人', type: 'user' },
  { prop: 'followupType', label: '方式', type: 'dict', dictType: 'crm_followup_type' },
  { prop: 'dates', label: '跟进时间', type: 'daterange' },
  { prop: 'pendingOnly', label: '仅看待跟进', type: 'slot' }
]
const columns: TableColumn<FollowupRow>[] = [
  { prop: 'followupAt', label: '跟进时间', width: 150, type: 'datetime' },
  { prop: 'customerShortName', label: '客户', width: 130, type: 'link', onClick: (r) => router.push(`/crm/customer/${r.customerId}`) },
  { prop: 'contactName', label: '联系人', width: 100 },
  { prop: 'followupType', label: '方式', width: 90, type: 'dict', dictType: 'crm_followup_type' },
  { prop: 'subject', label: '主题', minWidth: 160 },
  { prop: 'content', label: '内容', minWidth: 220 },
  { prop: 'opportunityName', label: '商机', width: 150 },
  { prop: 'nextFollowupAt', label: '下次跟进', width: 110, slot: true },
  { prop: 'ownerName', label: '记录人', width: 90 }
]
async function remove(r: FollowupRow) {
  await followupApi.remove(r.id)
  ElMessage.success('删除成功')
  load()
}
const asRow = (r: unknown) => r as FollowupRow
</script>

<template>
  <ErpPage description="记录每次拜访、电话、邮件等沟通，并设置下次跟进提醒；记录超过 24 小时后只读">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-customerId><CustomerSelect v-model="query.customerId" placeholder="全部" class="w200" /></template>
          <template #field-pendingOnly><el-switch v-model="query.pendingOnly" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="crm.followup" :actions-width="120" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'crm:followup:create'" type="primary" icon="Plus" @click="dialogRef?.open()">新增跟进</el-button>
        </template>
        <template #col-nextFollowupAt="{ row }">
          <span :class="{ 'text-danger': asRow(row).nextDue }">{{ asRow(row).nextFollowupAt ?? '' }}</span>
        </template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '编辑', permission: 'crm:followup:update', visible: asRow(row).editable, handler: () => dialogRef?.open(asRow(row)) },
            { label: '删除', permission: 'crm:followup:delete', danger: true, visible: asRow(row).editable, confirm: '确定删除这条跟进记录吗？', handler: () => remove(asRow(row)) }
          ]" />
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
    <FollowupDialog ref="dialogRef" @saved="load" />
  </ErpPage>
</template>
