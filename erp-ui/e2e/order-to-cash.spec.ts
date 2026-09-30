import { expect, test } from '@playwright/test'
import { adminToken, client } from './api'
import { uiLogin, watchProblems } from './support'

/**
 * 订单到收款主线：接口准备客户、物料、库存，走完 订单 → 出货通知 → 拣货 → 装箱 → 出货单 → 出库确认，
 * 再通过页面核对：订单详情、出货单详情、应收单列表都出现这笔业务（出货确认后自动生成应收）。
 */
const CAT_FG = '507'
const W_FG = '803'
const TERM_NET30 = '404'

test('销售订单 → 出货 → 应收', async ({ page, request }) => {
  const api = client(request, await adminToken(request))
  const uniq = Date.now().toString().slice(-8)

  // 物料与库存
  const materialId = await api.post<string>('/engineering/materials', {
    categoryId: CAT_FG, name: `E2E成品${uniq}`, nameEn: 'E2E item', materialType: 'FINISHED', baseUom: 'PCS', tracking: 'NONE',
    iqcRequired: false, fqcRequired: false, oqcRequired: false
  })
  await api.post(`/engineering/materials/${materialId}/enable`)
  const stockIn = await api.post<number>('/inventory/stock-ins', {
    warehouseId: W_FG, reason: 'E2E 备货', lines: [{ materialId, qty: '100', unitCost: '5' }]
  })
  await api.post(`/inventory/stock-ins/${stockIn}/submit`)

  // 客户与订单
  const customerName = `E2E客户 ${uniq}`
  const addr = (type: string) => ({ addressType: type, companyName: customerName, country: 'CN', addressLine: '1 Main St', isDefault: true })
  const customer = await api.post<{ id: string }>('/crm/customers', {
    name: customerName, country: 'CN', paymentTermId: TERM_NET30, taxNo: `91440300${uniq}`,
    contacts: [{ name: 'John', email: 'john@example.com', isPrimary: true }], addresses: [addr('SHIP_TO'), addr('BILL_TO')]
  })
  await api.post(`/crm/customers/${customer.id}/activate`)
  const order = await api.post<{ id: string; orderNo?: string }>('/sales/orders', {
    customerId: customer.id,
    lines: [{ materialId, qty: '100', price: '10', requiredDate: new Date(Date.now() + 20 * 86400000).toISOString().slice(0, 10) }]
  })
  const submitted = await api.post<{ status: string }>(`/sales/orders/${order.id}/submit`)
  expect(submitted.status).toBe('APPROVED')
  const orderDetail = await api.get<{ docNo: string; lines: { id: string }[] }>(`/sales/orders/${order.id}`)

  // 出货通知 → 拣货 → 装箱
  const defaults = await api.get<{ shipToAddressId: string }>(`/shipping/notices/customer-defaults?customerId=${customer.id}`)
  const notice = await api.post<{ id: string }>('/shipping/notices', {
    customerId: customer.id, shipDate: new Date().toISOString().slice(0, 10), transportMode: 'LAND', shipToAddressId: defaults.shipToAddressId,
    warehouseId: W_FG, lines: [{ orderLineId: orderDetail.lines[0].id, qty: '100' }]
  })
  await api.post(`/shipping/notices/${notice.id}/submit`)
  const nd = await api.get<{ pickings: { id: string }[]; lines: { id: string }[] }>(`/shipping/notices/${notice.id}`)
  const pickingId = nd.pickings[nd.pickings.length - 1].id
  const picking = await api.get<{ lines: { noticeLineId: string; batchNo?: string; suggestedQty: number }[] }>(`/shipping/pickings/${pickingId}`)
  await api.put(`/shipping/pickings/${pickingId}/lines`, {
    lines: picking.lines.map((l) => ({ noticeLineId: l.noticeLineId, batchNo: l.batchNo ?? null, suggestedQty: l.suggestedQty, pickedQty: l.suggestedQty }))
  })
  await api.post(`/shipping/pickings/${pickingId}/complete`, { acceptShort: false })
  await api.post(`/shipping/notices/${notice.id}/cartons/batch`, {
    noticeLineId: nd.lines[0].id, qtyPerCarton: '50', lengthCm: '60', widthCm: '40', heightCm: '40', tareWeightKg: '1.5'
  })
  await api.post(`/shipping/notices/${notice.id}/pack-complete`)

  // 出货单 → 提交 → 确认出库
  const packing = await api.get<{ cartons: { id: string }[] }>(`/shipping/notices/${notice.id}/cartons`)
  const shipmentId = await api.post<string>(`/shipping/notices/${notice.id}/shipments`, { cartonIds: packing.cartons.map((c) => c.id) })
  await api.post(`/shipping/shipments/${shipmentId}/submit`)
  const shipment = await api.get<{ stockOutId: string; docNo: string }>(`/shipping/shipments/${shipmentId}`)
  await api.post(`/inventory/stock-outs/${shipment.stockOutId}/confirm`, {})
  expect((await api.get<{ shipmentStatus: string }>(`/shipping/shipments/${shipmentId}`)).shipmentStatus).toBe('SHIPPED')

  // 页面核对
  await uiLogin(page)
  const problems = watchProblems(page)

  await page.goto(`/sales/order/${order.id}`)
  await expect(page.getByText(orderDetail.docNo).first()).toBeVisible()
  await expect(page.getByText(customerName).first()).toBeVisible()
  await expect(page.getByText('已完成').first()).toBeVisible()

  await page.goto(`/shipping/shipment/${shipmentId}`)
  await expect(page.getByText(shipment.docNo).first()).toBeVisible()
  await expect(page.getByText('已出货').first()).toBeVisible()

  // 出货确认后生成应收：按客户核对金额 100 × 10 = 1,000
  const ars = await api.get<{ list: { docNo: string; totalAmount: number; sourceNo: string; status: string }[] }>(`/finance/receivables?customerId=${customer.id}`)
  expect(ars.list).toHaveLength(1)
  expect(ars.list[0].sourceNo).toBe(shipment.docNo)
  expect(Number(ars.list[0].totalAmount)).toBe(1000)
  await page.goto('/finance/receivable')
  await page.getByPlaceholder(/单号|客户|关键字/).first().fill(ars.list[0].docNo).catch(() => undefined)
  await page.keyboard.press('Enter')
  await expect(page.getByText(ars.list[0].docNo).first()).toBeVisible()

  expect(problems).toEqual([])
})
