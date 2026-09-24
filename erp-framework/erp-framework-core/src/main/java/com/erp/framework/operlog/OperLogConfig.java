package com.erp.framework.operlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

/** 操作日志采集配置：过滤器排在安全过滤器之后（能取到登录用户），异步线程池使用有界队列，满时丢弃。 */
@Slf4j
@Configuration
public class OperLogConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new OperLogFilter.HandlerCapture()).addPathPatterns("/api/**");
    }

    @Bean
    public ThreadPoolTaskExecutor operLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("oper-log-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(2000);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }

    @Bean
    public FilterRegistrationBean<OperLogFilter> operLogFilter(List<OperLogRecorder> recorders, ObjectMapper objectMapper) {
        FilterRegistrationBean<OperLogFilter> bean = new FilterRegistrationBean<>(new OperLogFilter(recorders, objectMapper, operLogExecutor()));
        bean.addUrlPatterns("/api/*");
        // Spring Security 过滤器链的顺序为 -100，这里排在其后
        bean.setOrder(Ordered.LOWEST_PRECEDENCE - 100);
        return bean;
    }
}
