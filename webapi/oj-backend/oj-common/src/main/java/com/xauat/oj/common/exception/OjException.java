package com.xauat.oj.common.exception;

public class OjException extends RuntimeException {
    private final String code;

    public OjException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
