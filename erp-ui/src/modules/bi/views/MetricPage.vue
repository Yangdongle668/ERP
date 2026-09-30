<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { type Metric, metricApi, TOPIC_LABEL, UNIT_LABEL } from '../api/bi'
import { formatDateTime } from '@/utils/format'

/** 指标库（需求 13-01 第 4 节）：计算逻辑在代码中注册，这里只能修改展示名称、补充说明、负责人 */
const list = ref<Metric[]>([])
const loading = ref(false)
const keyword = ref('')
const topic = ref('')

async function load() {
  loading.value = true
  try {
    list.value = await metricApi.list()
  } finally {
    loading.value = false
  }
}
onMounted(load)

const dimsText = (m: Metric) => m.dimensions.map((d) => d.label).join('、')
const filtered = () => list.value.filter((m) => (!topic.value || m.topic === topic.value)
  && (!keyword.value || m.code.includes(keyword.value) || m.name.includes(keyword.value) || m.defaultName.includes(keyword.value)))

const dialog = reactive({ visible: false, saving: false, code: '', title: '', displayName: '', description: '', ownerName: '' })
function edit(m: Metric) {
  Object.assign(dialog, { visible: true, code: m.code, title: m.defaultName, displayName: m.name === m.defaultName ? '' : m.name,
    description: m.remark ?? '', ownerName: m.ownerName ?? '' })
}
async function save() {
  dialog.saving = true
  try {
    await metricApi.update(dialog.code, { displayName: dialog.displayName, description: dialog.description, ownerName: dialog.ownerName })
    ElMessage.success('已保存')
    dialog.visible = false
    await load()
  } finally {
    dialog.saving = false
  }
}
</script>

<template>
  <ErpPage description="统一定义指标口径；报表、驾驶舱与 AI 问数都按指标库取数。计算逻辑由系统内置，不能在页面修改">
    <ErpPanel>
      <template #filter>
        <div class="bi-toolbar">
          <el-input v-model="keyword" placeholder="编码 / 名称" clearable prefix-icon="Search" class="bi-toolbar__kw" />
          <el-select v-model="topic" placeholder="专题" clearable class="bi-toolbar__sel">
            <el-option v-for="(l, k) in TOPIC_LABEL" :key="k" :value="k" :label="l" />
          </el-select>
        </div>
      </template>
      <el-table v-loading="loading" :data="filtered()" row-key="code">
        <el-table-column prop="code" label="编码" width="200" />
        <el-table-column label="名称" width="150">
          <template #default="{ row }">
            {{ row.name }}<div v-if="row.name !== row.defaultName" class="bi-sub">{{ row.defaultName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="专题" width="80"><template #default="{ row }">{{ TOPIC_LABEL[row.topic] ?? row.topic }}</template></el-table-column>
        <el-table-column label="口径" min-width="280">
          <template #default="{ row }">
            {{ row.description }}<div v-if="row.remark" class="bi-sub">{{ row.remark }}</div>
          </template>
        </el-table-column>
        <el-table-column label="单位" width="80"><template #default="{ row }">{{ UNIT_LABEL[(row as Metric).unit] }}</template></el-table-column>
        <el-table-column prop="source" label="来源" width="130" />
        <el-table-column label="可用维度" min-width="180"><template #default="{ row }">{{ dimsText(row as Metric) }}</template></el-table-column>
        <el-table-column prop="permission" label="权限" width="150" />
        <el-table-column label="敏感" width="70" align="center"><template #default="{ row }">{{ row.sensitive ? '是' : '' }}</template></el-table-column>
        <el-table-column prop="ownerName" label="负责人" width="100" />
        <el-table-column label="最近计算" width="150"><template #default="{ row }">{{ formatDateTime(row.lastCalculatedAt, true) }}</template></el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }"><el-button link type="primary" @click="edit(row as Metric)">编辑说明</el-button></template>
        </el-table-column>
      </el-table>
    </ErpPanel>

    <el-dialog v-model="dialog.visible" :title="`编辑说明：${dialog.title}`" width="520px">
      <el-form label-width="84px">
        <el-form-item label="展示名称"><el-input v-model="dialog.displayName" :placeholder="dialog.title" maxlength="64" /></el-form-item>
        <el-form-item label="补充说明"><el-input v-model="dialog.description" type="textarea" :rows="4" maxlength="1000" show-word-limit /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="dialog.ownerName" maxlength="64" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.bi-toolbar { display: flex; gap: var(--erp-space-3); }
.bi-toolbar__kw { width: 220px; }
.bi-toolbar__sel { width: 140px; }
.bi-sub { color: var(--erp-color-text-tertiary); font-size: var(--erp-font-size-caption); }
</style>
