package com.xauat.oj.common.response;

public record ApiResponse<T>(T data, String requestId) {
    public static <T> ApiResponse<T> of(T data, String requestId) {
        return new ApiResponse<>(data, requestId);
    }
}
