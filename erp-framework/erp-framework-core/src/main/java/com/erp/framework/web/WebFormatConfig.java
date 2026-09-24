package com.erp.framework.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.format.DateTimeFormatter;

/**
 * 查询参数（非 JSON）的日期格式与 JSON 保持一致：日期 {@code yyyy-MM-dd}，日期时间 {@code yyyy-MM-dd HH:mm:ss}。
 * 前端 ErpSearchForm 的日期、日期时间范围按此格式传参。
 */
@Configuration
public class WebFormatConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        DateTimeFormatterRegistrar registrar = new DateTimeFormatterRegistrar();
        registrar.setDateFormatter(JacksonConfig.DATE);
        registrar.setDateTimeFormatter(JacksonConfig.DATE_TIME);
        registrar.setTimeFormatter(DateTimeFormatter.ofPattern("HH:mm:ss"));
        registrar.registerFormatters(registry);
    }
}
