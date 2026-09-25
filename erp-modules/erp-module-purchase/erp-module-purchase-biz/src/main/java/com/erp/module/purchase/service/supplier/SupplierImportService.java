package com.erp.module.purchase.service.supplier;

import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.purchase.controller.vo.SupplierVOs.SupplierSave;
import com.erp.module.system.api.currency.CurrencyApi;
import com.erp.module.system.api.dict.DictApi;
import com.erp.module.system.api.dict.DictItemDTO;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import com.erp.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 供应商导入（新建潜在供应商；付款条件由导入选项指定） */
@Service
public class SupplierImportService {

    public static final List<ExcelColumn<Object>> COLUMNS = List.of(
            ExcelColumn.input("code", "编码", false, "为空时自动生成"),
            ExcelColumn.input("name", "名称", true, "全称，唯一"),
            ExcelColumn.input("shortName", "简称", true, null),
            ExcelColumn.input("nameEn", "英文名", false, null),
            ExcelColumn.input("supplierType", "类型", false, "生产商/贸易商/委外加工商/服务商，默认生产商"),
            ExcelColumn.input("country", "国家", false, "ISO 二位码，默认 CN"),
            ExcelColumn.input("taxNo", "税号", false, null),
            ExcelColumn.input("phone", "电话", false, null),
            ExcelColumn.input("email", "邮箱", false, null),
            ExcelColumn.input("address", "地址", false, null),
            ExcelColumn.input("buyer", "采购员用户名", false, "默认当前用户"),
            ExcelColumn.input("currency", "币别", false, "默认本位币"),
            ExcelColumn.input("taxRate", "税率", false, "小数，如 0.13"),
            ExcelColumn.input("remark", "备注", false, null));

    private final SupplierService supplierService;
    private final DictApi dictApi;
    private final UserApi userApi;
    private final CurrencyApi currencyApi;

    public SupplierImportService(SupplierService supplierService, DictApi dictApi, UserApi userApi, CurrencyApi currencyApi) {
        this.supplierService = supplierService;
        this.dictApi = dictApi;
        this.userApi = userApi;
        this.currencyApi = currencyApi;
    }

    /** 校验，返回每行动作（CREATE） */
    public Map<Integer, String> check(List<ImportRow> rows) {
        Map<Integer, String> actions = new HashMap<>();
        Set<String> names = new HashSet<>();
        Map<String, String> typeByLabel = new HashMap<>();
        for (DictItemDTO d : dictApi.getItems("pur_supplier_type")) {
            typeByLabel.put(d.label(), d.value());
            typeByLabel.put(d.value(), d.value());
        }
        for (ImportRow r : rows) {
            String name = r.get("name");
            if (name == null) r.error("名称不能为空");
            else if (!names.add(name)) r.error("文件中名称重复");
            else if (!supplierService.searchDto(name, null, 50).stream().filter(s -> s.name().equals(name)).toList().isEmpty()) {
                r.error("供应商「" + name + "」已存在");
            }
            if (r.get("shortName") == null) r.error("简称不能为空");
            if (r.get("supplierType") != null && !typeByLabel.containsKey(r.get("supplierType"))) r.error("类型不正确");
            if (r.get("buyer") != null && userApi.getByUsername(r.get("buyer")).isEmpty()) r.error("采购员用户名不存在");
            if (r.get("currency") != null && currencyApi.get(r.get("currency").toUpperCase()).isEmpty()) r.error("币别不存在");
            if (r.get("taxRate") != null) {
                try {
                    BigDecimal t = new BigDecimal(r.get("taxRate"));
                    if (t.signum() < 0 || t.compareTo(BigDecimal.ONE) >= 0) r.error("税率应为 0～1 之间的小数");
                } catch (NumberFormatException e) {
                    r.error("税率不是数字");
                }
            }
            if (!r.hasError()) actions.put(r.rowNo(), "CREATE");
        }
        return actions;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult doImport(List<ImportRow> rows, Long paymentTermId) {
        Map<String, String> typeByLabel = new HashMap<>();
        for (DictItemDTO d : dictApi.getItems("pur_supplier_type")) {
            typeByLabel.put(d.label(), d.value());
            typeByLabel.put(d.value(), d.value());
        }
        int ok = 0;
        List<ImportResult.Error> errors = new ArrayList<>();
        for (ImportRow r : rows) {
            try {
                Long buyer = r.get("buyer") == null ? null : userApi.getByUsername(r.get("buyer")).map(UserDTO::id).orElse(null);
                supplierService.create(new SupplierSave(r.get("code"), r.get("name"), r.get("nameEn"), r.get("shortName"),
                        r.get("supplierType") == null ? null : typeByLabel.get(r.get("supplierType")), null, r.get("country"), null, null,
                        r.get("address"), r.get("taxNo"), r.get("phone"), r.get("email"), null, buyer, null, r.get("currency"), paymentTermId, null,
                        r.get("taxRate") == null ? null : new BigDecimal(r.get("taxRate")), null, null, r.get("remark"), null, null, null, null, null, null));
                ok++;
            } catch (BizException e) {
                errors.add(new ImportResult.Error(r.rowNo(), e.getMessage()));
            }
        }
        return new ImportResult(ok, errors.size(), errors);
    }
}
