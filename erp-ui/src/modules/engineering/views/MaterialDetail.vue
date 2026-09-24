<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { DocAction } from '@/components'
import { fetchBlob } from '@/api/http'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatPrice, formatQty } from '@/utils/format'
import {
  labelOf, materialApi, ISSUE_RULE_OPTIONS, MATERIAL_STATUS, MATERIAL_TYPE_OPTIONS, ORDER_POLICY_OPTIONS, SOURCE_TYPE_OPTIONS, TRACKING_OPTIONS,
  type BomBrief, type Material, type MaterialSettings
} from '../api/material'
import { BOM_STATUS } from '../api/bom'

defineOptions({ name: 'EngMaterialDetail' })

/** 物料详情（需求 05-02 3.3，T5 无审批；参数要求启用审批时显示审批操作与记录） */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const m = ref<Material>()
const settings = ref<MaterialSettings>({ enableApproval: false, manualCodeAllowed: true, duplicateCheck: 'WARN', canViewCost: false })
const boms = ref<{ asParent: BomBrief[]; asComponent: BomBrief[] }>({ asParent: [], asComponent: [] })
const activeTab = ref('attrs')
const imageUrl = ref('')

async function load() {
  m.value = await materialApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), `物料 ${m.value.code}`)
  boms.value = await materialApi.boms(id.value).catch(() => boms.value)
  if (m.value.imageFileId) imageUrl.value = URL.createObjectURL(await fetchBlob(`/system/files/${m.value.imageFileId}/preview`).catch(() => new Blob()))
}

async function disable() {
  const r = await materialApi.references(id.value)
  const parts: string[] = []
  if (Number(r.stockQty) > 0) parts.push(`当前有库存 ${formatQty(r.stockQty)}`)
  if (r.openDocCount) parts.push(`未完成单据 ${r.openDocCount} 张`)
  if (r.bomCount) parts.push(`被 ${r.bomCount} 个 BOM 使用`)
  await ElMessageBox.confirm(`${parts.length ? `该物料${parts.join('，')}。` : ''}停用后不能在新单据中使用，确定停用吗？`, '停用物料', { type: 'warning' })
  await materialApi.disable(id.value)
  ElMessage.success('已停用')
  load()
}

const actions = computed<DocAction[]>(() => {
  const s = m.value?.status
  return [
    { key: 'copy', label: '复制新建', permission: 'eng:material:create', handler: () => router.push({ path: '/engineering/material/new', query: { from: id.value } }) },
    { key: 'disable', label: '停用', permission: 'eng:material:disable', visible: () => s === 'ENABLED', handler: disable },
    { key: 'edit', label: '编辑', permission: 'eng:material:update', visible: () => s !== 'PENDING', handler: () => router.push(`/engineering/material/${id.value}/edit`) },
    {
      key: 'enable', label: settings.value.enableApproval && s === 'DRAFT' ? '提交启用' : '启用', type: 'primary', permission: 'eng:material:enable',
      visible: () => s === 'DRAFT' || s === 'DISABLED',
      handler: async () => {
        const st = await materialApi.enable(id.value)
        ElMessage.success(st === 'PENDING' ? '已提交启用审批' : '已启用')
        load()
      }
    }
  ]
})

const yes = (v?: boolean) => (v ? '是' : '否')
const pct = (v?: string) => (v === undefined || v === null ? '-' : `${Number((Number(v) * 100).toFixed(4))}%`)
const val = (v?: string | number | null) => (v === undefined || v === null || v === '' ? '-' : String(v))
const qty = (v?: string) => (v === undefined || v === null ? '-' : formatQty(v))

function openBom(b: BomBrief) {
  router.push(`/engineering/bom/${b.bomId}`)
}

onMounted(async () => {
  settings.value = await materialApi.settings().catch(() => settings.value)
  await load()
})
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="m ? `${m.code} ${m.name}` : '物料'" :status="m?.status" :status-map="MATERIAL_STATUS" :actions="actions"
                     @back="router.push('/engineering/material')">
        <template #actions-prefix>
          <ApprovalActions v-if="m && settings.enableApproval" biz-type="ENG_MATERIAL" :biz-id="id" @changed="load" />
        </template>
      </DocPageHeader>
    </template>

    <template v-if="m">
      <ErpPanel>
        <div class="head">
          <img v-if="imageUrl" :src="imageUrl" alt="物料图片" class="image" />
          <el-descriptions :column="3" class="head-desc">
            <el-descriptions-item label="物料类别">{{ m.categoryName }}（{{ m.categoryCode }}）</el-descriptions-item>
            <el-descriptions-item label="物料类型">{{ labelOf(MATERIAL_TYPE_OPTIONS, m.materialType) }}</el-descriptions-item>
            <el-descriptions-item label="基本单位">{{ m.baseUom }}</el-descriptions-item>
            <el-descriptions-item label="规格型号" :span="3">{{ val(m.spec) }}</el-descriptions-item>
            <el-descriptions-item label="英文名称">{{ val(m.nameEn) }}</el-descriptions-item>
            <el-descriptions-item label="图号 / 版本">{{ val(m.drawingNo) }} / {{ val(m.revision) }}</el-descriptions-item>
            <el-descriptions-item label="品牌">{{ val(m.brand) }}</el-descriptions-item>
            <el-descriptions-item label="制造商">{{ val(m.manufacturer) }}</el-descriptions-item>
            <el-descriptions-item label="制造商料号">{{ val(m.mpn) }}</el-descriptions-item>
            <el-descriptions-item label="海关编码">{{ val(m.hsCode) }}</el-descriptions-item>
            <el-descriptions-item label="创建">{{ m.createdByName ?? '-' }} {{ m.createdAt }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ m.updatedAt }}</el-descriptions-item>
            <el-descriptions-item label="备注">{{ val(m.remark) }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane label="属性" name="attrs">
            <div class="group-title">计划属性</div>
            <el-descriptions :column="3" border>
              <el-descriptions-item label="取得方式">{{ labelOf(SOURCE_TYPE_OPTIONS, m.sourceType) }}</el-descriptions-item>
              <el-descriptions-item label="提前期">{{ m.leadTimeDays }} 天</el-descriptions-item>
              <el-descriptions-item label="计划员">{{ val(m.plannerName) }}</el-descriptions-item>
              <el-descriptions-item label="安全库存">{{ qty(m.safetyStock) }}</el-descriptions-item>
              <el-descriptions-item label="最高库存">{{ qty(m.maxStock) }}</el-descriptions-item>
              <el-descriptions-item label="批量规则">{{ labelOf(ORDER_POLICY_OPTIONS, m.orderPolicy) }}
                <template v-if="m.orderPolicy === 'FIXED_QTY'">（{{ qty(m.fixedLotQty) }}）</template>
                <template v-if="m.orderPolicy === 'PERIOD'">（{{ m.periodDays }} 天）</template>
              </el-descriptions-item>
              <el-descriptions-item label="MOQ">{{ qty(m.moq) }}</el-descriptions-item>
              <el-descriptions-item label="MPQ">{{ qty(m.mpq) }}</el-descriptions-item>
              <el-descriptions-item label="低位码">{{ m.lowLevelCode ?? 0 }}</el-descriptions-item>
            </el-descriptions>
            <div class="group-title">采购与库存</div>
            <el-descriptions :column="3" border>
              <el-descriptions-item label="采购员">{{ val(m.buyerName) }}</el-descriptions-item>
              <el-descriptions-item label="采购单位">{{ m.purchaseUom ?? m.baseUom }}</el-descriptions-item>
              <el-descriptions-item label="允许超收">{{ pct(m.overReceivePct) }}</el-descriptions-item>
              <el-descriptions-item label="库存管理">{{ labelOf(TRACKING_OPTIONS, m.tracking) }}</el-descriptions-item>
              <el-descriptions-item label="出库规则">{{ labelOf(ISSUE_RULE_OPTIONS, m.issueRule) }}</el-descriptions-item>
              <el-descriptions-item label="保质期">{{ m.shelfLifeDays ? `${m.shelfLifeDays} 天` : '-' }}</el-descriptions-item>
              <el-descriptions-item label="最小剩余保质期">{{ m.minRemainingLifePct ? pct(m.minRemainingLifePct) : '-' }}</el-descriptions-item>
              <el-descriptions-item label="单位净重">{{ m.unitNetWeight ? `${formatQty(m.unitNetWeight)} kg` : '-' }}</el-descriptions-item>
              <el-descriptions-item label="单位毛重">{{ m.unitGrossWeight ? `${formatQty(m.unitGrossWeight)} kg` : '-' }}</el-descriptions-item>
            </el-descriptions>
            <div class="group-title">质量、财务与销售</div>
            <el-descriptions :column="3" border>
              <el-descriptions-item label="来料检验">{{ yes(m.iqcRequired) }}</el-descriptions-item>
              <el-descriptions-item label="完工检验">{{ yes(m.fqcRequired) }}</el-descriptions-item>
              <el-descriptions-item label="出货检验">{{ yes(m.oqcRequired) }}</el-descriptions-item>
              <el-descriptions-item label="标准成本">{{ settings.canViewCost ? (m.standardCost ? formatPrice(m.standardCost) : '-') : '***' }}</el-descriptions-item>
              <el-descriptions-item label="销售单位">{{ m.salesUom ?? m.baseUom }}</el-descriptions-item>
              <el-descriptions-item label="进项 / 销项税率">{{ pct(m.purchaseTaxRate) }} / {{ pct(m.salesTaxRate) }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <el-tab-pane :label="`单位换算(${m.uoms?.length ?? 0})`" name="uom">
            <el-table :data="m.uoms ?? []">
              <el-table-column prop="uom" label="辅助单位" width="120" />
              <el-table-column label="换算" min-width="200"><template #default="{ row }">1 {{ row.uom }} = {{ Number(row.rate) }} {{ m.baseUom }}</template></el-table-column>
              <el-table-column prop="remark" label="备注" min-width="200" />
              <el-table-column label="已使用" width="80" align="center"><template #default="{ row }">{{ row.used ? '是' : '' }}</template></el-table-column>
              <template #empty><ErpEmpty compact description="未配置辅助单位" /></template>
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`BOM(${boms.asParent.length + boms.asComponent.length})`" name="bom">
            <div class="group-title">本物料的 BOM 版本</div>
            <el-table :data="boms.asParent" @row-click="openBom">
              <el-table-column label="BOM" min-width="180"><template #default="{ row }"><el-link type="primary" underline="never">{{ row.docNo }}</el-link></template></el-table-column>
              <el-table-column label="默认" width="80" align="center"><template #default="{ row }"><ErpBadge v-if="row.isDefault" type="success">默认</ErpBadge></template></el-table-column>
              <el-table-column label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status" :map="BOM_STATUS" /></template></el-table-column>
              <template #empty>
                <ErpEmpty compact description="没有 BOM">
                  <el-button v-if="['SEMI_FINISHED', 'FINISHED', 'PHANTOM'].includes(m.materialType)" v-perm="'eng:bom:create'" icon="Plus"
                             @click.stop="router.push({ path: '/engineering/bom/new', query: { materialId: id } })">新建 BOM</el-button>
                </ErpEmpty>
              </template>
            </el-table>
            <div class="group-title">使用本物料的 BOM（反查）</div>
            <el-table :data="boms.asComponent" @row-click="openBom">
              <el-table-column label="BOM" min-width="160"><template #default="{ row }"><el-link type="primary" underline="never">{{ row.docNo }}</el-link></template></el-table-column>
              <el-table-column prop="materialName" label="父件名称" min-width="180" />
              <el-table-column label="用量" width="120" align="right"><template #default="{ row }">{{ formatQty(row.qtyPer) }} {{ row.uom }}</template></el-table-column>
              <el-table-column label="默认" width="80" align="center"><template #default="{ row }"><ErpBadge v-if="row.isDefault" type="success">默认</ErpBadge></template></el-table-column>
              <el-table-column label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status" :map="BOM_STATUS" /></template></el-table-column>
              <template #empty><ErpEmpty compact description="没有 BOM 使用本物料" /></template>
            </el-table>
          </el-tab-pane>

          <el-tab-pane v-if="settings.enableApproval" label="审批记录" name="approval" lazy>
            <ApprovalTimeline biz-type="ENG_MATERIAL" :biz-id="id" />
          </el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy>
            <OperationLogTable biz-type="ENG_MATERIAL" :biz-id="id" :status-map="MATERIAL_STATUS" />
          </el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy>
            <AttachmentPanel biz-type="ENG_MATERIAL" :biz-id="id" editable />
          </el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
.head { display: flex; gap: var(--erp-space-5); align-items: flex-start; }
.head-desc { flex: 1; min-width: 0; }
.image { width: 96px; height: 96px; object-fit: cover; border-radius: var(--erp-radius-card); border: 1px solid var(--erp-color-border); }
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
.detail-tabs .group-title { margin: var(--erp-space-5) 0 var(--erp-space-3); }
.detail-tabs .group-title:first-child { margin-top: 0; }
</style>
