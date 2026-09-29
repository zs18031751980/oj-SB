package com.xauat.oj.api.web;

import com.xauat.oj.common.constant.RequestIdConstants;
import com.xauat.oj.common.exception.OjException;
import com.xauat.oj.common.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(OjException.class)
    public ResponseEntity<ApiError> handleDomain(OjException exception, HttpServletRequest request) {
        return ResponseEntity.status(statusFor(exception.code()))
                .body(new ApiError(exception.code(), exception.getMessage(), requestId(request)));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(new ApiError("BAD_REQUEST", exception.getMessage(), requestId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
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
