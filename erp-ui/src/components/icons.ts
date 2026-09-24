import type { App, Component } from 'vue'
import {
  ArrowDown, ArrowDownToLine, ArrowLeft, ArrowRight, ArrowUp, ArrowUpToLine, Ban, Bell, Boxes, Building2, CalendarDays, ChartColumn,
  Check, ChevronDown, ChevronLeft, ChevronRight, ChevronUp, CircleAlert, CircleCheck, CircleX, Clock, CloudUpload, Columns3, Copy,
  Download, Ellipsis, Eye, Factory, FileSpreadsheet, FileText, Files, Folder, FolderX, History, House, Inbox, Info, KeyRound,
  Languages, LayoutGrid, Lightbulb, Link, ListFilter, LoaderCircle, Lock, LogOut, Package, PanelLeftClose, PanelLeftOpen,
  Paperclip, Pencil, Play, Plus, Printer, Receipt, RefreshCw, RotateCcw, Save, Search, Send, Settings, Settings2, ShoppingCart,
  SlidersHorizontal, Tag, TriangleAlert, Trash2, Truck, Upload, User, Users, Wallet, Warehouse, X
} from 'lucide-vue-next'

/**
 * 全站唯一图标库：Lucide（线性图标，1.75 描边）。
 *
 * 页面与组件通过名称使用图标：`<el-button icon="Plus">`、`<el-icon><Search /></el-icon>`、模块菜单 `icon: 'Box'`。
 * 名称沿用 Element Plus 图标命名以兼容已有代码，但全部映射到 Lucide；不再注册 @element-plus/icons-vue，
 * 禁止使用 Emoji 或其他图标库。需要新图标时在这里增加映射。
 */
export const ICONS: Record<string, Component> = {
  // 通用操作
  Plus, Search, Refresh: RefreshCw, RefreshLeft: RotateCcw, Download, Upload, UploadFilled: CloudUpload, Delete: Trash2, Edit: Pencil,
  View: Eye, CopyDocument: Copy, Printer, Paperclip, Document: FileText, Files, Link, Filter: ListFilter, Check, Close: X,
  CircleClose: CircleX, FolderDelete: FolderX, More: Ellipsis, MoreFilled: Ellipsis, Loading: LoaderCircle, Save, Send, Play,
  Ban, History, Clock, Columns: Columns3, Setting: Settings, Operation: SlidersHorizontal, Tools: Settings2, Sheet: FileSpreadsheet,
  // 方向
  Top: ArrowUpToLine, Bottom: ArrowDownToLine, Up: ArrowUp, Down: ArrowDown, Right: ArrowRight, Back: ArrowLeft, ArrowLeft,
  ArrowDown: ChevronDown, ArrowUp: ChevronUp, ArrowRight: ChevronRight, ArrowLeftBold: ChevronLeft,
  Expand: PanelLeftOpen, Fold: PanelLeftClose,
  // 状态
  InfoFilled: Info, Info, Warning: TriangleAlert, WarningFilled: CircleAlert, CircleCheck, SuccessFilled: CircleCheck,
  CircleCloseFilled: CircleX, Empty: Inbox,
  // 用户与账户
  User, UserFilled: User, Avatar: Users, Users, Lock, Key: KeyRound, SwitchButton: LogOut, Switch: Languages, Bell,
  // 组织与业务对象
  OfficeBuilding: Building2, Folder, HomeFilled: House, House, Box: Package, Goods: Boxes, Warehouse, Van: Truck,
  ShoppingCart, Sell: Tag, Money: Wallet, Tickets: Receipt, SetUp: Factory, Calendar: CalendarDays, Opportunity: Lightbulb,
  DataAnalysis: ChartColumn, Grid: LayoutGrid
}

export function registerIcons(app: App) {
  for (const [name, c] of Object.entries(ICONS)) app.component(name, c)
}
