package com.erp.module.system.service.job;

import com.erp.module.system.api.job.ErpJob;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 收集所有 Bean 上的 {@link ErpJob} 方法（类似 @Scheduled 的处理方式）。只记录 Bean 名称，
 * 执行时从容器取最终 Bean（含事务等代理）。同一编码重复声明时启动失败。
 */
@Component
public class ErpJobCollector implements BeanPostProcessor {

    private static final Pattern MODULE = Pattern.compile("^com\\.erp\\.module\\.([a-z][a-z0-9]*)\\.");

    /** 声明的任务：code → handle */
    public record JobHandle(String code, String name, String cron, String moduleCode, String beanName, Method method) {
    }

    private final Map<String, JobHandle> handles = new LinkedHashMap<>();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> target = AopUtils.getTargetClass(bean);
        if (!target.getName().startsWith("com.erp.")) return bean;
        Map<Method, ErpJob> found = MethodIntrospector.selectMethods(target,
                (MethodIntrospector.MetadataLookup<ErpJob>) m -> AnnotatedElementUtils.findMergedAnnotation(m, ErpJob.class));
        found.forEach((method, job) -> {
            if (method.getParameterCount() > 0) {
                throw new IllegalStateException("@ErpJob 方法不能有参数: " + target.getName() + "#" + method.getName());
            }
            JobHandle old = handles.get(job.code());
            if (old != null && !old.beanName().equals(beanName)) {
                throw new IllegalStateException("定时任务编码重复: " + job.code() + "（" + old.beanName() + "、" + beanName + "）");
            }
            Matcher m = MODULE.matcher(target.getName());
            handles.put(job.code(), new JobHandle(job.code(), job.name(), job.cron(), m.find() ? m.group(1) : "system", beanName, method));
        });
        return bean;
    }

    public Collection<JobHandle> handles() {
        return Collections.unmodifiableCollection(handles.values());
    }

    public JobHandle get(String code) {
        return handles.get(code);
    }
}
