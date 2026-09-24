package com.erp.framework.operlog;

import com.erp.common.result.CommonResult;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.framework.web.ClientIp;
import com.erp.framework.web.TraceIdFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

/**
 * 操作日志采集（01-11 日志审计）：记录所有 /api/** 的写操作。
 * <ul>
 *   <li>参数 JSON 中名称包含 password / secret / token 的字段替换为 ******，截断到 4000 字符；</li>
 *   <li>文件上传只记录文件名和大小；</li>
 *   <li>在独立线程池中异步保存，失败只写应用日志，不影响业务请求。</li>
 * </ul>
 * 认证接口（登录、刷新）由登录日志记录，这里不重复记录。
 */
@Slf4j
public class OperLogFilter extends OncePerRequestFilter {

    static final String ATTR_HANDLER = OperLogFilter.class.getName() + ".handler";
    static final String ATTR_CODE = OperLogFilter.class.getName() + ".code";
    static final String ATTR_MSG = OperLogFilter.class.getName() + ".msg";

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");
    private static final Pattern SENSITIVE = Pattern.compile("(?i).*(password|secret|token).*");
    private static final int MAX_PARAMS = 4000;
    private static final int MAX_BODY_CACHE = 64 * 1024;

    private final List<OperLogRecorder> recorders;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public OperLogFilter(List<OperLogRecorder> recorders, ObjectMapper objectMapper, Executor executor) {
        this.recorders = recorders;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return recorders.isEmpty() || !WRITE_METHODS.contains(request.getMethod())
                || !uri.startsWith("/api/") || uri.startsWith("/api/system/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean multipart = request.getContentType() != null && request.getContentType().toLowerCase().startsWith("multipart/");
        HttpServletRequest req = multipart ? request : new ContentCachingRequestWrapper(request, MAX_BODY_CACHE);
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(req, response);
        } finally {
            try {
                capture(req, response, multipart, (int) (System.currentTimeMillis() - start));
            } catch (Exception e) {
                log.warn("[操作日志] 采集失败 {}", request.getRequestURI(), e);
            }
        }
    }

    private void capture(HttpServletRequest req, HttpServletResponse resp, boolean multipart, int duration) {
        HandlerMethod handler = req.getAttribute(ATTR_HANDLER) instanceof HandlerMethod h ? h : null;
        OperLog anno = handler == null ? null : handler.getMethodAnnotation(OperLog.class);
        if (anno != null && !anno.enabled()) return;

        String path = req.getRequestURI();
        String module = anno != null && !anno.module().isEmpty() ? anno.module() : moduleOf(path);
        String action = anno != null && !anno.value().isEmpty() ? anno.value() : actionOf(handler, path);
        Integer code = req.getAttribute(ATTR_CODE) instanceof Integer c ? c : null;
        String msg = req.getAttribute(ATTR_MSG) instanceof String m ? m : null;
        String result = resp.getStatus() >= 500 ? "SYSTEM_ERROR"
                : (code != null && code != 0) || resp.getStatus() >= 400 ? "BIZ_ERROR" : "SUCCESS";
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        String params = truncate(multipart ? multipartParams(req) : bodyParams(req), MAX_PARAMS);

        OperLogRecord record = new OperLogRecord(TraceIdFilter.current(), module, truncate(action, 64), req.getMethod(),
                truncate(path, 256), params, result, "SUCCESS".equals(result) ? null : (code != null ? code : resp.getStatus()),
                "SUCCESS".equals(result) ? null : truncate(msg, 512), duration,
                user == null ? null : user.id(), user == null ? null : user.username(), user == null ? null : user.realName(),
                ClientIp.of(req), LocalDateTime.now());
        try {
            executor.execute(() -> {
                for (OperLogRecorder r : recorders) {
                    try {
                        r.record(record);
                    } catch (Exception e) {
                        log.warn("[操作日志] 保存失败 traceId={}", record.traceId(), e);
                    }
                }
            });
        } catch (Exception e) {
            // 线程池队列已满：丢弃并记录，不影响业务
            log.error("[操作日志] 队列已满，丢弃 traceId={} {}", record.traceId(), path);
        }
    }

    private static String moduleOf(String path) {
        String[] seg = path.split("/");
        return seg.length > 2 ? seg[2] : "";
    }

    private static String actionOf(HandlerMethod handler, String path) {
        if (handler != null) {
            Operation op = handler.getMethodAnnotation(Operation.class);
            if (op != null && !op.summary().isEmpty()) return op.summary();
        }
        return path;
    }

    private String bodyParams(HttpServletRequest req) {
        StringBuilder sb = new StringBuilder();
        if (req.getQueryString() != null) sb.append("?").append(req.getQueryString());
        if (req instanceof ContentCachingRequestWrapper w) {
            byte[] body = w.getContentAsByteArray();
            if (body.length > 0) {
                String text = new String(body, StandardCharsets.UTF_8);
                if (sb.length() > 0) sb.append(' ');
                sb.append(mask(text));
            }
        }
        return sb.toString();
    }

    private String multipartParams(HttpServletRequest req) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            ArrayNode files = node.putArray("files");
            for (Part p : req.getParts()) {
                if (p.getSubmittedFileName() != null) {
                    files.addObject().put("name", p.getSubmittedFileName()).put("size", p.getSize());
                }
            }
            for (Map.Entry<String, String[]> e : req.getParameterMap().entrySet()) {
                node.put(e.getKey(), SENSITIVE.matcher(e.getKey()).matches() ? "******" : String.join(",", e.getValue()));
            }
            return node.toString();
        } catch (Exception e) {
            return "(multipart)";
        }
    }

    /** 脱敏：JSON 中名称包含 password/secret/token 的字段值替换为 ******；非 JSON 原样返回 */
    String mask(String text) {
        try {
            JsonNode node = objectMapper.readTree(text);
            maskNode(node);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return SENSITIVE.matcher(text).matches() ? "******" : text;
        }
    }

    private static void maskNode(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (SENSITIVE.matcher(e.getKey()).matches() && !e.getValue().isContainerNode()) {
                    e.setValue(obj.textNode("******"));
                } else {
                    maskNode(e.getValue());
                }
            }
        } else if (node instanceof ArrayNode arr) {
            arr.forEach(OperLogFilter::maskNode);
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /** 记录处理请求的 Controller 方法，供采集时读取注解 */
    public static class HandlerCapture implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            if (handler instanceof HandlerMethod) request.setAttribute(ATTR_HANDLER, handler);
            return true;
        }
    }

    /** 记录响应中的业务错误码与提示（含全局异常处理器的返回值） */
    @RestControllerAdvice
    public static class ResultCapture implements ResponseBodyAdvice<Object> {
        @Override
        public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
            return true;
        }

        @Override
        public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                      Class<? extends HttpMessageConverter<?>> converterType,
                                      ServerHttpRequest request, ServerHttpResponse response) {
            if (body instanceof CommonResult<?> r && request instanceof ServletServerHttpRequest s) {
                s.getServletRequest().setAttribute(ATTR_CODE, r.code());
                s.getServletRequest().setAttribute(ATTR_MSG, r.msg());
            }
            return body;
        }
    }
}
