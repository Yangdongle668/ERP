<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { followupApi, type FollowupRow } from '../api/crm'
import FollowupDialog from './FollowupDialog.vue'

/** 跟进记录时间线（客户详情“跟进记录”页签、商机详情）；顶部 [新增跟进] 固定客户/商机 */
const props = defineProps<{ customerId?: string; opportunityId?: string }>()
const me = useUserStore()
const list = ref<FollowupRow[]>([])
const loading = ref(false)
const dialogRef = ref<InstanceType<typeof FollowupDialog>>()

async function load() {
  loading.value = true
  try {
    list.value = (await followupApi.page({ customerId: props.customerId, opportunityId: props.opportunityId, pageNo: 1, pageSize: 100 })).list
  } finally {
    loading.value = false
  }
}
async function remove(r: FollowupRow) {
  await followupApi.remove(r.id)
  ElMessage.success('删除成功')
  load()
}
watch(() => [props.customerId, props.opportunityId], load)
onMounted(load)
defineExpose({ load })
</script>

<template>
  <div v-loading="loading">
    <div class="bar">
      <el-button v-if="me.hasPermission('crm:followup:create') && customerId" type="primary" icon="Plus"
                 @click="dialogRef?.open(undefined, { customerId, opportunityId })">新增跟进</el-button>
    </div>
    <el-timeline v-if="list.length">
      <el-timeline-item v-for="f in list" :key="f.id" :timestamp="f.followupAt.slice(0, 16)" placement="top">
        <div class="item">
          <div class="head">
            <DictTag type="crm_followup_type" :value="f.followupType" />
            <span class="subject">{{ f.subject }}</span>
            <span class="text-muted">{{ f.ownerName }}{{ f.contactName ? ` · 联系人 ${f.contactName}` : '' }}{{ f.opportunityName ? ` · 商机 ${f.opportunityName}` : '' }}</span>
            <span class="spacer" />
            <template v-if="f.editable">
              <el-button link type="primary" @click="dialogRef?.open(f)">编辑</el-button>
              <el-popconfirm title="确定删除这条跟进吗？" @confirm="remove(f)">
                <template #reference><el-button link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
          </div>
          <div class="content">{{ f.content }}</div>
          <div v-if="f.nextFollowupAt" class="next" :class="{ 'text-danger': f.nextDue }">
            下次跟进 {{ f.nextFollowupAt }}{{ f.nextPlan ? `：${f.nextPlan}` : '' }}
          </div>
          <div v-if="f.fileCount" class="text-muted">附件 {{ f.fileCount }} 个</div>
        </div>
      </el-timeline-item>
    </el-timeline>
    <ErpEmpty v-else compact description="还没有跟进记录" />
    <FollowupDialog ref="dialogRef" @saved="load" />
  </div>
</template>

<style scoped>
.bar { margin-bottom: var(--erp-space-4); }
.item { display: flex; flex-direction: column; gap: var(--erp-space-1); }
.head { display: flex; align-items: center; gap: var(--erp-space-2); }
.subject { font-weight: var(--erp-font-weight-medium); }
.spacer { flex: 1; }
.content { white-space: pre-wrap; color: var(--erp-color-text); }
.next { font-size: var(--erp-font-size-secondary); }
</style>
