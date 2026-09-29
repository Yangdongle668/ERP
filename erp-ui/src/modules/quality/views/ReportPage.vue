<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { formatQty } from '@/utils/format'
import { NCR_SOURCE, reportApi, type CountRow, type LotRow, type ParetoRow } from '../api/quality'

defineOptions({ name: 'QcReportPage' })

/** 质量报表（需求 10-07 第 2 节）：来料、制程、成品与出货、NCR / CAPA / 客诉统计、缺陷 Pareto */
const router = useRouter()
const tab = ref('iqc')
const pad = (n: number) => String(n).padStart(2, '0')
const d0 = new Date()
const range = ref<string[]>([`${d0.getFullYear()}-${pad(d0.getMonth() + 1)}-01`, `${d0.getFullYear()}-${pad(d0.getMonth() + 1)}-${pad(d0.getDate())}`])
const supplierId = ref<string>()
const materialId = ref<string>()
const source = ref('IQC')
const loading = ref(false)
const iqc = ref<Awaited<ReturnType<typeof reportApi.iqc>>>()
const proc = ref<Awaited<ReturnType<typeof reportApi.process>>>()
const outg = ref<Awaited<ReturnType<typeof reportApi.outgoing>>>()
const ncc = ref<Awaited<ReturnType<typeof reportApi.ncrCapaComplaint>>>()
const pareto = ref<ParetoRow[]>([])

const q = () => ({ from: range.value?.[0], to: range.value?.[1], supplierId: supplierId.value, materialId: materialId.value, source: source.value })
async function load() {
  loading.value = true
  try {
    if (tab.value === 'iqc') iqc.value = await reportApi.iqc(q())
    else if (tab.value === 'process') proc.value = await reportApi.process(q())
    else if (tab.value === 'outgoing') outg.value = await reportApi.outgoing(q())
    else if (tab.value === 'ncc') ncc.value = await reportApi.ncrCapaComplaint(q())
    else pareto.value = await reportApi.pareto(q())
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch(tab, load)
const pct = (v?: string | null) => (v === undefined || v === null ? '-' : `${Number(v).toFixed(2)}%`)
const width = (v?: string | number | null) => `${Math.max(0, Math.min(100, Number(v ?? 0)))}%`
const maxCount = (rows: CountRow[]) => Math.max(1, ...rows.map((r) => r.count))
const lowest = (rows: LotRow[]) => rows.slice(0, 10)
</script>

<template>
  <ErpPage>
    <ErpPanel>
      <div class="bar">
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" range-separator="至" />
        <SupplierSelect v-if="tab === 'iqc' || tab === 'ncc'" v-model="supplierId" placeholder="供应商" class="sel" />
        <MaterialSelect v-model="materialId" placeholder="物料" class="sel" />
        <el-select v-if="tab === 'pareto'" v-model="source" class="src">
          <el-option v-for="s in ['IQC', 'IPQC', 'FQC', 'OQC', 'RETURN', 'PRODUCTION', 'COMPLAINT']" :key="s" :value="s" :label="NCR_SOURCE[s] ?? s" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="load">查询</el-button>
      </div>
    </ErpPanel>
    <ErpPanel flush>
      <el-tabs v-model="tab" class="detail-tabs">
        <el-tab-pane label="来料质量" name="iqc">
          <template v-if="iqc">
            <div class="kpis">
              <div><span>检验批次</span><strong class="num">{{ iqc.total.lots }}</strong></div>
              <div><span>批次合格率</span><strong class="num">{{ pct(iqc.total.passRate) }}</strong></div>
              <div><span>特采 / 拒收 / 挑选</span><strong class="num">{{ iqc.total.concessionLots }} / {{ iqc.total.rejectedLots }} / {{ iqc.total.sortedLots }}</strong></div>
              <div><span>检验数量</span><strong class="num">{{ formatQty(iqc.total.inspectedQty) }}</strong></div>
              <div><span>样本不良率</span><strong class="num">{{ pct(iqc.total.defectRate) }}</strong></div>
            </div>
            <h4>供应商批次合格率（最低 10 家）</h4>
            <div v-for="r in lowest(iqc.bySupplier)" :key="r.key" class="bar-row">
              <span class="label">{{ r.name || r.key }}</span>
              <span class="track"><i :style="{ width: width(r.passRate) }" /></span>
              <span class="val num">{{ pct(r.passRate) }}（{{ r.qualifiedLots }}/{{ r.lots }}）</span>
            </div>
            <el-table :data="iqc.byMonth" class="gap">
              <el-table-column label="月份" prop="key" width="100" />
              <el-table-column label="检验批次" prop="lots" width="100" align="right" />
              <el-table-column label="合格批次" prop="qualifiedLots" width="100" align="right" />
              <el-table-column label="批次合格率" width="120" align="right"><template #default="{ row }">{{ pct(row.passRate) }}</template></el-table-column>
              <el-table-column label="不良率" width="100" align="right"><template #default="{ row }">{{ pct(row.defectRate) }}</template></el-table-column>
            </el-table>
          </template>
        </el-tab-pane>

        <el-tab-pane label="制程质量" name="process">
          <template v-if="proc">
            <div class="kpis">
              <div><span>IPQC 批次</span><strong class="num">{{ proc.total.lots }}</strong></div>
              <div><span>IPQC 批次合格率</span><strong class="num">{{ pct(proc.total.passRate) }}</strong></div>
              <div><span>样本不良率</span><strong class="num">{{ pct(proc.total.defectRate) }}</strong></div>
            </div>
            <p class="muted">工序一次良率、直通率见生产报表（良率），与本页 IPQC 数据同口径统计。</p>
            <el-table :data="proc.byMaterial">
              <el-table-column label="物料" min-width="180"><template #default="{ row }">{{ row.key }} {{ row.name }}</template></el-table-column>
              <el-table-column label="批次" prop="lots" width="90" align="right" />
              <el-table-column label="合格率" width="110" align="right"><template #default="{ row }">{{ pct(row.passRate) }}</template></el-table-column>
              <el-table-column label="不良率" width="110" align="right"><template #default="{ row }">{{ pct(row.defectRate) }}</template></el-table-column>
            </el-table>
            <h4>IPQC 不良 Pareto</h4>
            <div v-for="r in proc.pareto" :key="r.code" class="bar-row">
              <span class="label">{{ r.name }}</span><span class="track"><i :style="{ width: width(r.pct) }" /></span>
              <span class="val num">{{ r.qty }}（累计 {{ pct(r.cumulativePct) }}）</span>
            </div>
          </template>
        </el-tab-pane>

        <el-tab-pane label="成品与出货" name="outgoing">
          <template v-if="outg">
            <div class="kpis">
              <div><span>FQC 批次</span><strong class="num">{{ outg.fqc.lots }}</strong></div>
              <div><span>FQC 一次合格率</span><strong class="num">{{ pct(outg.fqcFirstPassRate) }}</strong></div>
              <div><span>OQC 批次</span><strong class="num">{{ outg.oqc.lots }}</strong></div>
              <div><span>OQC 合格率</span><strong class="num">{{ pct(outg.oqc.passRate) }}</strong></div>
              <div><span>客诉 / 客诉率</span><strong class="num">{{ outg.complaints }} / {{ pct(outg.complaintRate) }}</strong></div>
            </div>
            <el-table :data="outg.oqcByMonth">
              <el-table-column label="月份" prop="key" width="100" />
              <el-table-column label="OQC 批次" prop="lots" width="100" align="right" />
              <el-table-column label="合格率" width="110" align="right"><template #default="{ row }">{{ pct(row.passRate) }}</template></el-table-column>
              <el-table-column label="拒收批次" prop="rejectedLots" width="100" align="right" />
            </el-table>
          </template>
        </el-tab-pane>

        <el-tab-pane label="NCR / CAPA / 客诉" name="ncc">
          <template v-if="ncc">
            <div class="kpis">
              <div><span>NCR 数量</span><strong class="num">{{ ncc.ncrCount }}</strong></div>
              <div><span>NCR 平均关闭（天）</span><strong class="num">{{ ncc.ncrAvgCloseDays ?? '-' }}</strong></div>
              <div><span>CAPA 按期关闭率</span><strong class="num">{{ pct(ncc.capaOnTimeRate) }}</strong></div>
              <div><span>客诉 / 首次回复及时率</span><strong class="num">{{ ncc.complaintCount }} / {{ pct(ncc.replyOnTimeRate) }}</strong></div>
            </div>
            <div class="cols">
              <div v-for="[title, rows] in ([['NCR 按来源', ncc.ncrBySource], ['NCR 按责任', ncc.ncrByResponsibility], ['NCR 按处置', ncc.ncrByDisposition],
                                             ['客诉按客户', ncc.complaintByCustomer], ['客诉按类型', ncc.complaintByType], ['客诉按严重度', ncc.complaintBySeverity]] as [string, CountRow[]][])"
                   :key="title" class="col">
                <h4>{{ title }}</h4>
                <div v-for="r in rows" :key="r.key" class="bar-row">
                  <span class="label">{{ NCR_SOURCE[r.key] && title === 'NCR 按来源' ? NCR_SOURCE[r.key] : r.name || r.key }}</span>
                  <span class="track"><i :style="{ width: `${(r.count / maxCount(rows)) * 100}%` }" /></span>
                  <span class="val num">{{ r.count }}</span>
                </div>
                <ErpEmpty v-if="!rows.length" description="无数据" />
              </div>
            </div>
            <h4>CAPA 超期清单</h4>
            <el-table :data="ncc.capaOverdue">
              <el-table-column label="单号" width="150">
                <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/quality/capa/${row.id}`)">{{ row.docNo }}</el-link></template>
              </el-table-column>
              <el-table-column label="标题" prop="title" min-width="200" />
              <el-table-column label="负责人" prop="leaderName" width="90" />
              <el-table-column label="期限" prop="dueDate" width="100" />
              <el-table-column label="超期（天）" prop="overdueDays" width="90" align="right" />
              <el-table-column label="当前步骤" width="90"><template #default="{ row }">D{{ row.currentStep }}</template></el-table-column>
            </el-table>
          </template>
        </el-tab-pane>

        <el-tab-pane label="缺陷 Pareto" name="pareto">
          <div class="pareto-head">
            <ExportButton url="/quality/reports/defect-pareto/export" :params="q" permission="qc:report:export" filename="缺陷Pareto" />
          </div>
          <div v-for="r in pareto" :key="r.code" class="bar-row">
            <span class="label">{{ r.code }} {{ r.name }}</span><span class="track"><i :style="{ width: width(r.pct) }" /></span>
            <span class="val num">{{ r.qty }}（{{ pct(r.pct) }}，累计 {{ pct(r.cumulativePct) }}）</span>
          </div>
          <ErpEmpty v-if="!pareto.length" description="期间内没有缺陷记录" />
        </el-tab-pane>
      </el-tabs>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.bar { display: flex; align-items: center; gap: var(--erp-space-3); flex-wrap: wrap; }
.sel { width: 220px; }
.src { width: 140px; }
.kpis { display: flex; gap: var(--erp-space-4); flex-wrap: wrap; margin-bottom: var(--erp-space-4); }
.kpis > div {
  flex: 1; min-width: 160px; display: flex; flex-direction: column; gap: var(--erp-space-1); padding: var(--erp-space-3) var(--erp-space-4);
  border: 1px solid var(--erp-color-border-light); border-radius: var(--erp-radius-card);
}
.kpis span { color: var(--erp-color-text-secondary); }
.kpis strong { font-size: var(--erp-font-size-metric); }
h4 { margin: var(--erp-space-4) 0 var(--erp-space-2); font-weight: var(--erp-font-weight-semibold); }
.bar-row { display: grid; grid-template-columns: 180px minmax(0, 1fr) 200px; align-items: center; gap: var(--erp-space-2); margin-bottom: var(--erp-space-1); }
.label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.track { height: 10px; background: var(--erp-color-bg); border-radius: var(--erp-radius-card); overflow: hidden; }
.track i { display: block; height: 100%; background: var(--erp-color-primary); }
.val { color: var(--erp-color-text-secondary); }
.cols { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--erp-space-6); }
.cols .bar-row { grid-template-columns: 110px minmax(0, 1fr) 48px; }
.muted { color: var(--erp-color-text-secondary); }
.gap { margin-top: var(--erp-space-4); }
.pareto-head { display: flex; justify-content: flex-end; margin-bottom: var(--erp-space-3); }
</style>
