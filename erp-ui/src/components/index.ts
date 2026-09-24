import type { App } from 'vue'
import ErpPage from './base/ErpPage.vue'
import ErpPanel from './base/ErpPanel.vue'
import ErpBadge from './base/ErpBadge.vue'
import ErpEmpty from './base/ErpEmpty.vue'
import ErpIconButton from './base/ErpIconButton.vue'
import ErpSearchForm from './list/ErpSearchForm.vue'
import ErpTable from './list/ErpTable.vue'
import ErpPagination from './list/ErpPagination.vue'
import RowActions from './list/RowActions.vue'
import StatusTag from './form/StatusTag.vue'
import DictTag from './form/DictTag.vue'
import DictSelect from './form/DictSelect.vue'
import DictRadio from './form/DictRadio.vue'
import RemoteSelect from './form/RemoteSelect.vue'
import UserSelect from './form/UserSelect.vue'
import OrgTreeSelect from './form/OrgTreeSelect.vue'
import UomSelect from './form/UomSelect.vue'
import CurrencySelect from './form/CurrencySelect.vue'
import CountrySelect from './form/CountrySelect.vue'
import MaterialSelect from './form/MaterialSelect.vue'
import MaterialPickerDialog from './form/MaterialPickerDialog.vue'
import CustomerSelect from './form/CustomerSelect.vue'
import SupplierSelect from './form/SupplierSelect.vue'
import WarehouseSelect from './form/WarehouseSelect.vue'
import LocationSelect from './form/LocationSelect.vue'
import BatchSelect from './form/BatchSelect.vue'
import NumberInput from './form/NumberInput.vue'
import QtyInput from './form/QtyInput.vue'
import AmountInput from './form/AmountInput.vue'
import PriceInput from './form/PriceInput.vue'
import SourceDocPicker from './form/SourceDocPicker.vue'
import LinesEditor from './form/LinesEditor.vue'
import AttachmentUpload from './form/AttachmentUpload.vue'
import DocPageHeader from './doc/DocPageHeader.vue'
import DocSteps from './doc/DocSteps.vue'
import ApprovalTimeline from './doc/ApprovalTimeline.vue'
import OperationLogTable from './doc/OperationLogTable.vue'
import RelatedDocs from './doc/RelatedDocs.vue'
import AttachmentPanel from './doc/AttachmentPanel.vue'
import ApproveDialog from './doc/ApproveDialog.vue'
import ReasonDialog from './doc/ReasonDialog.vue'
import ImportDialog from './tools/ImportDialog.vue'
import ExportButton from './tools/ExportButton.vue'
import PrintButton from './tools/PrintButton.vue'

/**
 * 公共组件（UI 设计规范第 5 节）。全局注册，模块页面直接使用，不需要 import；
 * 各模块不得自行实现同类组件。
 *
 * 基础：ErpPage（页面骨架）、ErpPanel（内容面板）、ErpBadge（状态徽标）、ErpEmpty（空状态）、ErpIconButton（图标按钮）；
 * Button / Input / Select / DatePicker / Tabs / Dialog / Drawer / Dropdown / Skeleton / Toast 直接使用 Element Plus，
 * 外观由 styles/tokens.css + styles/element.css 统一定义，页面不得覆盖。
 */
export const components = {
  ErpPage, ErpPanel, ErpBadge, ErpEmpty, ErpIconButton,
  ErpSearchForm, ErpTable, ErpPagination, RowActions,
  StatusTag, DictTag, DictSelect, DictRadio, RemoteSelect, UserSelect, OrgTreeSelect, UomSelect, CurrencySelect, CountrySelect,
  MaterialSelect, MaterialPickerDialog, CustomerSelect, SupplierSelect, WarehouseSelect, LocationSelect, BatchSelect,
  NumberInput, QtyInput, AmountInput, PriceInput, SourceDocPicker, LinesEditor, AttachmentUpload,
  DocPageHeader, DocSteps, ApprovalTimeline, OperationLogTable, RelatedDocs, AttachmentPanel, ApproveDialog, ReasonDialog,
  ImportDialog, ExportButton, PrintButton
}

export function registerComponents(app: App) {
  for (const [name, c] of Object.entries(components)) app.component(name, c)
}

declare module 'vue' {
  export interface GlobalComponents {
    ErpPage: typeof ErpPage
    ErpPanel: typeof ErpPanel
    ErpBadge: typeof ErpBadge
    ErpEmpty: typeof ErpEmpty
    ErpIconButton: typeof ErpIconButton
    ErpSearchForm: typeof ErpSearchForm
    ErpTable: typeof ErpTable
    ErpPagination: typeof ErpPagination
    RowActions: typeof RowActions
    StatusTag: typeof StatusTag
    DictTag: typeof DictTag
    DictSelect: typeof DictSelect
    DictRadio: typeof DictRadio
    RemoteSelect: typeof RemoteSelect
    UserSelect: typeof UserSelect
    OrgTreeSelect: typeof OrgTreeSelect
    UomSelect: typeof UomSelect
    CurrencySelect: typeof CurrencySelect
    CountrySelect: typeof CountrySelect
    MaterialSelect: typeof MaterialSelect
    MaterialPickerDialog: typeof MaterialPickerDialog
    CustomerSelect: typeof CustomerSelect
    SupplierSelect: typeof SupplierSelect
    WarehouseSelect: typeof WarehouseSelect
    LocationSelect: typeof LocationSelect
    BatchSelect: typeof BatchSelect
    NumberInput: typeof NumberInput
    QtyInput: typeof QtyInput
    AmountInput: typeof AmountInput
    PriceInput: typeof PriceInput
    SourceDocPicker: typeof SourceDocPicker
    LinesEditor: typeof LinesEditor
    AttachmentUpload: typeof AttachmentUpload
    DocPageHeader: typeof DocPageHeader
    DocSteps: typeof DocSteps
    ApprovalTimeline: typeof ApprovalTimeline
    OperationLogTable: typeof OperationLogTable
    RelatedDocs: typeof RelatedDocs
    AttachmentPanel: typeof AttachmentPanel
    ApproveDialog: typeof ApproveDialog
    ReasonDialog: typeof ReasonDialog
    ImportDialog: typeof ImportDialog
    ExportButton: typeof ExportButton
    PrintButton: typeof PrintButton
  }
}

export * from './types'
export * from './status'
