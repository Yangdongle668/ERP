package com.erp.framework.web;

import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.CommonResult;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：把异常统一转换为 {@link CommonResult}。
 *
 * <p>原则：业务异常原样提示；参数异常给出字段级信息；未知异常只返回 traceId，堆栈只记日志，不暴露给前端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public CommonResult<Object> handleBiz(BizException e) {
        log.info("[业务异常] code={}, msg={}", e.getCode(), e.getMessage());
        return new CommonResult<>(e.getCode(), e.getMessage(), e.getData());
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CommonResult<Void> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::fieldMessage)
                .collect(Collectors.joining("；"));
        return badRequest(msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CommonResult<Void> handleConstraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.joining("；"));
        return badRequest(msg);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CommonResult<Void> handleBadRequest(Exception e) {
        return badRequest(e instanceof HttpMessageNotReadableException ? "请求体格式错误" : e.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public CommonResult<Void> handleMethod() {
        return CommonResult.error(GlobalErrorCodes.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public CommonResult<Void> handleNotFound() {
        return CommonResult.error(GlobalErrorCodes.NOT_FOUND);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public CommonResult<Void> handleForbidden() {
        return CommonResult.error(GlobalErrorCodes.FORBIDDEN);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.OK)
    public CommonResult<Void> handleDuplicate(DuplicateKeyException e) {
        log.warn("[唯一约束冲突] {}", e.getMostSpecificCause().getMessage());
        return CommonResult.error(GlobalErrorCodes.CONFLICT);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.OK)
    public CommonResult<Void> handleOptimisticLock() {
        return CommonResult.error(GlobalErrorCodes.CONCURRENT_MODIFICATION);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public CommonResult<Void> handleUnknown(Exception e) {
        String traceId = TraceIdFilter.current();
        log.error("[系统异常] traceId={}", traceId, e);
        return CommonResult.error(GlobalErrorCodes.INTERNAL_ERROR.code(),
                GlobalErrorCodes.INTERNAL_ERROR.message().replace("{}", String.valueOf(traceId)));
    }

    private static CommonResult<Void> badRequest(String detail) {
        return CommonResult.error(GlobalErrorCodes.BAD_REQUEST.code(),
                GlobalErrorCodes.BAD_REQUEST.message().replace("{}", String.valueOf(detail)));
    }

    private static String fieldMessage(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}
