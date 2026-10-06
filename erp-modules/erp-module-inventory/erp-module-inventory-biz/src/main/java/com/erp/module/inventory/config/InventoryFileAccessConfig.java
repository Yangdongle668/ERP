package com.erp.module.inventory.config;

import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.inventory.service.doc.CountService;
import com.erp.module.inventory.service.doc.StockInService;
import com.erp.module.inventory.service.doc.StockOutService;
import com.erp.module.inventory.service.doc.TransferService;
import com.erp.module.system.api.file.FileAccessChecker;
import com.erp.module.system.api.file.FileAccessChecker.Rule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/** 仓库附件访问控制（SYS-FIL-R04）：有单据查看权限可查看附件；有编辑类权限可上传、删除 */
@Configuration
public class InventoryFileAccessConfig {

    @Bean
    public FileAccessChecker inventoryFileAccessChecker() {
        return FileAccessChecker.byPermissions(Map.ofEntries(
                Map.entry(StockInService.BIZ_TYPE, Rule.of("inv:in:query", "inv:in:create", "inv:in:update", "inv:in:confirm")),
                Map.entry(StockOutService.BIZ_TYPE, Rule.of("inv:out:query", "inv:out:create", "inv:out:update", "inv:out:confirm")),
                Map.entry(TransferService.BIZ_TYPE, Rule.of("inv:transfer:query", "inv:transfer:create", "inv:transfer:update", "inv:transfer:confirm")),
                Map.entry(CountService.BIZ_TYPE, Rule.of("inv:count:query", "inv:count:create", "inv:count:input"))), InventoryFileAccessConfig::hasPermission);
    }

    private static boolean hasPermission(String permission) {
        LoginUser u = SecurityUtils.getLoginUserOrNull();
        return u == null || u.hasPermission(permission);
    }
}
