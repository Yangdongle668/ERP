<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { ENABLE_STATUS } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { LANGUAGE_OPTIONS, printApi, type PrintBiz, type TemplateQuery, type TemplateRow } from '../api/print'

defineOptions({ name: 'SystemPrintTemplateList' })

/** 打印模板（需求 01-09 第 3.1 节）：模板为 HTML + Handlebars，内置模板只读，可复制后修改 */
const router = useRouter()
const bizList = ref<PrintBiz[]>([])

const { query, list, total, loading, load, search, reset } = useListPage<Omit<TemplateQuery, 'pageNo' | 'pageSize'>, TemplateRow>({
  api: (q) => printApi.page(q as TemplateQuery),
  refreshOnActivated: true
})

const fields: SearchField[] = [
  { prop: 'bizType', label: '单据类型', type: 'slot' },
  { prop: 'language', label: '语言', type: 'select', options: LANGUAGE_OPTIONS },
  { prop: 'status', label: '状态', type: 'select', options: [{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }] }
]

const columns: TableColumn<TemplateRow>[] = [
  { prop: 'bizTypeName', label: '单据类型', width: 140 },
  { prop: 'name', label: '模板名称', minWidth: 200, slot: true },
  { prop: 'language', label: '语言', width: 90, type: 'enum', options: LANGUAGE_OPTIONS },
  { prop: 'paper', label: '纸张', width: 110, type: 'dict', dictType: 'sys_print_paper' },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: ENABLE_STATUS },
  { prop: 'updatedAt', label: '修改时间', width: 150, type: 'datetime' }
]

const modules = computed(() => [...new Set(bizList.value.map((b) => b.moduleName))])

async function action(t: TemplateRow, act: 'copy' | 'setDefault' | 'enable' | 'disable' | 'remove') {
  if (act === 'copy') {
    const id = await printApi.copy(t.id)
    ElMessage.success('已复制，可在副本上修改')
    router.push(`/system/print-template/${id}/edit`)
    return
  }
  await printApi[act](t.id)
  ElMessage.success(act === 'remove' ? '删除成功' : '操作成功')
  load()
}

// ---------- 新建 ----------
const newVisible = ref(false)
const newForm = ref<{ bizType?: string; language: string; from: string }>({ language: 'zh-CN', from: 'BUILTIN' })
function openNew() {
  newForm.value = { bizType: query.bizType as string | undefined ?? bizList.value[0]?.bizType, language: 'zh-CN', from: 'BUILTIN' }
  newVisible.value = true
}
async function confirmNew() {
  const f = newForm.value
  if (!f.bizType) return ElMessage.warning('请选择单据类型')
  let from: string | undefined
  if (f.from === 'BUILTIN') {
    const page = await printApi.page({ bizType: f.bizType, language: f.language, pageNo: 1, pageSize: 50 })
    from = page.list.find((t) => t.isBuiltin)?.id ?? page.list.find((t) => t.isDefault)?.id
    if (!from) ElMessage.info('该单据类型没有内置模板，将从空白模板开始')
  }
  newVisible.value = false
  router.push({ path: '/system/print-template/new', query: { bizType: f.bizType, language: f.language, from } })
}

const asRow = (r: unknown) => r as TemplateRow

onMounted(async () => {
  bizList.value = await printApi.bizList().catch(() => [])
})
</script>

<template>
  <ErpPage description="单据打印模板（HTML + Handlebars）；同一单据类型、语言只有一个默认模板，打印按钮直接使用默认模板">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-bizType>
            <el-select v-model="query.bizType" placeholder="全部" clearable filterable class="w200">
              <el-option-group v-for="m in modules" :key="m" :label="m">
                <el-option v-for="b in bizList.filter((x) => x.moduleName === m)" :key="b.bizType" :value="b.bizType" :label="b.name" />
              </el-option-group>
            </el-select>
          </template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="system.print-template" :actions-width="220"
                :empty-text="bizList.length ? '暂无打印模板' : '还没有模块声明可打印的单据类型'" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'system:print:create'" type="primary" icon="Plus" :disabled="!bizList.length" @click="openNew">新建模板</el-button>
        </template>
        <template #col-name="{ row }">
          <span class="name-cell">
            <el-link type="primary" underline="never" @click="router.push(`/system/print-template/${asRow(row).id}/edit`)">{{ asRow(row).name }}</el-link>
            <ErpBadge v-if="asRow(row).isDefault" type="primary" :dot="false">默认</ErpBadge>
            <ErpBadge v-if="asRow(row).isBuiltin" :dot="false">内置</ErpBadge>
          </span>
        </template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '编辑', permission: 'system:print:update', visible: !asRow(row).isBuiltin, handler: () => router.push(`/system/print-template/${asRow(row).id}/edit`) },
            { label: '查看', visible: asRow(row).isBuiltin, handler: () => router.push(`/system/print-template/${asRow(row).id}/edit`) },
            { label: '复制', permission: 'system:print:create', handler: () => action(asRow(row), 'copy') },
            { label: '设为默认', permission: 'system:print:update', visible: !asRow(row).isDefault && asRow(row).status === 'ENABLED', handler: () => action(asRow(row), 'setDefault') },
            { label: '停用', permission: 'system:print:update', visible: !asRow(row).isDefault && asRow(row).status === 'ENABLED', handler: () => action(asRow(row), 'disable') },
            { label: '启用', permission: 'system:print:update', visible: asRow(row).status === 'DISABLED', handler: () => action(asRow(row), 'enable') },
            { label: '删除', permission: 'system:print:delete', danger: true, visible: !asRow(row).isBuiltin && !asRow(row).isDefault,
              confirm: `确定删除模板「${asRow(row).name}」吗？删除后不可恢复。`, handler: () => action(asRow(row), 'remove') }
          ]" />
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="newVisible" title="新建打印模板" width="480px" :close-on-click-modal="false" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="单据类型" required>
          <el-select v-model="newForm.bizType" filterable>
            <el-option-group v-for="m in modules" :key="m" :label="m">
              <el-option v-for="b in bizList.filter((x) => x.moduleName === m)" :key="b.bizType" :value="b.bizType" :label="b.name" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="语言" required>
          <el-radio-group v-model="newForm.language"><el-radio v-for="o in LANGUAGE_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item label="起始内容">
          <el-radio-group v-model="newForm.from"><el-radio value="BUILTIN">复制内置模板</el-radio><el-radio value="BLANK">空白模板</el-radio></el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmNew">下一步</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.name-cell { display: inline-flex; align-items: center; gap: 8px; }
</style>
