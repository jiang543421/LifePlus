package com.lifepulse.common.web;

/**
 * 全局返回信封（spec §03：{@code {code, message, data, traceId}}）。
 *
 * <p>{@code @RestControllerAdvice} 在 1.3 落地统一转换，本阶段仅供 IT 与
 * 手工断言使用。{@code traceId} 由 MDC filter（1.3）在响应阶段注入。
 *
 * @param code 业务错误码；{@code 0} 表示成功
 * @param message 人类可读消息
 * @param data 业务数据；可为 {@code null}
 * @param traceId 全链路追踪 ID；1.3 由 MDC 注入
 * @param <T> 数据载荷类型
 */
public record MyResponse<T>(int code, String message, T data, String traceId) {

    private static final String DEFAULT_OK_MESSAGE = "ok";

    public static <T> MyResponse<T> ok(T data) {
        return new MyResponse<>(0, DEFAULT_OK_MESSAGE, data, null);
    }

    public static <T> MyResponse<T> error(int code, String message) {
        return new MyResponse<>(code, message, null, null);
    }
}