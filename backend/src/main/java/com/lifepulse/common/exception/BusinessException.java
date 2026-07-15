package com.lifepulse.common.exception;

/**
 * 业务异常：承载业务错误码与可读消息，由 {@code @RestControllerAdvice}（1.3）
 * 转换为统一返回信封。
 *
 * <p>CLAUDE.md §4.5：业务异常统一抛 {@code BusinessException(code, msg)}，
 * 禁止用 {@code Optional.empty()} 隐式掩盖跨用户访问。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}