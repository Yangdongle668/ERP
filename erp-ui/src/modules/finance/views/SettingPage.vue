<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  ACCOUNT_TYPES, AMOUNT_FIELDS, AUX_TYPES, ENABLE, labelOf, MAPPING_BIZ_TYPES, PERIOD_STATUS, settingApi,
  type AccountNode, type AccountOption, type AccountSave, type BankAccount, type Mapping, type PeriodVO
} from '../api/finance'

defineOptions({ name: 'FinSettingPage' })

/** 财务基础设置（需求 12-01）：会计科目（树）、会计期间、本公司银行账户、科目映射 */
const me = useUserStore()
const tab = ref('account')

// ==================== 会计科目 ====================
const accounts = ref<AccountNode[]>([])
const accountOptions = ref<AccountOption[]>([])
async function loadAccounts() {
  accounts.value = await settingApi.accounts()
  accountOptions.value = await settingApi.accountOptions()
}
const accDialog = ref(false)
const accEditId = ref<string>()
const acc = ref<AccountSave>({ code: '', name: '' })
function newAccount(parent?: AccountNode) {
  accEditId.value = undefined
  acc.value = { code: parent ? parent.code : '', name: '', parentCode: parent?.code, accountType: parent?.accountType, direction: parent?.direction,
    auxTypes: [], currencyAccounting: false }
  accDialog.value = true
}
function editAccount(a: AccountNode) {
  accEditId.value = a.id
  acc.value = { code: a.code, name: a.name, parentCode: a.parentCode, accountType: a.accountType, direction: a.direction, auxTypes: [...a.auxTypes],
    currencyAccounting: a.currencyAccounting }
  accDialog.value = true
}
async function saveAccount() {
  if (!acc.value.code || !acc.value.name) return ElMessage.warning('请填写编码和名称')
  if (accEditId.value) await settingApi.updateAccount(accEditId.value, acc.value)
  else await settingApi.createAccount(acc.value)
  accDialog.value = false
  ElMessage.success('已保存')
  loadAccounts()
}
async function toggleAccount(a: AccountNode) {
  await settingApi.accountStatus(a.id, a.status !== 'ENABLED')
  loadAccounts()
}
async function deleteAccount(a: AccountNode) {
  await ElMessageBox.confirm(`确定删除科目 ${a.code} ${a.name} 吗？`, '删除', { type: 'warning' })
  await settingApi.deleteAccount(a.id)
  ElMessage.success('已删除')
  loadAccounts()
}

// ==================== 会计期间 ====================
const year = ref(new Date().getFullYear())
const periods = ref<PeriodVO[]>([])
async function loadPeriods() { periods.value = await settingApi.periods(year.value) }
async function initYear() {
  await ElMessageBox.confirm(`生成 ${year.value} 年 12 个会计期间，确定吗？`, '初始化年度')
  await settingApi.initYear(year.value)
  ElMessage.success('已初始化')
  loadPeriods()
}

// ==================== 银行账户 ====================
const banks = ref<BankAccount[]>([])
async function loadBanks() { banks.value = await settingApi.banks() }
const bankDialog = ref(false)
const bank = ref<BankAccount>({ code: '', name: '', bankName: '', accountNo: '', currency: 'CNY', isDefault: false })
function editBank(b?: BankAccount) {
  bank.value = b ? { ...b } : { code: '', name: '', bankName: '', accountNo: '', currency: 'CNY', isDefault: false, status: 'ENABLED' }
  bankDialog.value = true
}
async function saveBank() {
  const b = bank.value
  if (!b.code || !b.name || !b.bankName || !b.accountNo || !b.currency) return ElMessage.warning('请填写必填项')
  if (b.id) await settingApi.updateBank(b.id, b)
  else await settingApi.createBank(b)
  bankDialog.value = false
  ElMessage.success('已保存')
  loadBanks()
}
async function deleteBank(b: BankAccount) {
  await ElMessageBox.confirm(`确定删除 ${b.name} 吗？已被收付款引用的账户将改为停用。`, '删除', { type: 'warning' })
  await settingApi.deleteBank(b.id!)
  ElMessage.success('已处理')
  loadBanks()
}

// ==================== 科目映射 ====================
const mappings = ref<Mapping[]>([])
async function loadMappings() { mappings.value = await settingApi.mappings() }
const mapDialog = ref(false)
const mapping = ref<Mapping>({ bizType: 'SALES_AR', priority: 0, entries: [] })
function editMapping(m?: Mapping) {
  mapping.value = m ? JSON.parse(JSON.stringify(m)) : { bizType: 'SALES_AR', priority: 0, status: 'ENABLED',
    entries: [{ direction: 'DEBIT', accountCode: '', amountField: 'totalAmount' }, { direction: 'CREDIT', accountCode: '', amountField: 'amount' }] }
  mapDialog.value = true
}
async function saveMapping() {
  const m = mapping.value
  if (!m.entries.some((e) => e.accountCode)) return ElMessage.warning('请至少填写一条分录')
  if (m.id) await settingApi.updateMapping(m.id, m)
  else await settingApi.createMapping(m)
  mapDialog.value = false
  ElMessage.success('已保存')
  loadMappings()
}
async function deleteMapping(m: Mapping) {
  await ElMessageBox.confirm('确定删除该映射吗？', '删除', { type: 'warning' })
  await settingApi.deleteMapping(m.id!)
  loadMappings()
}

onMounted(() => { loadAccounts(); loadPeriods(); loadBanks(); loadMappings() })
</script>

<template>
  <ErpPage description="会计科目按 4-2-2 级次编码；科目映射定义业务类型 → 借贷科目（凭证生成使用，引用的科目必须是启用的末级科目）">
    <ErpPanel flush>
      <el-tabs v-model="tab" class="detail-tabs">
        <el-tab-pane label="会计科目" name="account">
          <div class="toolbar">
            <el-button v-if="me.hasPermission('fin:account:manage')" type="primary" @click="newAccount()">新增一级科目</el-button>
          </div>
          <el-table :data="accounts" row-key="code" :tree-props="{ children: 'children' }" default-expand-all>
            <el-table-column prop="code" label="编码" width="160" />
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column label="类型" width="80"><template #default="{ row }">{{ labelOf(ACCOUNT_TYPES, row.accountType) }}</template></el-table-column>
            <el-table-column label="方向" width="60"><template #default="{ row }">{{ row.direction === 'DEBIT' ? '借' : '贷' }}</template></el-table-column>
            <el-table-column label="辅助核算" width="160">
              <template #default="{ row }">{{ row.auxTypes.map((t: string) => labelOf(AUX_TYPES, t)).join('、') }}</template>
            </el-table-column>
            <el-table-column label="外币" width="60"><template #default="{ row }">{{ row.currencyAccounting ? '是' : '' }}</template></el-table-column>
            <el-table-column label="末级" width="60"><template #default="{ row }">{{ row.leaf ? '是' : '' }}</template></el-table-column>
            <el-table-column label="状态" width="80"><template #default="{ row }"><StatusTag :value="row.status" :map="ENABLE" /></template></el-table-column>
            <el-table-column v-if="me.hasPermission('fin:account:manage')" label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="newAccount(row as AccountNode)">新增下级</el-button>
                <el-button link type="primary" @click="editAccount(row as AccountNode)">编辑</el-button>
                <el-button link type="primary" @click="toggleAccount(row as AccountNode)">{{ row.status === 'ENABLED' ? '停用' : '启用' }}</el-button>
                <el-button link type="danger" @click="deleteAccount(row as AccountNode)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="会计期间" name="period">
          <div class="toolbar">
            <el-input-number v-model="year" :min="2000" :max="2100" controls-position="right" @change="loadPeriods" />
            <el-button v-if="me.hasPermission('fin:period:manage')" type="primary" :disabled="periods.length > 0" @click="initYear">初始化年度</el-button>
            <span class="tip">结账在“月结”页面进行；未初始化的期间视为已开启</span>
          </div>
          <el-table :data="periods">
            <el-table-column prop="period" label="期间" width="100" />
            <el-table-column prop="startDate" label="开始" width="110" />
            <el-table-column prop="endDate" label="结束" width="110" />
            <el-table-column label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status" :map="PERIOD_STATUS" /></template></el-table-column>
            <el-table-column label="成本锁定" width="90"><template #default="{ row }">{{ row.costLocked ? '是' : '' }}</template></el-table-column>
            <el-table-column prop="closedByName" label="结账人" width="100" />
            <el-table-column prop="closedAt" label="结账时间" min-width="150" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="银行账户" name="bank">
          <div class="toolbar">
            <el-button v-if="me.hasPermission('fin:bank:manage')" type="primary" @click="editBank()">新增账户</el-button>
          </div>
          <el-table :data="banks">
            <el-table-column prop="code" label="编码" width="110" />
            <el-table-column prop="name" label="名称" min-width="140" />
            <el-table-column prop="bankName" label="开户行" min-width="140" />
            <el-table-column prop="accountNo" label="账号" width="180" />
            <el-table-column prop="currency" label="币别" width="60" />
            <el-table-column prop="swift" label="SWIFT" width="110" />
            <el-table-column prop="accountCode" label="会计科目" width="100" />
            <el-table-column label="默认" width="60"><template #default="{ row }">{{ row.isDefault ? '是' : '' }}</template></el-table-column>
            <el-table-column label="状态" width="80"><template #default="{ row }"><StatusTag :value="row.status" :map="ENABLE" /></template></el-table-column>
            <el-table-column v-if="me.hasPermission('fin:bank:manage')" label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="editBank(row as BankAccount)">编辑</el-button>
                <el-button link type="danger" @click="deleteBank(row as BankAccount)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="科目映射" name="mapping">
          <div class="toolbar">
            <el-button v-if="me.hasPermission('fin:mapping:manage')" type="primary" @click="editMapping()">新增映射</el-button>
          </div>
          <el-table :data="mappings">
            <el-table-column label="业务类型" width="140"><template #default="{ row }">{{ labelOf(MAPPING_BIZ_TYPES, row.bizType) }}</template></el-table-column>
            <el-table-column label="条件" width="160"><template #default="{ row }">{{ row.conditionDesc || (row.matchCondition ? row.matchCondition : '默认') }}</template></el-table-column>
            <el-table-column prop="priority" label="优先级" width="70" align="right" />
            <el-table-column label="分录" min-width="320">
              <template #default="{ row }">
                <div v-for="(e, i) in row.entries" :key="i">{{ e.direction === 'DEBIT' ? '借' : '贷' }} {{ e.accountCode }}（{{ labelOf(AMOUNT_FIELDS, e.amountField) }}）</div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80"><template #default="{ row }"><StatusTag :value="row.status" :map="ENABLE" /></template></el-table-column>
            <el-table-column v-if="me.hasPermission('fin:mapping:manage')" label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="editMapping(row as Mapping)">编辑</el-button>
                <el-button link type="danger" @click="deleteMapping(row as Mapping)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </ErpPanel>

    <el-dialog v-model="accDialog" :title="accEditId ? '编辑科目' : '新增科目'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="上级科目">{{ acc.parentCode ?? '（一级科目）' }}</el-form-item>
        <el-form-item label="编码" required><el-input v-model="acc.code" :disabled="!!accEditId" maxlength="32" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="acc.name" maxlength="64" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="acc.accountType" class="w-full"><el-option v-for="t in ACCOUNT_TYPES" :key="String(t.value)" :value="t.value" :label="t.label" /></el-select>
        </el-form-item>
        <el-form-item label="余额方向">
          <el-radio-group v-model="acc.direction"><el-radio value="DEBIT">借</el-radio><el-radio value="CREDIT">贷</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item label="辅助核算">
          <el-checkbox-group v-model="acc.auxTypes"><el-checkbox v-for="t in AUX_TYPES" :key="String(t.value)" :value="t.value">{{ t.label }}</el-checkbox></el-checkbox-group>
        </el-form-item>
        <el-form-item label="外币核算"><el-switch v-model="acc.currencyAccounting" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="accDialog = false">取消</el-button>
        <el-button type="primary" @click="saveAccount">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bankDialog" :title="bank.id ? '编辑银行账户' : '新增银行账户'" width="560px">
      <el-form label-width="90px">
        <el-form-item label="编码" required><el-input v-model="bank.code" maxlength="32" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="bank.name" maxlength="64" placeholder="如 中行美元户" /></el-form-item>
        <el-form-item label="开户行" required><el-input v-model="bank.bankName" maxlength="128" /></el-form-item>
        <el-form-item label="账号" required><el-input v-model="bank.accountNo" maxlength="64" /></el-form-item>
        <el-form-item label="币别" required><CurrencySelect v-model="bank.currency" /></el-form-item>
        <el-form-item label="SWIFT"><el-input v-model="bank.swift" maxlength="32" /></el-form-item>
        <el-form-item label="银行地址"><el-input v-model="bank.bankAddress" maxlength="256" /></el-form-item>
        <el-form-item label="会计科目">
          <el-select v-model="bank.accountCode" clearable filterable class="w-full">
            <el-option v-for="a in accountOptions" :key="a.code" :value="a.code" :label="`${a.code} ${a.fullName}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="同币别默认"><el-switch v-model="bank.isDefault" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="bank.status"><el-radio value="ENABLED">启用</el-radio><el-radio value="DISABLED">停用</el-radio></el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bankDialog = false">取消</el-button>
        <el-button type="primary" @click="saveBank">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="mapDialog" :title="mapping.id ? '编辑科目映射' : '新增科目映射'" width="820px">
      <el-form label-width="90px">
        <el-form-item label="业务类型" required>
          <el-select v-model="mapping.bizType" class="w-full"><el-option v-for="t in MAPPING_BIZ_TYPES" :key="String(t.value)" :value="t.value" :label="t.label" /></el-select>
        </el-form-item>
        <el-form-item label="条件"><el-input v-model="mapping.matchCondition" placeholder='JSON，如 {"currency":"USD"}；为空表示默认映射' /></el-form-item>
        <el-form-item label="条件说明"><el-input v-model="mapping.conditionDesc" maxlength="128" /></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="mapping.priority" :min="0" :max="999" /></el-form-item>
        <el-form-item label="分录" required>
          <el-table :data="mapping.entries">
            <el-table-column label="借贷" width="100">
              <template #default="{ row }">
                <el-select v-model="row.direction"><el-option value="DEBIT" label="借" /><el-option value="CREDIT" label="贷" /></el-select>
              </template>
            </el-table-column>
            <el-table-column label="科目" min-width="200">
              <template #default="{ row }">
                <el-select v-model="row.accountCode" filterable>
                  <el-option v-for="a in accountOptions" :key="a.code" :value="a.code" :label="`${a.code} ${a.fullName}`" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="金额取值" width="130">
              <template #default="{ row }">
                <el-select v-model="row.amountField"><el-option v-for="f in AMOUNT_FIELDS" :key="String(f.value)" :value="f.value" :label="f.label" /></el-select>
              </template>
            </el-table-column>
            <el-table-column label="摘要模板" min-width="140"><template #default="{ row }"><el-input v-model="row.summaryTemplate" /></template></el-table-column>
            <el-table-column width="60"><template #default="{ $index }"><el-button link type="danger" @click="mapping.entries.splice($index, 1)">删除</el-button></template></el-table-column>
          </el-table>
          <el-button class="add" @click="mapping.entries.push({ direction: 'CREDIT', accountCode: '', amountField: 'amount' })">添加分录</el-button>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="mapping.status"><el-radio value="ENABLED">启用</el-radio><el-radio value="DISABLED">停用</el-radio></el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mapDialog = false">取消</el-button>
        <el-button type="primary" @click="saveMapping">保存</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.toolbar { display: flex; align-items: center; gap: var(--erp-space-2); padding: var(--erp-space-3); }
.tip { color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-caption); }
.add { margin-top: var(--erp-space-2); }
</style>
