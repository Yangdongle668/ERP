<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatQty } from '@/utils/format'
import OrderSelect from '../components/OrderSelect.vue'
import { issueApi, MATERIAL_DOC_STATUS, num, type IssueCandidate, type IssueDetail } from '../api/production'

defineOptions({ name: 'MfgIssueEdit' })

/** 领料单编辑（需求 09-03 3.2，T4）：选择生产订单带出未领物料（倒冲物料不需要领料），可按套数计算申请数量 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const saving = ref(false)
const detail = ref<IssueDetail>()

interface Line extends Partial<IssueCandidate> { materialLineId: string; requestQty?: string; remark?: string; selected: boolean }
const form = ref<{ prodOrderId?: string; kitQty?: string; remark?: string; lines: Line[] }>({ lines: [] })
const guard = useLeaveGuard(() => form.value)
const loadingLines = ref(false)

async function loadCandidates() {
  const f = form.value
  if (!f.prodOrderId) {
    f.lines = []
    return
  }
  loadingLines.value = true
  try {
    const rows = await issueApi.candidates(f.prodOrderId, f.kitQty || undefined)
    const keep = new Map(f.lines.map((l) => [l.materialLineId, l]))
    f.lines = rows.map((c) => ({ ...c, remark: keep.get(c.materialLineId)?.remark, selected: keep.get(c.materialLineId)?.selected ?? true }))
  } finally {
    loadingLines.value = false
  }
}

onMounted(async () => {
  if (id.value) {
    const d = await issueApi.get(id.value)
    if (d.status !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的领料单可以修改')
      router.replace(`/production/issue/${id.value}`)
      return
    }
    detail.value = d
    form.value = {
      prodOrderId: d.prodOrderId, kitQty: d.kitQty, remark: d.remark,
      lines: d.lines.map((l) => ({ materialLineId: l.materialLineId, code: l.code, name: l.name, spec: l.spec, uom: l.uom, requiredQty: l.requiredQty,
        issuedQty: l.lineIssuedQty, openQty: l.openQty, availableQty: l.availableQty, requestQty: l.requestQty, remark: l.remark, selected: true }))
    }
    tabs.setTitle(tabKeyOf(route), `编辑领料单 ${d.docNo}`)
  } else if (typeof route.query.prodOrderId === 'string') {
    form.value.prodOrderId = route.query.prodOrderId
    await loadCandidates()
  }
  guard.markClean()
})

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/production/issue/${id.value}` : '/production/issue')
}

async function save(submit: boolean) {
  if (saving.value) return
  const f = form.value
  if (!f.prodOrderId) return ElMessage.warning('请选择生产订单')
  const lines = f.lines.filter((l) => l.selected && num(l.requestQty) > 0)
  if (!lines.length) return ElMessage.warning('请至少选择一行并填写申请数量')
  const data = {
    prodOrderId: f.prodOrderId, kitQty: f.kitQty || undefined, remark: f.remark?.trim() || undefined, version: detail.value?.version,
    lines: lines.map((l) => ({ materialLineId: l.materialLineId, requestQty: l.requestQty!, remark: l.remark?.trim() || undefined }))
  }
  saving.value = true
  try {
    let ids: string[]
    if (id.value) {
      await issueApi.update(id.value, data)
      ids = [id.value]
    } else {
      const r = await issueApi.create(data)
      ids = r.ids
      if (r.ids.length > 1) ElMessage.info(`按发料仓拆分为 ${r.ids.length} 张领料单：${r.docNos.join('、')}`)
    }
    guard.markClean()
    if (submit) {
      for (const x of ids) {
        try {
          await issueApi.submit(x)
        } catch {
          if (!id.value) router.replace(`/production/issue/${x}/edit`)
          return
        }
      }
      ElMessage.success('已提交，等待仓库确认出库')
    } else {
      ElMessage.success('保存成功')
    }
    tabs.remove([tabKeyOf(route)])
    router.push(ids.length === 1 ? `/production/issue/${ids[0]}` : '/production/issue')
  } finally {
    saving.value = false
  }
}

const title = computed(() => (detail.value ? `编辑领料单 ${detail.value.docNo}` : '新建领料单'))
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.status" :map="MATERIAL_DOC_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
      <el-button v-if="me.hasPermission('mfg:issue:submit')" type="primary" :loading="saving" @click="save(true)">提交</el-button>
    </template>

    <ErpPanel title="基本信息">
      <el-form label-width="100px">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12">
            <el-form-item label="生产订单" required><OrderSelect v-model="form.prodOrderId" :disabled="!!id" @update:model-value="loadCandidates" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="按套数">
              <div class="kit">
                <QtyInput v-model="form.kitQty" placeholder="为空按未领数量" />
                <el-button :disabled="!form.prodOrderId" @click="loadCandidates">计算</el-button>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </ErpPanel>

    <ErpPanel title="领料明细" description="只显示发料方式为“领料”且有未领数量的物料；申请数量超过未领（含允许超领比例）需走超领">
      <el-table v-loading="loadingLines" :data="form.lines">
        <el-table-column label="" width="50"><template #default="{ row }"><el-checkbox v-model="row.selected" /></template></el-table-column>
        <el-table-column label="物料" min-width="220"><template #default="{ row }">{{ row.code }} {{ row.name }}</template></el-table-column>
        <el-table-column prop="spec" label="规格" width="130" show-overflow-tooltip />
        <el-table-column label="应领" width="100" align="right"><template #default="{ row }">{{ formatQty(row.requiredQty) }}</template></el-table-column>
        <el-table-column label="已领" width="100" align="right"><template #default="{ row }">{{ formatQty(row.issuedQty) }}</template></el-table-column>
        <el-table-column label="未领" width="100" align="right"><template #default="{ row }">{{ formatQty(row.openQty) }}</template></el-table-column>
        <el-table-column label="在途申请" width="100" align="right"><template #default="{ row }">{{ row.pendingQty ? formatQty(row.pendingQty) : '-' }}</template></el-table-column>
        <el-table-column label="可用库存" width="100" align="right">
          <template #default="{ row }"><span :class="{ 'text-danger': num(row.availableQty) < num(row.requestQty) }">{{ formatQty(row.availableQty) }}</span></template>
        </el-table-column>
        <el-table-column label="申请数量" width="140"><template #default="{ row }"><QtyInput v-model="row.requestQty" :uom="row.uom" /></template></el-table-column>
        <el-table-column prop="uom" label="单位" width="60" />
        <el-table-column prop="warehouseName" label="发料仓" width="110" />
        <el-table-column label="备注" min-width="120"><template #default="{ row }"><el-input v-model="row.remark" maxlength="256" /></template></el-table-column>
        <template #empty><ErpEmpty compact :description="form.prodOrderId ? '该订单没有需要领料的物料' : '请先选择生产订单'" /></template>
      </el-table>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.kit { display: flex; gap: var(--erp-space-2); width: 100%; }
</style>
