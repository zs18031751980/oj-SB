package com.xauat.oj.common.response;

public record ApiError(String code, String message, String requestId) {
}
