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

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(OjException.class)
    public ResponseEntity<ApiError> handleDomain(OjException exception, HttpServletRequest request) {
        return ResponseEntity.status(statusFor(exception.code()))
                .body(new ApiError(exception.code(), exception.getMessage(), requestId(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + " " + field.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", message.isBlank() ? "请求参数无效" : message, requestId(request)));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(new ApiError("BAD_REQUEST", exception.getMessage(), requestId(request)));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", "资源不存在", requestId(request)));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleConflict(IllegalStateException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("CONFLICT", exception.getMessage(), requestId(request)));
    }

    /** 客户端提前断开（轮询取消/导航离开）：属正常现象，不按服务器错误记录。 */
    @ExceptionHandler({org.apache.catalina.connector.ClientAbortException.class,
            org.springframework.web.context.request.async.AsyncRequestNotUsableException.class})
    public void handleClientAbort(Exception exception, HttpServletRequest request) {
        log.debug("客户端中断连接 requestId={}", requestId(request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("未处理异常 requestId={}", requestId(request), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "服务器内部错误", requestId(request)));
    }

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

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdConstants.ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
