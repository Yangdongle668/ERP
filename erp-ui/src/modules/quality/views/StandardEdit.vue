<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { refApi, type CategoryNode } from '@/api/refs'
import {
  INSPECT_TYPE, LEVELS, STD_STATUS, basicApi, standardApi, type ItemLibRow, type SamplingRow, type StandardDetail, type StandardItem
} from '../api/quality'

defineOptions({ name: 'QcStandardEdit' })

/** 检验标准编辑（需求 10-01 5.1，T4）：单头 + 检验项目；生效的标准只读（只能新建版本） */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => (route.params.id === 'new' ? undefined : String(route.params.id)))
const d = ref<StandardDetail>()
const form = ref<{ name?: string; inspectType: string; scopeType: string; materialId?: string; categoryId?: string; operation?: string; samplingPlanId?: string; remark?: string }>({
  inspectType: 'IQC', scopeType: 'MATERIAL'
})
const items = ref<StandardItem[]>([])
const libs = ref<ItemLibRow[]>([])
const plans = ref<SamplingRow[]>([])
const categories = ref<CategoryNode[]>([])
const catProps = { value: 'id', label: 'name', children: 'children' }
const readonly = computed(() => !!d.value && d.value.status !== 'DRAFT')
const saving = ref(false)
const typeOptions = Object.entries(INSPECT_TYPE).filter(([k]) => k !== 'RECHECK')

onMounted(async () => {
  const [l, p, c] = await Promise.all([basicApi.items({ pageNo: 1, pageSize: 500, status: 'ENABLED' }), basicApi.enabledPlans(), refApi.categoryTree()])
  categories.value = c
  libs.value = l.list
  plans.value = p
  if (id.value) {
    d.value = await standardApi.get(id.value)
    tabs.setTitle(tabKeyOf(route), `${d.value.code} V${d.value.stdVersion}`)
    form.value = { name: d.value.name, inspectType: d.value.inspectType, scopeType: d.value.scopeType, materialId: d.value.materialId,
      categoryId: d.value.categoryId, operation: d.value.operation, samplingPlanId: d.value.samplingPlanId, remark: d.value.remark }
    items.value = d.value.items.map((i) => ({ ...i }))
  } else {
    form.value.samplingPlanId = plans.value[0]?.id
  }
})
const lib = (libId?: string) => libs.value.find((x) => x.id === libId)
function onLib(i: StandardItem) {
  const l = lib(i.libItemId)
  if (!l) return
  i.name = l.name
  i.itemType = l.itemType
  i.unit = l.unit
  i.defectLevel = l.defectLevel
}
function add() {
  items.value.push({ isKey: false })
}
async function save() {
  if (!form.value.name) return ElMessage.warning('请填写名称')
  if (!items.value.length) return ElMessage.warning('请至少添加一个检验项目')
  saving.value = true
  try {
    const body = { ...form.value, version: d.value?.version, items: items.value }
    const newId = await standardApi.save(id.value, body)
    ElMessage.success('已保存')
    if (!id.value && newId) router.replace(`/quality/standard/${newId}`)
    else if (id.value) d.value = await standardApi.get(id.value)
  } finally {
    saving.value = false
  }
}
async function activate() {
  await save()
  const target = id.value ?? String(route.params.id)
  await standardApi.activate(target)
  ElMessage.success('已生效')
  d.value = await standardApi.get(target)
}
</script>

<template>
  <ErpPage :title="d ? `检验标准 ${d.code} V${d.stdVersion}` : '新建检验标准'" back="/quality/standard">
    <ErpPanel title="基本信息">
      <template #extra><StatusTag v-if="d" :value="d.status" :map="STD_STATUS" /></template>
      <el-form label-width="110px" :disabled="readonly" class="form">
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="128" /></el-form-item>
        <el-form-item label="检验类型" required>
          <el-select v-model="form.inspectType"><el-option v-for="[k, v] in typeOptions" :key="k" :value="k" :label="v" /></el-select>
        </el-form-item>
        <el-form-item label="适用范围" required>
          <el-radio-group v-model="form.scopeType"><el-radio value="MATERIAL">物料</el-radio><el-radio value="CATEGORY">物料类别</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.scopeType === 'MATERIAL'" label="物料" required><MaterialSelect v-model="form.materialId" /></el-form-item>
        <el-form-item v-else label="物料类别">
          <el-tree-select v-model="form.categoryId" :data="categories" node-key="id" :props="catProps" check-strictly filterable
                          clearable placeholder="为空表示通用标准" />
        </el-form-item>
        <el-form-item v-if="form.inspectType === 'IPQC'" label="适用工序"><DictSelect v-model="form.operation" type="eng_operation" placeholder="为空表示所有检验点工序" /></el-form-item>
        <el-form-item label="默认抽样方案" required>
          <el-select v-model="form.samplingPlanId"><el-option v-for="p in plans" :key="p.id" :value="p.id" :label="p.name" /></el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" maxlength="512" /></el-form-item>
      </el-form>
    </ErpPanel>

    <ErpPanel title="检验项目">
      <el-table :data="items" border>
        <el-table-column label="#" type="index" width="50" />
        <el-table-column label="项目" min-width="150">
          <template #default="{ row }">
            <el-select v-model="row.libItemId" filterable :disabled="readonly" @change="onLib(row)">
              <el-option v-for="l in libs" :key="l.id" :value="l.id" :label="`${l.name}（${l.itemType === 'QUANTITATIVE' ? '定量' : '定性'}）`" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="名称" width="120"><template #default="{ row }"><el-input v-model="row.name" :disabled="readonly" /></template></el-table-column>
        <el-table-column label="规格描述" min-width="160"><template #default="{ row }"><el-input v-model="row.spec" :disabled="readonly" /></template></el-table-column>
        <el-table-column label="目标值" width="100">
          <template #default="{ row }"><NumberInput v-if="lib(row.libItemId)?.itemType === 'QUANTITATIVE'" v-model="row.target" :precision="6" :disabled="readonly" /></template>
        </el-table-column>
        <el-table-column label="下限" width="100">
          <template #default="{ row }"><NumberInput v-if="lib(row.libItemId)?.itemType === 'QUANTITATIVE'" v-model="row.lowerLimit" :precision="6" :disabled="readonly" /></template>
        </el-table-column>
        <el-table-column label="上限" width="100">
          <template #default="{ row }"><NumberInput v-if="lib(row.libItemId)?.itemType === 'QUANTITATIVE'" v-model="row.upperLimit" :precision="6" :disabled="readonly" /></template>
        </el-table-column>
        <el-table-column label="等级" width="110">
          <template #default="{ row }">
            <el-select v-model="row.defectLevel" :disabled="readonly"><el-option v-for="l in LEVELS" :key="String(l.value)" :value="l.value" :label="String(l.value)" /></el-select>
          </template>
        </el-table-column>
        <el-table-column label="抽样方案（覆盖）" width="170">
          <template #default="{ row }">
            <el-select v-model="row.samplingPlanId" clearable :disabled="readonly"><el-option v-for="p in plans" :key="p.id" :value="p.id" :label="p.name" /></el-select>
          </template>
        </el-table-column>
        <el-table-column label="关键" width="60" align="center"><template #default="{ row }"><el-checkbox v-model="row.isKey" :disabled="readonly" /></template></el-table-column>
        <el-table-column v-if="!readonly" label="" width="60" align="center">
          <template #default="{ $index }"><el-button link type="danger" @click="items.splice($index, 1)">删除</el-button></template>
        </el-table-column>
      </el-table>
      <el-button v-if="!readonly" class="gap" @click="add">+ 添加项目</el-button>
    </ErpPanel>

    <ErpPanel v-if="d && d.versions.length > 1" title="版本">
      <el-table :data="d.versions">
        <el-table-column label="版本" width="80"><template #default="{ row }">V{{ row.stdVersion }}</template></el-table-column>
        <el-table-column label="名称" prop="name" min-width="160" />
        <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" :map="STD_STATUS" /></template></el-table-column>
        <el-table-column label="更新时间" prop="updatedAt" width="170" />
        <el-table-column label="" width="80">
          <template #default="{ row }"><el-button v-if="row.id !== d.id" link type="primary" @click="router.push(`/quality/standard/${row.id}`)">查看</el-button></template>
        </el-table-column>
      </el-table>
    </ErpPanel>

    <div v-if="!readonly" class="footer">
      <el-button @click="router.push('/quality/standard')">取消</el-button>
      <el-button v-perm="id ? 'qc:standard:update' : 'qc:standard:create'" :loading="saving" @click="save">保存</el-button>
      <el-button v-if="id" v-perm="'qc:standard:approve'" type="primary" :loading="saving" @click="activate">保存并生效</el-button>
    </div>
  </ErpPage>
</template>

<style scoped>
.form { max-width: 720px; }
.gap { margin-top: var(--erp-space-3); }
.footer { display: flex; justify-content: flex-end; gap: var(--erp-space-2); }
</style>
