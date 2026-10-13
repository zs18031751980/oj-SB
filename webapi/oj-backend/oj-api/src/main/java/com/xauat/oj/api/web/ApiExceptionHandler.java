package com.xauat.oj.api.web;

import com.xauat.oj.common.constant.RequestIdConstants;
import com.xauat.oj.common.exception.OjException;
import com.xauat.oj.common.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器：把各类异常映射为统一的 {@link ApiError} JSON 响应。
 *
 * <p>安全相关意义：401/403 等鉴权结果由 Spring Security 过滤器链直接返回，这里主要处理
 * 领域异常与参数/资源类异常，并统一附带请求 id 便于追踪，且避免向客户端泄露内部堆栈。</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * 处理业务领域异常，按其错误码映射 HTTP 状态。
     *
     * @param exception 领域异常
     * @param request   当前请求
     * @return 统一错误响应
     */
    @ExceptionHandler(OjException.class)
    public ResponseEntity<ApiError> handleDomain(OjException exception, HttpServletRequest request) {
        return ResponseEntity.status(statusFor(exception.code()))
                .body(new ApiError(exception.code(), exception.getMessage(), requestId(request)));
    }

    /**
     * 处理 Bean 校验失败，汇总字段错误信息。
     *
     * @param exception 校验异常
     * @param request   当前请求
     * @return 400 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + " " + field.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", message.isBlank() ? "请求参数无效" : message, requestId(request)));
    }

    /**
     * 处理参数类型/缺失或请求体不可解析等客户端错误。
     *
     * @param exception 异常
     * @param request   当前请求
     * @return 400 错误响应
     */
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(new ApiError("BAD_REQUEST", exception.getMessage(), requestId(request)));
    }

    /**
     * 处理静态资源/路由不存在。
     *
     * @param exception 资源未找到异常
     * @param request   当前请求
     * @return 404 错误响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", "资源不存在", requestId(request)));
    }

    /**
     * 处理状态冲突（如重复提交、乐观锁失败）。
     *
     * @param exception 状态异常
     * @param request   当前请求
     * @return 409 错误响应
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleConflict(IllegalStateException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("CONFLICT", exception.getMessage(), requestId(request)));
    }

    /**
     * 客户端提前断开（轮询取消/导航离开）：属正常现象，不按服务器错误记录。
     *
     * @param exception 中断异常
     * @param request   当前请求
     */
    @ExceptionHandler({org.apache.catalina.connector.ClientAbortException.class,
            org.springframework.web.context.request.async.AsyncRequestNotUsableException.class})
    public void handleClientAbort(Exception exception, HttpServletRequest request) {
        log.debug("客户端中断连接 requestId={}", requestId(request));
    }

    /**
     * 兜底处理未预期异常，记录完整堆栈但只向客户端返回通用提示。
     *
     * @param exception 异常
     * @param request   当前请求
     * @return 500 通用错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("未处理异常 requestId={}", requestId(request), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "服务器内部错误", requestId(request)));
    }

    /** 领域错误码到 HTTP 状态的映射，未知错误码按 400 处理。 */
    private static HttpStatus statusFor(String code) {
        return switch (code) {
            case "UNAUTHORIZED" -> HttpStatus.UNAUTHORIZED;
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "CONFLICT" -> HttpStatus.CONFLICT;
            case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "SERVICE_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    /** 从请求属性读取由 RequestIdFilter 写入的请求 id，缺失时返回空串。 */
    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdConstants.ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
