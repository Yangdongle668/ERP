package com.erp.it.shipping;

import com.erp.module.inventory.api.warehouse.WarehouseApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 仓库提供库位编码查询（出货打印按库位编码显示与排序） */
class LocationCodeApiIntegrationTest extends ShippingTestSupport {

    @Autowired
    WarehouseApi warehouseApi;

    @Test
    void locationCodes() throws Exception {
        String code = "T" + uniq();
        Long id = Long.valueOf(ok(doPost("/api/inventory/warehouses/" + W_FG + "/locations", admin, Map.of("code", code))).asText());
        assertThat(warehouseApi.getLocationCodes(Arrays.asList(id, null, 987654321L))).containsExactly(Map.entry(id, code));
        assertThat(warehouseApi.getLocationCodes(null)).isEmpty();
    }
}
