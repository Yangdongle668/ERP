import { http } from './http'

/**
 * 公共选择器使用的业务模块接口（各模块需求文档中“登录即可”的搜索接口）。
 * 这里只定义契约；接口由对应模块实现（未实现时选择器提示接口错误，不影响其他功能）。
 * 所有搜索接口都支持 ids 参数（逗号分隔），用于编辑已有数据时回显。
 */

export interface MaterialBrief {
  id: string
  code: string
  name: string
  spec?: string
  baseUom: string
  materialType: string
  categoryId?: string
}

export interface CustomerBrief {
  id: string
  code: string
  name: string
  shortName?: string
  customerStatus?: string
  currency?: string
  paymentTermId?: string
  tradeTerm?: string
  salesTaxRate?: string
  ownerId?: string
}

export interface SupplierBrief {
  id: string
  code: string
  name: string
  shortName?: string
  status?: string
  currency?: string
  paymentTermId?: string
  taxRate?: string
  buyerId?: string
}

export interface WarehouseBrief {
  id: string
  code: string
  name: string
  warehouseType: string
  available: boolean
  locationEnabled?: boolean
}

export interface LocationBrief {
  id: string
  code: string
  name?: string
}

export interface BatchBrief {
  batchNo: string
  availableQty: string
  productionDate?: string
  expiryDate?: string
  inDate?: string
  frozen?: boolean
}

export interface CategoryNode {
  id: string
  parentId?: string
  code: string
  name: string
  children?: CategoryNode[]
}

export const refApi = {
  materialSearch: (params: { keyword?: string; types?: string; ids?: string; status?: string; limit?: number }) =>
    http.get<MaterialBrief[]>('/engineering/materials/search', { status: 'ENABLED', limit: 20, ...params }),
  materialByCode: (code: string) =>
    http.get<MaterialBrief | null>(`/engineering/materials/by-code/${encodeURIComponent(code)}`, undefined, { silent: true }),
  materialPage: (params: { keyword?: string; categoryId?: string; types?: string; status?: string; pageNo: number; pageSize: number }) =>
    http.get<{ list: MaterialBrief[]; total: number }>('/engineering/materials', { status: 'ENABLED', ...params }),
  categoryTree: () => http.get<CategoryNode[]>('/engineering/categories/simple-tree'),
  customerSearch: (params: { keyword?: string; statuses?: string; ids?: string }) =>
    http.get<CustomerBrief[]>('/crm/customers/search', params),
  supplierSearch: (params: { keyword?: string; statuses?: string; ids?: string }) =>
    http.get<SupplierBrief[]>('/purchase/suppliers/search', params),
  warehouseSimple: (params: { types?: string; onlyMine?: boolean }) =>
    http.get<WarehouseBrief[]>('/inventory/warehouses/simple', params),
  locationSimple: (warehouseId: string) => http.get<LocationBrief[]>('/inventory/locations/simple', { warehouseId }),
  batchAvailable: (materialId: string, warehouseId: string) =>
    http.get<BatchBrief[]>('/inventory/batches/available', { materialId, warehouseId })
}
