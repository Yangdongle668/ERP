package com.erp.module.shipping.config;

import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.system.api.file.FileAccessChecker;
import com.erp.module.system.api.file.FileAccessChecker.Rule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/** 出货附件访问控制（SYS-FIL-R04）：有单据查看权限可查看附件；有编辑类权限可上传、删除 */
@Configuration
public class ShippingFileAccessConfig {

    @Bean
    public FileAccessChecker shippingFileAccessChecker() {
        return FileAccessChecker.byPermissions(Map.ofEntries(
                Map.entry(ShippingModuleConfig.NOTICE, Rule.of("shp:notice:query", "shp:notice:create", "shp:notice:update")),
                Map.entry(ShippingModuleConfig.PICKING, Rule.of("shp:picking:query", "shp:picking:pick")),
                Map.entry(ShippingModuleConfig.SHIPMENT, new Rule(List.of("shp:shipment:query", "shp:logistics:query"),
                        List.of("shp:shipment:create", "shp:shipment:update", "shp:shipment:submit", "shp:logistics:update"))),
                Map.entry(ShippingModuleConfig.PACKING_LIST, Rule.of("shp:document:query", "shp:document:create", "shp:document:update")),
                Map.entry(ShippingModuleConfig.INVOICE, Rule.of("shp:document:query", "shp:document:create", "shp:document:update")),
                Map.entry(ShippingModuleConfig.CUSTOMS, Rule.of("shp:document:query", "shp:document:create", "shp:document:update"))), ShippingFileAccessConfig::hasPermission);
    }

    private static boolean hasPermission(String permission) {
        LoginUser u = SecurityUtils.getLoginUserOrNull();
        return u == null || u.hasPermission(permission);
    }
}
