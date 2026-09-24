package com.erp.it.system;

import com.erp.module.system.api.print.PrintBizDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 测试用的可打印单据类型；内置模板在 test resources 的 print-templates/IT_PRINT-zh-CN.html */
@Configuration
class ItPrintConfig {

    static final String BIZ = "IT_PRINT";

    @Bean
    PrintBizDefinition itPrint() {
        return PrintBizDefinition.of(BIZ, "测试单", "system", "/it/print/{id}/print-data")
                .variable("docNo", "单号", "string")
                .variable("docDate", "日期", "date")
                .variable("lines", "明细", "array")
                .variable("lines.qty", "数量", "qty")
                .sampleData("{\"docNo\":\"IT-0001\",\"docDate\":\"2026-09-24\",\"lines\":[{\"name\":\"螺丝\",\"qty\":10,\"amount\":12.5}],\"total\":12.5}");
    }
}
