package com.erp.it.fx;

import com.erp.module.fx.service.FxQuoteSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** 测试用汇率数据源：替代中国银行（测试环境无法访问外网） */
@Configuration
class ItFxConfig {

    static final AtomicReference<Map<String, FxQuoteSource.Quote>> NEXT = new AtomicReference<>();
    static final AtomicReference<String> FAIL = new AtomicReference<>();

    @Bean
    @Primary
    FxQuoteSource itFxQuoteSource() {
        return () -> {
            String err = FAIL.get();
            if (err != null) throw new IllegalStateException(err);
            Map<String, FxQuoteSource.Quote> q = NEXT.get();
            if (q == null) throw new IllegalStateException("未设置测试报价");
            return q;
        };
    }
}
