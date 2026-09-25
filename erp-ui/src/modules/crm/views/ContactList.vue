<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { customerApi, type ContactRow } from '../api/crm'

defineOptions({ name: 'CrmContactList' })

/** 联系人查询（需求 03-01 3.5，T1）：跨客户查询，按客户数据权限过滤 */
const router = useRouter()
type Query = { name?: string; email?: string; phone?: string; customerId?: string; role?: string; status?: string }
const { query, list, total, loading, load, search, reset } = useListPage<Query, ContactRow>({
  api: (q) => customerApi.contacts(q),
  defaultQuery: () => ({ status: 'ACTIVE' })
})
const fields: SearchField[] = [
  { prop: 'name', label: '姓名' },
  { prop: 'email', label: '邮箱' },
  { prop: 'phone', label: '电话/手机' },
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'role', label: '角色', type: 'dict', dictType: 'crm_contact_role' },
  { prop: 'status', label: '状态', type: 'select', options: [{ value: 'ACTIVE', label: '在职' }, { value: 'LEFT', label: '已离职' }] }
]
const columns: TableColumn<ContactRow>[] = [
  { prop: 'name', label: '姓名', width: 120 },
  { prop: 'customerShortName', label: '客户', width: 140, type: 'link', onClick: (r) => router.push(`/crm/customer/${r.customerId}`) },
  { prop: 'title', label: '职位', width: 120 },
  { prop: 'role', label: '角色', width: 90, type: 'dict', dictType: 'crm_contact_role' },
  { prop: 'email', label: '邮箱', minWidth: 180 },
  { prop: 'mobile', label: '手机', width: 130 },
  { prop: 'phone', label: '电话', width: 130 },
  { prop: 'isPrimary', label: '主联系人', width: 80, type: 'bool' },
  { prop: 'status', label: '状态', width: 80, formatter: (r) => (r.status === 'LEFT' ? '已离职' : '在职') }
]
</script>

<template>
  <ErpPage description="跨客户查询联系人；联系人在客户档案中维护">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-customerId><CustomerSelect v-model="query.customerId" placeholder="全部" class="w200" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="crm.contact" @refresh="load" />
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
