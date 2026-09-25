package com.erp.module.crm.service;

import com.erp.common.exception.BizException;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.crm.controller.vo.CustomerVOs.AddressSave;
import com.erp.module.crm.controller.vo.CustomerVOs.ContactSave;
import com.erp.module.crm.controller.vo.CustomerVOs.CustomerSave;
import com.erp.module.system.api.currency.CurrencyApi;
import com.erp.module.system.api.dict.DictApi;
import com.erp.module.system.api.dict.DictItemDTO;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 客户导入（需求 03-01 3.1）：客户主信息 + 主联系人 + 默认收货地址，导入为潜在客户 */
@Service("crmCustomerImportService")
public class CustomerImportService {

    public static final List<ExcelColumn<Object>> COLUMNS = List.of(
            ExcelColumn.input("code", "编码", false, "为空时自动生成"),
            ExcelColumn.input("name", "客户名称", true, "同一国家内唯一"),
            ExcelColumn.input("nameEn", "英文名称", false, "外销客户必填"),
            ExcelColumn.input("shortName", "简称", false, "为空取名称前 10 个字"),
            ExcelColumn.input("customerType", "客户类型", false, "终端客户/贸易商/代理商/品牌商，默认终端客户"),
            ExcelColumn.input("level", "等级", false, "A/B/C/D，默认 C"),
            ExcelColumn.input("country", "国家", true, "ISO 二位代码，如 CN、US"),
            ExcelColumn.input("taxNo", "税号", false, null),
            ExcelColumn.input("phone", "电话", false, null),
            ExcelColumn.input("email", "邮箱", false, null),
            ExcelColumn.input("address", "公司地址", false, null),
            ExcelColumn.input("owner", "负责人用户名", false, "默认当前用户"),
            ExcelColumn.input("currency", "币别", false, "外销默认 USD，内销默认本位币"),
            ExcelColumn.input("contactName", "主联系人", false, null),
            ExcelColumn.input("contactEmail", "联系人邮箱", false, "填写主联系人时邮箱、电话至少一项"),
            ExcelColumn.input("contactPhone", "联系人电话", false, null),
            ExcelColumn.input("shipAddress", "收货地址", false, "作为默认收货地址"),
            ExcelColumn.input("remark", "备注", false, null));

    private final CustomerService customerService;
    private final DictApi dictApi;
    private final UserApi userApi;
    private final CurrencyApi currencyApi;

    public CustomerImportService(CustomerService customerService, DictApi dictApi, UserApi userApi, CurrencyApi currencyApi) {
        this.customerService = customerService;
        this.dictApi = dictApi;
        this.userApi = userApi;
        this.currencyApi = currencyApi;
    }

    private Map<String, String> typeByLabel() {
        Map<String, String> map = new HashMap<>();
        for (DictItemDTO d : dictApi.getItems("crm_customer_type")) {
            map.put(d.label(), d.value());
            map.put(d.value(), d.value());
        }
        return map;
    }

    /** 校验，返回每行动作（CREATE） */
    public Map<Integer, String> check(List<ImportRow> rows) {
        Map<Integer, String> actions = new HashMap<>();
        Set<String> keys = new HashSet<>();
        Map<String, String> types = typeByLabel();
        for (ImportRow r : rows) {
            String name = r.get("name");
            String country = r.get("country") == null ? null : r.get("country").toUpperCase(Locale.ROOT);
            if (name == null) r.error("客户名称不能为空");
            if (country == null || !country.matches("[A-Z]{2}")) r.error("国家请填写 ISO 二位代码");
            if (name != null && country != null) {
                if (!keys.add(CustomerService.nameKey(name) + "|" + country)) r.error("文件中客户名称重复");
                else customerService.findByName(name, country).ifPresent(c -> r.error("该国家已存在名称为「" + c.getName() + "」的客户"));
            }
            if (r.get("code") != null && customerService.findByCode(r.get("code")).isPresent()) r.error("编码已存在");
            if (country != null && !CustomerService.HOME_COUNTRY.equals(country) && r.get("nameEn") == null) r.error("外销客户必须填写英文名称");
            if (r.get("customerType") != null && !types.containsKey(r.get("customerType"))) r.error("客户类型不正确");
            if (r.get("level") != null && !List.of("A", "B", "C", "D").contains(r.get("level").toUpperCase(Locale.ROOT))) r.error("等级应为 A/B/C/D");
            if (r.get("owner") != null && userApi.getByUsername(r.get("owner")).isEmpty()) r.error("负责人用户名不存在");
            if (r.get("currency") != null && currencyApi.get(r.get("currency").toUpperCase(Locale.ROOT)).isEmpty()) r.error("币别不存在");
            if (r.get("contactName") != null && r.get("contactEmail") == null && r.get("contactPhone") == null) r.error("主联系人需要填写邮箱或电话");
            if (!r.hasError()) actions.put(r.rowNo(), "CREATE");
        }
        return actions;
    }

    /** 每个客户单独事务：失败的行不影响其他行 */
    public ImportResult doImport(List<ImportRow> rows, Long paymentTermId) {
        Map<String, String> types = typeByLabel();
        int ok = 0;
        List<ImportResult.Error> errors = new ArrayList<>();
        for (ImportRow r : rows) {
            try {
                Long owner = r.get("owner") == null ? null : userApi.getByUsername(r.get("owner")).map(UserDTO::id).orElse(null);
                String country = r.get("country").toUpperCase(Locale.ROOT);
                List<ContactSave> contacts = r.get("contactName") == null ? List.of()
                        : List.of(new ContactSave(r.get("contactName"), null, null, null, r.get("contactEmail"), r.get("contactPhone"), null, null, null,
                        true, "ACTIVE", null));
                String company = r.get("nameEn") != null ? r.get("nameEn") : r.get("name");
                List<AddressSave> addresses = r.get("shipAddress") == null ? List.of()
                        : List.of(new AddressSave("SHIP_TO", company, r.get("contactName"), r.get("contactPhone"), country, null, null, null,
                        r.get("shipAddress"), true, null));
                customerService.create(new CustomerSave(r.get("code"), r.get("name"), r.get("nameEn"), r.get("shortName"),
                        r.get("customerType") == null ? null : types.get(r.get("customerType")),
                        r.get("level") == null ? null : r.get("level").toUpperCase(Locale.ROOT), country, null, null, null, r.get("address"), null, null,
                        null, r.get("phone"), r.get("email"), r.get("taxNo"), owner, r.get("currency"), paymentTermId, null, null, null, null,
                        r.get("remark"), contacts, addresses, null, null, null));
                ok++;
            } catch (BizException e) {
                errors.add(new ImportResult.Error(r.rowNo(), e.getMessage()));
            }
        }
        return new ImportResult(ok, errors.size(), errors);
    }
}
