package com.lifepulse.common.web;

import org.slf4j.MDC;

/**
 * 全局返回信封（spec §03：{@code {code, message, data, traceId}}）。
 *
 * <p>1.3 落地：{@code traceId} 由 {@code TraceIdFilter} 写入 MDC，本类工厂方法
 * 读取 MDC 自动填入 {@code traceId} 字段，满足 spec §2 "信封恒含 traceId" 要求。
 * MDC 未设时仍为 {@code null}（向后兼容 1.2 IT）。
 *
 * @param code 业务错误码；{@code 0} 表示成功
 * @param message 人类可读消息
 * @param data 业务数据；可为 {@code null}
 * @param traceId 全链路追踪 ID；1.3 自动从 MDC 注入
 * @param <T> 数据载荷类型
 */
public record MyResponse<T>(int code, String message, T data, String traceId) {

    private static final String DEFAULT_OK_MESSAGE = "ok";

    public static <T> MyResponse<T> ok(T data) {
        return new MyResponse<>(0, DEFAULT_OK_MESSAGE, data, currentTraceId());
    }

    public static <T> MyResponse<T> error(int code, String message) {
        return new MyResponse<>(code, message, null, currentTraceId());
    }

    /** 从 {@code MDC} 取当前 traceId；MDC 未设时返回 {@code null}。 */
    private static String currentTraceId() {
        return MDC.get("traceId");
    }
}