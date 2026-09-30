<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { NOTICE_STATUS, noticeApi, type NoticeReader, type NoticeRow, type NoticeSave } from '../api/workbench'

defineOptions({ name: 'WbNoticeList' })

/**
 * 公告管理（需求 02-03 3.2，wb:notice:manage）：新建 / 编辑草稿、发布（可定时）、撤回、阅读情况。
 * 内容为 HTML 富文本，保存时服务端过滤脚本、事件属性与 iframe（WB-NTC-R01）；重要公告在用户登录后未读时弹窗。
 */
type Query = { keyword?: string; status?: string }
const { query, list, total, loading, load, search, reset } = useListPage<Query, NoticeRow>({ api: (q) => noticeApi.page(q as never) })
const fields: SearchField[] = [
  { prop: 'keyword', label: '标题' },
  { prop: 'status', label: '状态', type: 'select', options: Object.entries(NOTICE_STATUS).map(([value, s]) => ({ value, label: s.label })) }
]
const columns: TableColumn<NoticeRow>[] = [
  { prop: 'title', label: '标题', minWidth: 220, type: 'link', onClick: (r) => edit(r) },
  { prop: 'scope', label: '范围', width: 160, formatter: (r) => (r.scope === 'ALL' ? '全员' : r.deptNames ?? '部门') },
  { prop: 'important', label: '重要', width: 60, type: 'bool' },
  { prop: 'publishAt', label: '发布时间', width: 150, type: 'datetime' },
  { prop: 'expireAt', label: '过期时间', width: 150, type: 'datetime' },
  { key: 'reads', label: '阅读 / 应读', width: 100, formatter: (r) => `${r.readCount} / ${r.targetCount}` },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: NOTICE_STATUS },
  { prop: 'publisherName', label: '发布人', width: 90 }
]

const visible = ref(false)
const saving = ref(false)
const editId = ref<string>()
const formRef = ref<FormInstance>()
const blank = (): NoticeSave => ({ title: '', content: '', scope: 'ALL', deptIds: [], important: false, fileIds: [] })
const form = ref<NoticeSave>(blank())
const preview = ref(false)
const rules: FormRules = {
  title: [{ required: true, message: '请填写标题', trigger: 'blur' }],
  content: [{ required: true, message: '请填写内容', trigger: 'blur' }]
}
function create() {
  editId.value = undefined
  form.value = blank()
  preview.value = false
  visible.value = true
}
async function edit(r: NoticeRow) {
  const d = await noticeApi.get(r.id)
  editId.value = r.id
  form.value = { title: d.header.title, content: d.content, scope: d.header.scope, deptIds: d.header.deptIds ?? [], important: d.header.important,
    publishAt: d.header.publishAt, expireAt: d.header.expireAt, fileIds: [] }
  preview.value = d.header.status !== 'DRAFT'
  visible.value = true
}
async function save(publish: boolean) {
  await formRef.value?.validate()
  if (form.value.scope === 'DEPT' && !form.value.deptIds?.length) return ElMessage.warning('按部门发布时请选择部门')
  saving.value = true
  try {
    const id = editId.value ? (await noticeApi.update(editId.value, form.value), editId.value) : await noticeApi.create(form.value)
    if (publish) await noticeApi.publish(id)
    ElMessage.success(publish ? '已发布' : '已保存')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}
async function publish(r: NoticeRow) {
  await noticeApi.publish(r.id)
  ElMessage.success('已发布')
  load()
}
async function withdraw(r: NoticeRow) {
  await ElMessageBox.confirm(`撤回公告「${r.title}」？撤回后首页不再显示。`, '撤回', { type: 'warning' })
  await noticeApi.withdraw(r.id)
  ElMessage.success('已撤回')
  load()
}
async function remove(r: NoticeRow) {
  await ElMessageBox.confirm(`删除公告「${r.title}」？`, '删除', { type: 'warning' })
  await noticeApi.remove(r.id)
  ElMessage.success('已删除')
  load()
}
const readersVisible = ref(false)
const readers = ref<NoticeReader[]>([])
async function showReaders(r: NoticeRow) {
  readers.value = await noticeApi.readers(r.id)
  readersVisible.value = true
}
</script>

<template>
  <ErpPage description="公告发布给全员或指定部门（含下级部门）；可设置发布时间与过期时间，重要公告在用户登录后未读时弹窗提示">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset" />
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="wb.notice" @refresh="load">
        <template #toolbar><el-button type="primary" icon="Plus" @click="create">新建公告</el-button></template>
        <template #actions="{ row }">
          <el-button link type="primary" @click="edit(row as NoticeRow)">{{ row.status === 'PUBLISHED' ? '查看' : '编辑' }}</el-button>
          <el-button v-if="row.status !== 'PUBLISHED'" link type="primary" @click="publish(row as NoticeRow)">发布</el-button>
          <el-button v-if="row.status === 'PUBLISHED'" link @click="withdraw(row as NoticeRow)">撤回</el-button>
          <el-button v-if="row.status !== 'DRAFT'" link @click="showReaders(row as NoticeRow)">阅读情况</el-button>
          <el-button v-if="row.status !== 'PUBLISHED'" link type="danger" @click="remove(row as NoticeRow)">删除</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="visible" :title="editId ? '编辑公告' : '新建公告'" width="760px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标题" prop="title"><el-input v-model="form.title" maxlength="128" show-word-limit /></el-form-item>
        <el-form-item label="范围" required>
          <el-radio-group v-model="form.scope">
            <el-radio value="ALL">全员</el-radio>
            <el-radio value="DEPT">指定部门</el-radio>
          </el-radio-group>
          <OrgTreeSelect v-if="form.scope === 'DEPT'" v-model="form.deptIds" multiple class="depts" />
        </el-form-item>
        <el-form-item label="重要"><el-switch v-model="form.important" /><span class="hint">登录后未读弹窗提示</span></el-form-item>
        <el-form-item label="发布时间">
          <el-date-picker v-model="form.publishAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="默认立即" />
          <span class="label">过期时间</span>
          <el-date-picker v-model="form.expireAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="不过期" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <div class="content-bar">
            <el-radio-group v-model="preview" size="small">
              <el-radio-button :value="false">编辑</el-radio-button>
              <el-radio-button :value="true">预览</el-radio-button>
            </el-radio-group>
            <span class="hint">支持 HTML 格式（段落、加粗、列表、表格、链接、图片）</span>
          </div>
          <el-input v-if="!preview" v-model="form.content" type="textarea" :rows="12" />
          <div v-else class="preview" v-html="form.content" />
        </el-form-item>
        <el-form-item label="附件"><AttachmentUpload v-model="form.fileIds" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button :loading="saving" @click="save(false)">保存</el-button>
        <el-button type="primary" :loading="saving" @click="save(true)">保存并发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="readersVisible" title="阅读情况" width="560px">
      <el-table :data="readers" max-height="420">
        <el-table-column prop="userName" label="用户" />
        <el-table-column prop="deptName" label="部门" />
        <el-table-column prop="readAt" label="阅读时间" width="170" />
      </el-table>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.depts { width: 360px; margin-left: var(--erp-space-3); }
.hint { margin-left: var(--erp-space-2); color: var(--erp-color-text-tertiary); font-size: var(--erp-font-size-caption); }
.label { margin: 0 var(--erp-space-2) 0 var(--erp-space-4); color: var(--erp-color-text-secondary); }
.content-bar { display: flex; align-items: center; width: 100%; margin-bottom: var(--erp-space-2); }
.preview { width: 100%; min-height: 200px; padding: var(--erp-space-3); border: 1px solid var(--erp-color-border); border-radius: var(--erp-radius-control); }
</style>
