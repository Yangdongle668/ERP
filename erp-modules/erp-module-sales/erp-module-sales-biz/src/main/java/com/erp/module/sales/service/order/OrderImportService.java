package com.erp.module.sales.service.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.crm.api.part.CustomerPartApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.sales.controller.vo.OrderVOs.OrderLineSave;
import com.erp.module.sales.controller.vo.OrderVOs.OrderSave;
import com.erp.module.sales.dal.dataobject.SalOrderDO;
import com.erp.module.sales.dal.mapper.SalOrderMapper;
import com.erp.module.sales.service.SalSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 导入客户 PO（需求 04-03 3.5）：同一客户 + PO 号的行生成一张草稿订单 */
@Service("salOrderImportService")
public class OrderImportService {

    public static final List<ExcelColumn<Object>> COLUMNS = List.of(
            ExcelColumn.input("customerCode", "客户编码", true, null),
            ExcelColumn.input("customerPoNo", "客户 PO 号", true, null),
            ExcelColumn.input("customerPoDate", "客户 PO 日期", false, "yyyy-MM-dd"),
            ExcelColumn.input("materialCode", "物料编码", false, "物料编码与客户料号二选一"),
            ExcelColumn.input("customerPartNo", "客户料号", false, "按 CRM 客户料号对照匹配物料"),
            ExcelColumn.input("qty", "数量", true, "销售单位（默认物料基本单位）"),
            ExcelColumn.input("price", "单价", false, "为空时自动取价"),
            ExcelColumn.input("requiredDate", "要求交期", true, "yyyy-MM-dd"),
            ExcelColumn.input("remark", "备注", false, null));

    private final OrderService orderService;
    private final SalOrderMapper orderMapper;
    private final SalSupport support;
    private final CustomerPartApi customerPartApi;
    private final TransactionTemplate tx;

    public OrderImportService(OrderService orderService, SalOrderMapper orderMapper, SalSupport support, CustomerPartApi customerPartApi,
                              TransactionTemplate tx) {
        this.orderService = orderService;
        this.orderMapper = orderMapper;
        this.support = support;
        this.customerPartApi = customerPartApi;
        this.tx = tx;
    }

    /** 校验，返回每行动作（CREATE） */
    public Map<Integer, String> check(List<ImportRow> rows) {
        Map<Integer, String> actions = new HashMap<>();
        Map<String, CustomerDTO> customers = new HashMap<>();
        for (ImportRow r : rows) {
            CustomerDTO c = customer(r.get("customerCode"), customers);
            if (r.get("customerCode") == null) r.error("客户编码不能为空");
            else if (c == null) r.error("客户编码「" + r.get("customerCode") + "」不存在");
            String po = r.get("customerPoNo");
            if (po == null) r.error("客户 PO 号不能为空");
            else if (c != null) {
                SalOrderDO dup = orderMapper.selectOne(new LambdaQueryWrapper<SalOrderDO>().eq(SalOrderDO::getCustomerId, c.id())
                        .eq(SalOrderDO::getCustomerPoNo, po).ne(SalOrderDO::getStatus, DocStatus.VOIDED).last("LIMIT 1"));
                if (dup != null) r.error("客户 PO 号「" + po + "」已存在于订单「" + dup.getDocNo() + "」");
            }
            if (r.get("materialCode") == null && r.get("customerPartNo") == null) r.error("物料编码和客户料号至少填一个");
            else if (c != null && materialId(r, c) == null) {
                r.error(r.get("materialCode") != null ? "物料「" + r.get("materialCode") + "」不存在或未启用"
                        : "客户料号「" + r.get("customerPartNo") + "」没有对照的物料，请先维护客户料号对照");
            }
            BigDecimal qty = num(r, "qty", "数量");
            if (qty == null && r.get("qty") == null) r.error("数量不能为空");
            else if (qty != null && qty.signum() <= 0) r.error("数量必须大于 0");
            BigDecimal price = num(r, "price", "单价");
            if (price != null && price.signum() < 0) r.error("单价不能为负数");
            if (date(r, "requiredDate", "要求交期") == null && r.get("requiredDate") == null) r.error("要求交期不能为空");
            date(r, "customerPoDate", "客户 PO 日期");
            if (!r.hasError()) actions.put(r.rowNo(), "CREATE");
        }
        return actions;
    }

    /** 每张订单独立事务：部分失败时成功的仍然生成 */
    public ImportResult doImport(List<ImportRow> rows) {
        Map<String, CustomerDTO> customers = new HashMap<>();
        Map<String, List<ImportRow>> groups = new LinkedHashMap<>();
        for (ImportRow r : rows) groups.computeIfAbsent(r.get("customerCode").toUpperCase() + "|" + r.get("customerPoNo"), k -> new ArrayList<>()).add(r);
        int ok = 0;
        List<ImportResult.Error> errors = new ArrayList<>();
        for (List<ImportRow> group : groups.values()) {
            ImportRow first = group.get(0);
            try {
                CustomerDTO c = customer(first.get("customerCode"), customers);
                List<OrderLineSave> lines = new ArrayList<>();
                for (ImportRow r : group) {
                    lines.add(new OrderLineSave(materialId(r, c), r.get("customerPartNo"), null, null, new BigDecimal(r.get("qty").replace(",", "")),
                            r.get("price") == null ? null : new BigDecimal(r.get("price").replace(",", "")), null, LocalDate.parse(r.get("requiredDate")),
                            null, null, r.get("remark")));
                }
                LocalDate poDate = first.get("customerPoDate") == null ? null : LocalDate.parse(first.get("customerPoDate"));
                tx.executeWithoutResult(s -> orderService.create(new OrderSave(null, "NORMAL", c.id(), null, first.get("customerPoNo"), poDate, null, null,
                        null, null, null, null, null, null, null, null, null, null, "导入客户 PO", lines, null, null)));
                ok += group.size();
            } catch (BizException e) {
                group.forEach(r -> errors.add(new ImportResult.Error(r.rowNo(), e.getMessage())));
            }
        }
        return new ImportResult(ok, errors.size(), errors);
    }

    private CustomerDTO customer(String code, Map<String, CustomerDTO> cache) {
        if (code == null) return null;
        return cache.computeIfAbsent(code.toUpperCase(), k -> support.customerApi().search(code, null, 20).stream()
                .filter(c -> c.code().equalsIgnoreCase(code)).findFirst().orElse(null));
    }

    private Long materialId(ImportRow r, CustomerDTO c) {
        if (r.get("materialCode") != null) {
            String code = r.get("materialCode");
            return support.materialApi().search(code, null, 5).stream().filter(m -> m.code().equals(code)).map(MaterialDTO::id).findFirst().orElse(null);
        }
        return customerPartApi.toMaterial(c.id(), r.get("customerPartNo")).map(p -> p.materialId()).orElse(null);
    }

    private static BigDecimal num(ImportRow r, String key, String label) {
        String v = r.get(key);
        if (v == null) return null;
        try {
            return new BigDecimal(v.replace(",", ""));
        } catch (NumberFormatException e) {
            r.error(label + "不是数字");
            return null;
        }
    }

    private static LocalDate date(ImportRow r, String key, String label) {
        String v = r.get(key);
        if (v == null) return null;
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            r.error(label + "格式应为 yyyy-MM-dd");
            return null;
        }
    }
}
