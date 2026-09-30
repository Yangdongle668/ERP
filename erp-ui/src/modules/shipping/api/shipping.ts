import { http, type PageParam, type PageResult } from '@/api/http'
import type { Option, StatusMap } from '@/components'

/** 出货模块接口与公共类型（需求 11-出货）。拣货、装箱、出货数量为基本单位 */

export const NOTICE_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已审核', type: 'primary', plain: true },
  PICKING: { label: '拣货中', type: 'primary' },
  PACKED: { label: '已装箱', type: 'primary' },
  OQC: { label: '待 OQC', type: 'warning', plain: true },
  READY: { label: '待出货', type: 'success', plain: true },
  SHIPPED: { label: '已出货', type: 'success' },
  CLOSED: { label: '已关闭', type: 'info', plain: true },
  VOIDED: { label: '已作废', type: 'danger' }
}
export const PICKING_STATUS: StatusMap = {
  WAITING: { label: '待拣', type: 'warning' },
  PICKING: { label: '拣货中', type: 'primary' },
  DONE: { label: '已完成', type: 'success' },
  CANCELED: { label: '已取消', type: 'info', plain: true }
}
export const SHIPMENT_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING: { label: '待审批', type: 'warning' },
  SUBMITTED: { label: '待出库', type: 'primary', plain: true },
  SHIPPED: { label: '已出货', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  VOIDED: { label: '已作废', type: 'danger' }
}
export const OQC_RESULT: StatusMap = {
  PENDING: { label: '检验中', type: 'warning' },
  PASSED: { label: '合格', type: 'success' },
  REJECTED: { label: '不合格', type: 'danger' }
}
export const LOGISTICS_STATUS: StatusMap = {
  BOOKED: { label: '已订舱', type: 'info' },
  LOADED: { label: '已装柜', type: 'info', plain: true },
  DEPARTED: { label: '已离港', type: 'primary' },
  ARRIVED: { label: '已到港', type: 'primary', plain: true },
  CLEARED: { label: '已清关', type: 'warning', plain: true },
  DELIVERED: { label: '已签收', type: 'success' }
}
export const ENABLE: StatusMap = { ENABLED: { label: '启用', type: 'success' }, DISABLED: { label: '停用', type: 'info', plain: true } }
export const TRANSPORT_SERVICES: Option[] = [
  { value: 'SEA', label: '海运' }, { value: 'AIR', label: '空运' }, { value: 'EXPRESS', label: '快递' }, { value: 'LAND', label: '陆运' }, { value: 'RAIL', label: '铁路' }
]

export const optionsOf = (map: StatusMap, exclude: string[] = []): Option[] =>
  Object.entries(map).filter(([k]) => !exclude.includes(k)).map(([value, s]) => ({ value, label: s.label }))
export const joinList = (v?: string[]) => (v?.length ? v.join(',') : undefined)
export const num = (v?: string | number | null) => (v === undefined || v === null || v === '' ? 0 : Number(v))

export interface SaveResult { id: string; warnings: string[] }
export interface LinkedDoc { id: string; docNo: string; status: string; date?: string; qty?: string }

// ==================== 出货通知 ====================

export interface NoticeRow {
  id: string; docNo: string; customerId: string; customerName?: string; shipDate: string; overdue: boolean; transportMode: string; portOfDestination?: string
  lineCount: number; totalQty: string; currency: string; totalAmount?: string; pickedQty: string; packedQty: string; shippedQty: string; oqcRequired: boolean
  oqcResult?: string; noticeStatus: string; ownerId?: string; ownerName?: string; warehouseName?: string; createdAt: string
}
export interface NoticeLine {
  id?: string; lineNo?: number; orderId?: string; orderNo?: string; orderLineId: string; orderLineNo?: number; customerPoNo?: string; materialId?: string
  materialCode?: string; materialName?: string; spec?: string; customerPartNo?: string; description?: string; uom?: string; qty?: string; baseQty?: string
  baseUom?: string; priceInclTax?: string; taxRate?: string; totalAmount?: string; oqcRequired?: boolean; pickedQty?: string; packedQty?: string
  shippedQty?: string; shortageQty?: string; noticeableQty?: string; availableQty?: string; shippingPlanLineId?: string; remark?: string
}
export interface NoticeDetail {
  id: string; docNo: string; docDate: string; status: string; noticeStatus: string; customerId: string; customerName?: string; currency: string
  shipDate: string; transportMode: string; tradeTerm?: string; portOfLoading?: string; portOfDestination?: string; shipToAddressId: string; shipToText?: string
  notifyParty?: string; forwarderId?: string; forwarderName?: string; warehouseId: string; warehouseName?: string; oqcRequired: boolean; oqcResult?: string
  totalAmount?: string; totalAmountBase?: string; creditWarning: boolean; prepaymentUnpaid: boolean; approvedAt?: string; closeReason?: string
  ownerId?: string; ownerName?: string; remark?: string; pickingEnabled: boolean; packingEnabled: boolean; pickingStarted: boolean; pickingDone: boolean
  canShip: boolean; lines: NoticeLine[]; pickings: LinkedDoc[]; shipments: LinkedDoc[]; createdByName?: string; createdAt: string; priceVisible: boolean
}
export interface OrderLineOption {
  orderLineId: string; orderId: string; orderNo: string; lineNo: number; customerPoNo?: string; materialId: string; materialCode?: string
  materialName?: string; customerPartNo?: string; description?: string; uom: string; qty: string; noticedQty: string; noticeableQty: string
  promisedDate?: string; availableQty: string; currency: string; priceInclTax?: string
}
export interface CustomerDefaults {
  customerId: string; currency?: string; tradeTerm?: string; warehouseId?: string; addresses: { id: string; text: string; isDefault: boolean }[]
  shipToAddressId?: string; portOfLoading?: string; portOfDestination?: string
}
export interface PlanLineOption {
  planLineId: string; planNo: string; planWeek: string; customerId: string; customerName?: string; orderLineId: string; orderNo?: string; lineNo?: number
  materialId: string; materialCode?: string; materialName?: string; planQty: string; noticedQty: string; planShipDate?: string; transportMode?: string
}

export const noticeApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<NoticeRow>>('/shipping/notices', q),
  get: (id: string) => http.get<NoticeDetail>(`/shipping/notices/${id}`),
  orderLines: (q: { customerId?: string; orderNo?: string; materialId?: string; warehouseId?: string }) =>
    http.get<OrderLineOption[]>('/shipping/notices/order-lines', q),
  customerDefaults: (customerId: string) => http.get<CustomerDefaults>('/shipping/notices/customer-defaults', { customerId }),
  planLines: (week: string) => http.get<PlanLineOption[]>('/shipping/notices/plan-lines', { week }),
  create: (d: object) => http.post<SaveResult>('/shipping/notices', d),
  update: (id: string, d: object) => http.put<SaveResult>(`/shipping/notices/${id}`, d),
  remove: (id: string) => http.delete<void>(`/shipping/notices/${id}`),
  fromOrders: (d: { orderLineIds: string[]; shipDate?: string; warehouseId?: string }) => http.post<SaveResult>('/shipping/notices/from-orders', d),
  fromPlan: (d: { planWeek: string; planLineIds?: string[]; warehouseId?: string }) => http.post<string[]>('/shipping/notices/from-plan', d),
  submit: (id: string) => http.post<SaveResult>(`/shipping/notices/${id}/submit`),
  unapprove: (id: string) => http.post<void>(`/shipping/notices/${id}/unapprove`),
  close: (id: string, reason: string) => http.post<void>(`/shipping/notices/${id}/close`, { reason }),
  // 装箱
  packing: (id: string) => http.get<PackingView>(`/shipping/notices/${id}/cartons`),
  batchPack: (id: string, d: object) => http.post<string[]>(`/shipping/notices/${id}/cartons/batch`, d),
  addCarton: (id: string, d: object) => http.post<string>(`/shipping/notices/${id}/cartons`, d),
  clearCartons: (id: string) => http.delete<void>(`/shipping/notices/${id}/cartons`),
  updateCarton: (cartonId: string, d: object) => http.put<void>(`/shipping/cartons/${cartonId}`, d),
  deleteCarton: (cartonId: string) => http.delete<void>(`/shipping/cartons/${cartonId}`),
  packComplete: (id: string, requestOqc: boolean) => http.post<void>(`/shipping/notices/${id}/pack-complete`, { requestOqc }),
  requestOqc: (id: string) => http.post<void>(`/shipping/notices/${id}/request-oqc`),
  // 生成出货单
  generate: (id: string, d: { cartonIds?: string[]; units?: { noticeLineId: string; batchNo?: string; qty: string }[]; shipDate?: string }) =>
    http.post<string>(`/shipping/notices/${id}/shipments`, d)
}

// ==================== 拣货 ====================

export interface PickingRow {
  id: string; docNo: string; noticeId: string; noticeNo?: string; customerId?: string; customerName?: string; shipDate?: string; warehouseId: string
  warehouseName?: string; lineCount: number; suggestedQty: string; pickedQty: string; pickingStatus: string; pickerId?: string; pickerName?: string
  startedAt?: string; completedAt?: string; createdAt: string
}
export interface PickingLine {
  id?: string; lineNo?: number; noticeLineId: string; noticeLineNo?: number; materialId?: string; materialCode?: string; materialName?: string; spec?: string
  baseUom?: string; locationId?: string; batchNo?: string; suggestedQty?: string; pickedQty?: string; serialNos?: string; shortage?: boolean
}
export interface NoticeLineSum {
  noticeLineId: string; lineNo: number; materialId: string; materialCode?: string; materialName?: string; baseUom?: string; qty: string; pickedQty: string
  shortageQty?: string
}
export interface PickingDetail {
  id: string; docNo: string; status: string; pickingStatus: string; noticeId: string; noticeNo: string; noticeStatus: string; customerId: string
  customerName?: string; shipDate?: string; warehouseId: string; warehouseName?: string; pickerId?: string; pickerName?: string; startedAt?: string
  completedAt?: string; remark?: string; lines: PickingLine[]; summary: NoticeLineSum[]; createdAt: string
}
export interface BatchOption { batchNo?: string; locationId?: string; availableQty: string; productionDate?: string; expireDate?: string }

export const pickingApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<PickingRow>>('/shipping/pickings', q),
  get: (id: string) => http.get<PickingDetail>(`/shipping/pickings/${id}`),
  start: (id: string) => http.post<void>(`/shipping/pickings/${id}/start`),
  saveLines: (id: string, lines: object[]) => http.put<void>(`/shipping/pickings/${id}/lines`, { lines }),
  complete: (id: string, acceptShort: boolean) => http.post<void>(`/shipping/pickings/${id}/complete`, { acceptShort }),
  batches: (id: string, noticeLineId: string) => http.get<BatchOption[]>(`/shipping/pickings/${id}/batches`, { noticeLineId })
}

// ==================== 装箱 ====================

export interface PackItem {
  noticeLineId: string; lineNo: number; materialId: string; materialCode?: string; materialName?: string; customerPartNo?: string; batchNo?: string
  uom?: string; pickedQty: string; packedQty: string; remainingQty: string; unitNetWeight?: string; unitGrossWeight?: string
}
export interface CartonLine {
  id?: string; noticeLineId: string; lineNo?: number; materialId?: string; materialCode?: string; materialName?: string; customerPartNo?: string
  batchNo?: string; qty: string; serialNos?: string
}
export interface Carton {
  id: string; cartonNo: number; cartonSpec?: string; lengthCm?: string; widthCm?: string; heightCm?: string; grossWeightKg?: string; netWeightKg?: string
  cbm?: string; palletNo?: string; shipmentId?: string; shipmentNo?: string; lines: CartonLine[]
}
export interface PackingTotals { cartonCount: number; qty: string; grossWeight: string; netWeight: string; cbm: string }
export interface PackingView {
  noticeId: string; noticeNo: string; noticeStatus: string; customerId: string; customerName?: string; shipDate?: string; oqcRequired: boolean
  oqcResult?: string; packingEnabled: boolean; editable: boolean; complete: boolean; items: PackItem[]; cartons: Carton[]; totals: PackingTotals
}

// ==================== 出货单 ====================

export interface ShipmentRow {
  id: string; docNo: string; noticeId: string; noticeNo?: string; customerId: string; customerName?: string; shipDate: string; transportMode: string
  portOfDestination?: string; totalQty: string; currency: string; totalAmount?: string; cartonCount?: number; blNo?: string; etd?: string; eta?: string
  etaOverdue: boolean; forwarderId?: string; forwarderName?: string; containerNo?: string; logisticsStatus?: string; logisticsUpdatedAt?: string
  shipmentStatus: string; ownerId?: string; ownerName?: string; createdAt: string
}
export interface ShipmentLine {
  id: string; lineNo: number; noticeLineId: string; orderId: string; orderNo: string; orderLineId: string; customerPoNo?: string; materialId: string
  materialCode?: string; materialName?: string; spec?: string; customerPartNo?: string; description?: string; uom: string; qty: string; baseQty: string
  baseUom?: string; batchNo?: string; serialNos?: string; priceInclTax?: string; taxRate?: string; amount?: string; taxAmount?: string; totalAmount?: string
  outQty?: string
}
export interface LogisticsEvent { id: string; logisticsStatus: string; occurredAt: string; location?: string; remark?: string; operatorName?: string }
export interface DocRef { id: string; no: string; invalid: boolean }
export interface ShipmentDetail {
  id: string; docNo: string; docDate: string; status: string; shipmentStatus: string; noticeId: string; noticeNo: string; customerId: string
  customerName?: string; foreignCustomer: boolean; currency: string; exchangeRate?: string; shipDate: string; transportMode: string; tradeTerm?: string
  portOfLoading?: string; portOfDestination?: string; shipToText?: string; warehouseId: string; warehouseName?: string; totalQty: string
  totalAmount?: string; totalAmountBase?: string; cartonCount?: number; grossWeight?: string; netWeight?: string; cbm?: string; stockOutId?: string
  packingList?: DocRef; invoice?: DocRef; customs?: DocRef; forwarderId?: string; forwarderName?: string; containerNo?: string; sealNo?: string
  blNo?: string; blDate?: string; etd?: string; eta?: string; logisticsStatus?: string; logisticsUpdatedAt?: string; signedAt?: string; signedBy?: string
  creditWarning: boolean; prepaymentUnpaid: boolean; shippedAt?: string; voidReason?: string; ownerId?: string; ownerName?: string; remark?: string
  lines: ShipmentLine[]; cartons: Carton[]; cartonTotals: PackingTotals; events: LogisticsEvent[]; createdByName?: string; createdAt: string
  priceVisible: boolean
}

export const shipmentApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<ShipmentRow>>('/shipping/shipments', q),
  logistics: (q: PageParam & Record<string, unknown>) => http.get<PageResult<ShipmentRow>>('/shipping/logistics', q),
  get: (id: string) => http.get<ShipmentDetail>(`/shipping/shipments/${id}`),
  update: (id: string, d: object) => http.put<void>(`/shipping/shipments/${id}`, d),
  remove: (id: string) => http.delete<void>(`/shipping/shipments/${id}`),
  void: (id: string, reason: string) => http.post<void>(`/shipping/shipments/${id}/void`, { reason }),
  submit: (id: string) => http.post<SaveResult>(`/shipping/shipments/${id}/submit`),
  withdraw: (id: string) => http.post<void>(`/shipping/shipments/${id}/withdraw`),
  saveLogistics: (id: string, d: object) => http.put<void>(`/shipping/shipments/${id}/logistics`, d),
  sign: (id: string, d: object) => http.post<void>(`/shipping/shipments/${id}/sign`, d),
  addEvent: (id: string, d: object) => http.post<void>(`/shipping/shipments/${id}/logistics-events`, d),
  createPackingList: (id: string) => http.post<string>(`/shipping/shipments/${id}/packing-list`),
  createInvoice: (id: string) => http.post<string>(`/shipping/shipments/${id}/invoice`),
  createCustoms: (id: string) => http.post<string>(`/shipping/shipments/${id}/customs`)
}

// ==================== 单证 ====================

export interface DocRow {
  id: string; no: string; date?: string; shipmentId: string; shipmentNo?: string; customerId?: string; customerName?: string; currency?: string
  totalQty?: string; cartons?: number; totalAmount?: string; invalid: boolean; createdAt: string
}
export interface PlLine {
  cartonRange?: string; description?: string; partNo?: string; batchNo?: string; qtyPerCarton?: string; cartons?: number; qty?: string; uom?: string
  netWeight?: string; grossWeight?: string; cbm?: string; shipmentNo?: string
}
export interface PlTotals { cartons: number; qty: string; netWeight: string; grossWeight: string; cbm: string }
export interface PackingListDetail {
  id: string; plNo: string; plDate: string; shipmentId: string; shipmentNo: string; shipmentStatus: string; customerId: string; customerName?: string
  consignee?: string; notifyParty?: string; shippingMarks?: string; lines: PlLine[]; totals?: PlTotals; remark?: string; invalid: boolean; createdAt: string; shipmentNos: string[]
}
export interface InvoiceLine {
  id: string; lineNo: number; shipmentLineId?: string; orderNo?: string; customerPoNo?: string; customerPartNo?: string; description?: string
  hsCode?: string; origin?: string; qty: string; uom?: string; unitPrice?: string; amount?: string
}
export interface InvoiceDetail {
  id: string; invoiceNo: string; invoiceDate: string; shipmentId: string; shipmentNo: string; shipmentStatus: string; customerId: string
  customerName?: string; billTo?: string; consignee?: string; notifyParty?: string; currency: string; tradeTerm?: string; paymentTermText?: string
  portOfLoading?: string; portOfDestination?: string; vesselFlight?: string; totalAmount?: string; amountInWords?: string; bankInfo?: string
  remark?: string; invalid: boolean; priceVisible: boolean; lines: InvoiceLine[]; createdAt: string
}
export interface CustomsItem {
  id: string; seq: number; hsCode?: string; declareName?: string; declareElements?: string; qty: string; uom?: string; secondQty?: string
  secondUom?: string; unitPrice?: string; amount?: string; origin?: string; netWeight?: string; grossWeight?: string
}
export interface CustomsDetail {
  id: string; docCode: string; shipmentId: string; shipmentNo: string; shipmentStatus: string; customerId: string; customerName?: string
  customsNo?: string; declareDate?: string; tradeMode?: string; declarePort?: string; destinationCountry?: string; currency?: string
  totalAmount?: string; remark?: string; invalid: boolean; items: CustomsItem[]; createdAt: string
}

export const docApi = {
  packingLists: (q: PageParam & Record<string, unknown>) => http.get<PageResult<DocRow>>('/shipping/packing-lists', q),
  packingList: (id: string) => http.get<PackingListDetail>(`/shipping/packing-lists/${id}`),
  mergePackingList: (shipmentIds: string[]) => http.post<string>('/shipping/packing-lists/merge', { shipmentIds }),
  updatePackingList: (id: string, d: object) => http.put<void>(`/shipping/packing-lists/${id}`, d),
  invoices: (q: PageParam & Record<string, unknown>) => http.get<PageResult<DocRow>>('/shipping/invoices', q),
  invoice: (id: string) => http.get<InvoiceDetail>(`/shipping/invoices/${id}`),
  updateInvoice: (id: string, d: object) => http.put<void>(`/shipping/invoices/${id}`, d),
  customsList: (q: PageParam & Record<string, unknown>) => http.get<PageResult<DocRow>>('/shipping/customs', q),
  customs: (id: string) => http.get<CustomsDetail>(`/shipping/customs/${id}`),
  updateCustoms: (id: string, d: object) => http.put<void>(`/shipping/customs/${id}`, d)
}

// ==================== 货代 ====================

export interface ForwarderRow {
  id: string; code: string; name: string; contact?: string; phone?: string; email?: string; services: string[]; status: string; remark?: string; createdAt: string
}
export interface ForwarderOption { id: string; code: string; name: string }

export const forwarderApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<ForwarderRow>>('/shipping/forwarders', q),
  options: () => http.get<ForwarderOption[]>('/shipping/forwarders/options'),
  create: (d: object) => http.post<string>('/shipping/forwarders', d),
  update: (id: string, d: object) => http.put<void>(`/shipping/forwarders/${id}`, d),
  remove: (id: string) => http.delete<void>(`/shipping/forwarders/${id}`)
}

// ==================== 报表 ====================

export interface PendingRow {
  customerId: string; customerName?: string; orderId: string; orderNo: string; lineNo?: number; materialId: string; materialCode?: string
  materialName?: string; qty: string; dueDate?: string; overdue: boolean; noticeId?: string; noticeNo?: string; noticeStatus?: string
  availableQty?: string; ownerName?: string
}
export interface DetailRow {
  shipmentId: string; shipmentNo: string; shipDate: string; customerName?: string; orderId: string; orderNo: string; customerPoNo?: string
  materialCode?: string; materialName?: string; batchNo?: string; qty: string; uom?: string; currency: string; price?: string; amount?: string
  amountBase?: string; transportMode?: string; blNo?: string; ownerName?: string
}
export interface SummaryRow { key: string; label: string; lines: number; qty: string; amountBase?: string }
export interface DetailReport { rows: DetailRow[]; summary: SummaryRow[]; totalQty: string; totalAmountBase?: string }
export interface OnTimeGroup { key: string; label: string; total: number; onTime: number; rate?: string }
export interface DelayRow {
  orderId: string; orderNo: string; lineNo?: number; customerName?: string; materialCode?: string; materialName?: string; dueDate?: string
  firstShipDate: string; delayDays: number; ownerName?: string
}
export interface OnTimeReport {
  total: number; onTime: number; rate?: string; byCustomer: OnTimeGroup[]; byOwner: OnTimeGroup[]; byMaterial: OnTimeGroup[]; delays: DelayRow[]
}
export interface ExportStatRow { month: string; country: string; hsCode: string; shipments: number; qty: string; amountBase?: string }

export const reportApi = {
  pending: (q: object) => http.get<PendingRow[]>('/shipping/reports/pending', q),
  details: (q: object) => http.get<DetailReport>('/shipping/reports/details', q),
  onTime: (q: object) => http.get<OnTimeReport>('/shipping/reports/on-time', q),
  exportStats: (q: object) => http.get<ExportStatRow[]>('/shipping/reports/export-stats', q)
}
