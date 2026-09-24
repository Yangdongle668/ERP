package com.erp.framework.web;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * JSON 序列化约定：
 * <ul>
 *   <li>Long 序列化为字符串：雪花 ID 超过 JavaScript 安全整数范围（2^53），以数字传给前端会丢失精度；</li>
 *   <li>日期时间统一为 {@code yyyy-MM-dd HH:mm:ss}，日期为 {@code yyyy-MM-dd}。</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer erpJacksonCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance)
                .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME))
                .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME))
                .serializerByType(LocalDate.class, new LocalDateSerializer(DATE))
                .deserializerByType(LocalDate.class, new LocalDateDeserializer(DATE));
    }
}
